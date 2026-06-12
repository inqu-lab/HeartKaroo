from datetime import date, timedelta

from app.models import Phase, Race, RaceDistance, Session, Sport
from app.planner import build_plan
from app.workouts import build_events, workout_text

TODAY = date(2026, 6, 12)


def _session(intensity, duration, sport=Sport.BIKE):
    return Session(
        day=TODAY,
        sport=sport,
        title="x",
        description="",
        duration_min=duration,
        intensity=intensity,
        phase=Phase.BUILD,
    )


def test_threshold_workout_has_warmup_repeats_cooldown():
    text = workout_text(_session("threshold", 75))
    assert "Warmup\n- 15m Z1" in text
    assert "Main 4x\n- 8m Z4\n- 4m Z1" in text
    assert "Cooldown" in text


def test_step_durations_sum_to_session_duration():
    for intensity in ("endurance", "tempo", "threshold", "vo2", "recovery"):
        for duration in (30, 45, 60, 90):
            text = workout_text(_session(intensity, duration))
            total = 0
            repeats = 1
            for line in text.splitlines():
                if line and not line.startswith("-"):
                    repeats = int(line.split()[-1][:-1]) if line.endswith("x") else 1
                if line.startswith("-"):
                    total_part = int(line.split()[1][:-1])
                    total += total_part * repeats
                    # recovery line of a repeat shares the multiplier
            assert total == duration, f"{intensity} {duration}: {text}"


def test_build_events_covers_horizon_and_maps_types():
    race = Race(name="T", day=TODAY + timedelta(weeks=10), distance=RaceDistance.OLYMPIC)
    plan = build_plan(race, TODAY)
    events = build_events(plan, TODAY, 14)
    assert events
    days = sorted({e["start_date_local"][:10] for e in events})
    assert days[0] >= TODAY.isoformat()
    assert days[-1] <= (TODAY + timedelta(days=14)).isoformat()
    assert {e["type"] for e in events} <= {"Swim", "Ride", "Run"}
    assert all(e["category"] == "WORKOUT" for e in events)
    assert all(e["external_id"].startswith("triplanner-") for e in events)
    ids = [e["external_id"] for e in events]
    assert len(ids) == len(set(ids))


def test_brick_becomes_bike_then_run():
    race = Race(name="T", day=TODAY + timedelta(weeks=10), distance=RaceDistance.OLYMPIC)
    plan = build_plan(race, TODAY)
    events = build_events(plan, TODAY, 14)
    bricks = [e for e in events if "Brick" in e["name"]]
    assert len(bricks) % 2 == 0 and bricks
    assert bricks[0]["type"] == "Ride" and "1/2" in bricks[0]["name"]
    assert bricks[1]["type"] == "Run" and "2/2" in bricks[1]["name"]


def test_race_and_rest_days_not_synced():
    race = Race(name="T", day=TODAY + timedelta(days=8), distance=RaceDistance.SPRINT)
    plan = build_plan(race, TODAY)
    events = build_events(plan, TODAY, 14)
    assert all("RACE" not in e["name"] for e in events)
    assert all(e["type"] != "Rest" for e in events)
