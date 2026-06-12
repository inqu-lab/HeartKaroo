"""FastAPI backend for the TriPlanner iPhone app.

The app sends the athlete's intervals.icu credentials with each request
(stored in the iOS Keychain client-side); the backend keeps no athlete data
except the race list, held in a small SQLite file.
"""

from __future__ import annotations

import sqlite3
from contextlib import closing
from datetime import date
from pathlib import Path

import httpx
from fastapi import FastAPI, Header, HTTPException

from .intervals import IntervalsClient
from .models import Plan, Race, RaceDistance, Readiness
from .planner import build_plan
from .readiness import compute_readiness

DB_PATH = Path(__file__).resolve().parent.parent / "triplanner.db"

app = FastAPI(title="TriPlanner", version="1.0.0")


def _db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.execute(
        """CREATE TABLE IF NOT EXISTS races (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            athlete_id TEXT NOT NULL,
            name TEXT NOT NULL,
            day TEXT NOT NULL,
            distance TEXT NOT NULL,
            priority TEXT NOT NULL DEFAULT 'A'
        )"""
    )
    return conn


def _client(athlete_id: str, api_key: str) -> IntervalsClient:
    return IntervalsClient(athlete_id, api_key)


def _readiness(athlete_id: str, api_key: str, today: date) -> Readiness:
    client = _client(athlete_id, api_key)
    try:
        wellness = client.wellness(today)
        activities = client.activities(today)
    except httpx.HTTPStatusError as e:
        raise HTTPException(
            status_code=e.response.status_code,
            detail=f"intervals.icu request failed: {e.response.status_code}",
        )
    except httpx.HTTPError as e:
        raise HTTPException(status_code=502, detail=f"intervals.icu unreachable: {e}")
    finally:
        client.close()
    return compute_readiness(today, wellness, activities)


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.get("/readiness", response_model=Readiness)
def get_readiness(
    x_athlete_id: str = Header(...),
    x_api_key: str = Header(...),
) -> Readiness:
    return _readiness(x_athlete_id, x_api_key, date.today())


@app.get("/races", response_model=list[Race])
def list_races(x_athlete_id: str = Header(...)) -> list[Race]:
    with closing(_db()) as conn:
        rows = conn.execute(
            "SELECT id, name, day, distance, priority FROM races"
            " WHERE athlete_id = ? ORDER BY day",
            (x_athlete_id,),
        ).fetchall()
    return [
        Race(id=r[0], name=r[1], day=date.fromisoformat(r[2]), distance=RaceDistance(r[3]), priority=r[4])
        for r in rows
    ]


@app.post("/races", response_model=Race, status_code=201)
def add_race(race: Race, x_athlete_id: str = Header(...)) -> Race:
    with closing(_db()) as conn:
        cur = conn.execute(
            "INSERT INTO races (athlete_id, name, day, distance, priority) VALUES (?, ?, ?, ?, ?)",
            (x_athlete_id, race.name, race.day.isoformat(), race.distance.value, race.priority),
        )
        conn.commit()
        race.id = cur.lastrowid
    return race


@app.delete("/races/{race_id}", status_code=204)
def delete_race(race_id: int, x_athlete_id: str = Header(...)) -> None:
    with closing(_db()) as conn:
        cur = conn.execute(
            "DELETE FROM races WHERE id = ? AND athlete_id = ?", (race_id, x_athlete_id)
        )
        conn.commit()
    if cur.rowcount == 0:
        raise HTTPException(status_code=404, detail="Race not found")


@app.get("/plan", response_model=Plan)
def get_plan(
    x_athlete_id: str = Header(...),
    x_api_key: str = Header(...),
) -> Plan:
    """Plan for the next upcoming race, adjusted by today's readiness."""
    today = date.today()
    races = [r for r in list_races(x_athlete_id) if r.day > today]
    if not races:
        raise HTTPException(status_code=404, detail="No upcoming race — add one first")
    readiness = _readiness(x_athlete_id, x_api_key, today)
    return build_plan(races[0], today, readiness)
