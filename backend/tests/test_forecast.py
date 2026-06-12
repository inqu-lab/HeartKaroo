from datetime import date, timedelta

from app.forecast import build_forecast
from app.models import FitnessSnapshot, Race, RaceDistance
from app.planner import build_plan

TODAY = date(2026, 6, 12)


def _plan(weeks_out=16):
    race = Race(name="T", day=TODAY + timedelta(weeks=weeks_out), distance=RaceDistance.OLYMPIC)
    return build_plan(race, TODAY)


def _current(ctl=45.0):
    return FitnessSnapshot(
        day=TODAY,
        ftp=270.0,
        run_threshold_pace=270.0,  # 4:30 /km
        swim_threshold_pace=105.0,  # 1:45 /100m
        ctl=ctl,
    )


def test_ramping_plan_improves_ftp_and_paces():
    fc = build_forecast(_plan(), _current(), readiness_score=70)
    assert fc.race_day.ftp > 270
    assert fc.race_day.ftp < 270 * 1.11  # capped, plausible
    assert fc.race_day.run_threshold_pace < 270  # faster = fewer s/km
    assert fc.race_day.swim_threshold_pace < 105
    # Run improves relatively more than swim.
    run_gain = 1 - fc.race_day.run_threshold_pace / 270
    swim_gain = 1 - fc.race_day.swim_threshold_pace / 105
    assert run_gain > swim_gain
    assert len(fc.weekly) == len(_plan().weeks)


def test_poorly_perceived_training_lowers_projected_gains():
    high = build_forecast(_plan(), _current(), readiness_score=85)
    low = build_forecast(_plan(), _current(), readiness_score=35)
    assert high.race_day.ftp > low.race_day.ftp


def test_high_starting_ctl_gains_less():
    fresh = build_forecast(_plan(), _current(ctl=30), readiness_score=70)
    seasoned = build_forecast(_plan(), _current(ctl=70), readiness_score=70)
    assert fresh.race_day.ftp >= seasoned.race_day.ftp


def test_missing_markers_are_left_none():
    current = FitnessSnapshot(day=TODAY, ftp=250.0, ctl=50.0)
    fc = build_forecast(_plan(), current, readiness_score=70)
    assert fc.race_day.ftp is not None
    assert fc.race_day.run_threshold_pace is None
    assert fc.race_day.swim_threshold_pace is None


def test_projection_trends_upward_over_the_plan():
    fc = build_forecast(_plan(), _current(), readiness_score=70)
    ftps = [w.ftp for w in fc.weekly]
    assert ftps[-1] > ftps[0]
    # Peak projected fitness arrives late in the plan (recovery-week dips are fine).
    assert max(ftps) == max(ftps[-4:])
