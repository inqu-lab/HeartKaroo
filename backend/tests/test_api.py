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
