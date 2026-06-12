"""Minimal intervals.icu API client.

Auth is HTTP Basic with username "API_KEY" and the athlete's personal API key
as the password (Settings → Developer Settings in intervals.icu).
"""

from __future__ import annotations

from datetime import date, timedelta

import httpx

from .models import PerceivedActivity, Sport, Wellness

BASE_URL = "https://intervals.icu/api/v1"

SPORT_MAP = {
    "Swim": Sport.SWIM,
    "OpenWaterSwim": Sport.SWIM,
    "Ride": Sport.BIKE,
    "VirtualRide": Sport.BIKE,
    "GravelRide": Sport.BIKE,
    "MountainBikeRide": Sport.BIKE,
    "Run": Sport.RUN,
    "VirtualRun": Sport.RUN,
    "TrailRun": Sport.RUN,
}


class IntervalsClient:
    def __init__(self, athlete_id: str, api_key: str, base_url: str = BASE_URL):
        self._athlete_id = athlete_id
        self._client = httpx.Client(
            base_url=base_url,
            auth=("API_KEY", api_key),
            timeout=20.0,
        )

    def close(self) -> None:
        self._client.close()

    def _get(self, path: str, **params) -> list[dict]:
        resp = self._client.get(f"/athlete/{self._athlete_id}{path}", params=params)
        resp.raise_for_status()
        return resp.json()

    def weight(self) -> float | None:
        """Athlete body weight in kg, if set in their profile."""
        resp = self._client.get(f"/athlete/{self._athlete_id}")
        resp.raise_for_status()
        profile = resp.json()
        return profile.get("weight") or profile.get("icu_weight")

    def sport_settings(self) -> dict:
        """Current FTP (W), run threshold pace (s/km) and swim pace (s/100m)."""
        out = {"ftp": None, "run_threshold_pace": None, "swim_threshold_pace": None}
        for setting in self._get("/sport-settings"):
            types = setting.get("types") or []
            pace = setting.get("threshold_pace")  # m/s
            if "Ride" in types and setting.get("ftp"):
                out["ftp"] = setting["ftp"]
            if "Run" in types and pace:
                out["run_threshold_pace"] = 1000 / pace
            if "Swim" in types and pace:
                out["swim_threshold_pace"] = 100 / pace
        return out

    def planned_events(self, oldest: date, newest: date) -> list[dict]:
        return self._get("/events", oldest=oldest.isoformat(), newest=newest.isoformat())

    def create_event(self, event: dict) -> dict:
        resp = self._client.post(f"/athlete/{self._athlete_id}/events", json=event)
        resp.raise_for_status()
        return resp.json()

    def delete_event(self, event_id: int) -> None:
        resp = self._client.delete(f"/athlete/{self._athlete_id}/events/{event_id}")
        resp.raise_for_status()

    def wellness(self, today: date, days: int = 35) -> list[Wellness]:
        rows = self._get(
            "/wellness",
            oldest=(today - timedelta(days=days)).isoformat(),
            newest=today.isoformat(),
        )
        out = []
        for row in rows:
            sleep_secs = row.get("sleepSecs")
            out.append(
                Wellness(
                    day=date.fromisoformat(row["id"]),
                    hrv=row.get("hrv"),
                    resting_hr=row.get("restingHR"),
                    sleep_hours=sleep_secs / 3600 if sleep_secs else None,
                    sleep_quality=row.get("sleepQuality"),
                    fatigue=row.get("fatigue"),
                    soreness=row.get("soreness"),
                    stress=row.get("stress"),
                    ctl=row.get("ctl"),
                    atl=row.get("atl"),
                )
            )
        return out

    def activities(self, today: date, days: int = 14) -> list[PerceivedActivity]:
        rows = self._get(
            "/activities",
            oldest=(today - timedelta(days=days)).isoformat(),
            newest=today.isoformat(),
        )
        out = []
        for row in rows:
            sport = SPORT_MAP.get(row.get("type", ""))
            if sport is None:
                continue
            out.append(
                PerceivedActivity(
                    day=date.fromisoformat(row["start_date_local"][:10]),
                    sport=sport,
                    moving_time_s=row.get("moving_time") or 0,
                    training_load=row.get("icu_training_load"),
                    rpe=row.get("icu_rpe"),
                    feel=row.get("feel"),
                )
            )
        return out
