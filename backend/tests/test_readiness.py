from datetime import date, timedelta

from app.models import Adjustment, PerceivedActivity, Sport, Wellness
from app.readiness import compute_readiness

TODAY = date(2026, 6, 12)


def _history(hrv=65.0, rhr=48.0, days=30):
    return [
        Wellness(day=TODAY - timedelta(days=i), hrv=hrv, resting_hr=rhr)
        for i in range(days, 0, -1)
    ]


def _today(**kwargs):
    return Wellness(day=TODAY, **kwargs)


def test_good_wellness_and_strong_perception_trains_as_planned():
    wellness = _history() + [
        _today(hrv=68, resting_hr=47, sleep_hours=8, sleep_quality=1, fatigue=1, soreness=1, stress=1)
    ]
    activities = [
        PerceivedActivity(day=TODAY - timedelta(days=i), sport=Sport.RUN, rpe=5, feel=1)
        for i in range(1, 4)
    ]
    r = compute_readiness(TODAY, wellness, activities)
    assert r.score >= 80
    assert r.adjustment == Adjustment.AS_PLANNED


def test_training_perceived_as_very_hard_drags_score_down():
    # Identical wellness, only perception differs.
    wellness = _history() + [_today(hrv=65, resting_hr=48, sleep_hours=8)]
    strong = [
        PerceivedActivity(day=TODAY - timedelta(days=i), sport=Sport.BIKE, rpe=4, feel=1)
        for i in range(1, 6)
    ]
    weak = [
        PerceivedActivity(day=TODAY - timedelta(days=i), sport=Sport.BIKE, rpe=9, feel=5)
        for i in range(1, 6)
    ]
    r_strong = compute_readiness(TODAY, wellness, strong)
    r_weak = compute_readiness(TODAY, wellness, weak)
    assert r_strong.score - r_weak.score > 15
    assert r_strong.perceived_training_score == 100
    assert r_weak.perceived_training_score < 15


def test_suppressed_hrv_and_poor_subjective_forces_easy_or_rest():
    wellness = _history() + [
        _today(hrv=45, resting_hr=56, sleep_hours=5, sleep_quality=4, fatigue=4, soreness=4, stress=4)
    ]
    activities = [
        PerceivedActivity(day=TODAY - timedelta(days=1), sport=Sport.RUN, rpe=9, feel=5)
    ]
    r = compute_readiness(TODAY, wellness, activities)
    assert r.score < 35
    assert r.adjustment == Adjustment.REST


def test_no_data_defaults_to_trainable():
    r = compute_readiness(TODAY, [], [])
    assert r.adjustment == Adjustment.AS_PLANNED
    assert r.score == 65.0


def test_old_activities_ignored_for_perception():
    wellness = _history() + [_today(hrv=65, resting_hr=48)]
    stale = [
        PerceivedActivity(day=TODAY - timedelta(days=10), sport=Sport.RUN, rpe=10, feel=5)
    ]
    r = compute_readiness(TODAY, wellness, stale)
    assert r.perceived_training_score is None
