from datetime import date, timedelta

from fastapi.testclient import TestClient

from app import main
from app.models import PerceivedActivity, Readiness, Sport, Wellness
from app.readiness import compute_readiness


def _stub_readiness(athlete_id, api_key, today):
    wellness = [Wellness(day=today, sleep_hours=8, fatigue=1, soreness=1, stress=1)]
    activities = [
        PerceivedActivity(day=today - timedelta(days=1), sport=Sport.RUN, rpe=5, feel=2)
    ]
    return compute_readiness(today, wellness, activities)


def _client(tmp_path, monkeypatch):
    monkeypatch.setattr(main, "DB_PATH", tmp_path / "test.db")
    monkeypatch.setattr(main, "_readiness", _stub_readiness)
    return TestClient(main.app)


HEADERS = {"X-Athlete-Id": "i12345", "X-Api-Key": "secret"}


def test_race_crud_and_plan(tmp_path, monkeypatch):
    client = _client(tmp_path, monkeypatch)

    assert client.get("/races", headers=HEADERS).json() == []
    assert client.get("/plan", headers=HEADERS).status_code == 404

    race = {
        "name": "Stockholm Triathlon",
        "day": (date.today() + timedelta(weeks=12)).isoformat(),
        "distance": "olympic",
        "priority": "A",
    }
    created = client.post("/races", json=race, headers=HEADERS)
    assert created.status_code == 201
    race_id = created.json()["id"]

    plan = client.get("/plan", headers=HEADERS)
    assert plan.status_code == 200
    body = plan.json()
    assert body["race"]["name"] == "Stockholm Triathlon"
    assert body["readiness"]["score"] > 0
    assert len(body["weeks"]) >= 12

    assert client.delete(f"/races/{race_id}", headers=HEADERS).status_code == 204
    assert client.get("/races", headers=HEADERS).json() == []
    assert client.delete(f"/races/{race_id}", headers=HEADERS).status_code == 404


class FakeIntervals:
    """Stands in for IntervalsClient in /sync and /forecast tests."""

    created: list = []
    deleted: list = []

    def __init__(self):
        FakeIntervals.created = []
        FakeIntervals.deleted = []

    def close(self):
        pass

    def wellness(self, today, days=35):
        return [Wellness(day=today, sleep_hours=8, fatigue=1, soreness=1, stress=1, ctl=50.0)]

    def activities(self, today, days=14):
        return [PerceivedActivity(day=today - timedelta(days=1), sport=Sport.RUN, rpe=5, feel=2)]

    def sport_settings(self):
        return {"ftp": 270.0, "run_threshold_pace": 270.0, "swim_threshold_pace": 105.0}

    def planned_events(self, oldest, newest):
        return [
            {"id": 1, "external_id": "triplanner-old-1"},
            {"id": 2, "external_id": None},  # not ours — must survive
        ]

    def create_event(self, event):
        FakeIntervals.created.append(event)
        return event

    def delete_event(self, event_id):
        FakeIntervals.deleted.append(event_id)


def _client_with_fake(tmp_path, monkeypatch):
    monkeypatch.setattr(main, "DB_PATH", tmp_path / "test.db")
    monkeypatch.setattr(main, "_client", lambda athlete_id, api_key: FakeIntervals())
    return TestClient(main.app)


def test_sync_pushes_structured_workouts(tmp_path, monkeypatch):
    client = _client_with_fake(tmp_path, monkeypatch)
    race = {
        "name": "Sync Tri",
        "day": (date.today() + timedelta(weeks=10)).isoformat(),
        "distance": "olympic",
    }
    client.post("/races", json=race, headers=HEADERS)

    resp = client.post("/sync", headers=HEADERS)
    assert resp.status_code == 200
    body = resp.json()
    assert body["created"] == len(FakeIntervals.created) > 0
    assert body["deleted"] == 1
    assert FakeIntervals.deleted == [1]  # only our old event removed
    assert all("Z" in e["description"] for e in FakeIntervals.created)


def test_forecast_projects_to_race_day(tmp_path, monkeypatch):
    client = _client_with_fake(tmp_path, monkeypatch)
    race_day = (date.today() + timedelta(weeks=12)).isoformat()
    client.post(
        "/races",
        json={"name": "Forecast Tri", "day": race_day, "distance": "olympic"},
        headers=HEADERS,
    )

    resp = client.get("/forecast", headers=HEADERS)
    assert resp.status_code == 200
    body = resp.json()
    assert body["race_day"]["day"] == race_day
    assert body["race_day"]["ftp"] > body["current"]["ftp"] == 270.0
    assert body["race_day"]["run_threshold_pace"] < 270.0
    assert len(body["weekly"]) >= 12


def test_races_are_scoped_per_athlete(tmp_path, monkeypatch):
    client = _client(tmp_path, monkeypatch)
    race = {
        "name": "Hidden Race",
        "day": (date.today() + timedelta(weeks=8)).isoformat(),
        "distance": "sprint",
    }
    client.post("/races", json=race, headers=HEADERS)
    other = {"X-Athlete-Id": "i99999", "X-Api-Key": "secret"}
    assert client.get("/races", headers=other).json() == []
