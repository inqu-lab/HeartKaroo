"""FastAPI backend for the TriPlanner iPhone app.

The app sends the athlete's intervals.icu credentials with each request
(stored in the iOS Keychain client-side); the backend keeps no athlete data
except the race list, held in a small SQLite file.
"""

from __future__ import annotations

import sqlite3
from contextlib import closing, contextmanager
from datetime import date, timedelta
from pathlib import Path

import httpx
from fastapi import FastAPI, Header, HTTPException

from .forecast import build_forecast
from .intervals import IntervalsClient
from .models import FitnessSnapshot, Forecast, Plan, Race, RaceDistance, Readiness, SyncResult
from .planner import build_plan
from .readiness import compute_readiness
from .workouts import EVENT_TAG, build_events

SYNC_HORIZON_DAYS = 14

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


@contextmanager
def _intervals(athlete_id: str, api_key: str):
    """Client with intervals.icu errors translated to HTTP errors."""
    client = _client(athlete_id, api_key)
    try:
        yield client
    except httpx.HTTPStatusError as e:
        raise HTTPException(
            status_code=e.response.status_code,
            detail=f"intervals.icu request failed: {e.response.status_code}",
        )
    except httpx.HTTPError as e:
        raise HTTPException(status_code=502, detail=f"intervals.icu unreachable: {e}")
    finally:
        client.close()


def _readiness(athlete_id: str, api_key: str, today: date) -> Readiness:
    with _intervals(athlete_id, api_key) as client:
        wellness = client.wellness(today)
        activities = client.activities(today)
    return compute_readiness(today, wellness, activities)


def _next_race(athlete_id: str, today: date) -> Race:
    races = [r for r in list_races(athlete_id) if r.day > today]
    if not races:
        raise HTTPException(status_code=404, detail="No upcoming race — add one first")
    return races[0]


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
    race = _next_race(x_athlete_id, today)
    readiness = _readiness(x_athlete_id, x_api_key, today)
    return build_plan(race, today, readiness)


@app.get("/forecast", response_model=Forecast)
def get_forecast(
    x_athlete_id: str = Header(...),
    x_api_key: str = Header(...),
) -> Forecast:
    """Projected FTP and threshold pacing at race day, from the planned load."""
    today = date.today()
    race = _next_race(x_athlete_id, today)
    with _intervals(x_athlete_id, x_api_key) as client:
        wellness = client.wellness(today)
        activities = client.activities(today)
        settings = client.sport_settings()
    readiness = compute_readiness(today, wellness, activities)
    plan = build_plan(race, today, readiness)
    ctl = next((w.ctl for w in reversed(wellness) if w.ctl is not None), None)
    current = FitnessSnapshot(day=today, ctl=ctl, **settings)
    return build_forecast(plan, current, readiness.score)


@app.post("/sync", response_model=SyncResult)
def sync_to_calendar(
    x_athlete_id: str = Header(...),
    x_api_key: str = Header(...),
) -> SyncResult:
    """Push the next two weeks to the intervals.icu calendar as structured
    workouts. intervals.icu's Garmin integration then sends each one to the
    athlete's watch on its day. Previously synced TriPlanner workouts in the
    window are replaced."""
    today = date.today()
    race = _next_race(x_athlete_id, today)
    readiness = _readiness(x_athlete_id, x_api_key, today)
    plan = build_plan(race, today, readiness)
    events = build_events(plan, today, SYNC_HORIZON_DAYS)
    with _intervals(x_athlete_id, x_api_key) as client:
        existing = client.planned_events(today, today + timedelta(days=SYNC_HORIZON_DAYS))
        deleted = 0
        for event in existing:
            if (event.get("external_id") or "").startswith(EVENT_TAG):
                client.delete_event(event["id"])
                deleted += 1
        for event in events:
            client.create_event(event)
    return SyncResult(created=len(events), deleted=deleted, horizon_days=SYNC_HORIZON_DAYS)
