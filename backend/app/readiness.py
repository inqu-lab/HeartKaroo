"""Readiness scoring from intervals.icu wellness data and perceived training.

The score blends objective recovery markers (HRV, resting HR, sleep) with the
athlete's own ratings (fatigue/soreness/stress) and — crucially — how hard
recent training *felt* (session RPE and the post-workout "feel" rating).
A block that feels harder than it should is an early sign of under-recovery,
so it pulls the score, and therefore the planned load, down.
"""

from __future__ import annotations

from datetime import date, timedelta
from statistics import mean
from typing import Optional

from .models import Adjustment, PerceivedActivity, Readiness, Wellness

# Component weights; renormalised over whichever components have data.
WEIGHTS = {
    "hrv": 0.30,
    "resting_hr": 0.15,
    "sleep": 0.15,
    "subjective": 0.15,
    "perceived_training": 0.25,
}

HRV_BASELINE_DAYS = 30
PERCEIVED_WINDOW_DAYS = 7


def _clamp(x: float, lo: float = 0.0, hi: float = 100.0) -> float:
    return max(lo, min(hi, x))


def _hrv_score(today: Wellness, history: list[Wellness]) -> Optional[float]:
    """100 when today's rMSSD is at/above the 30-day baseline, falling as it drops."""
    if today.hrv is None:
        return None
    baseline = [w.hrv for w in history if w.hrv is not None and w.day < today.day]
    if len(baseline) < 3:
        return 75.0  # not enough history to judge — neutral-positive
    ratio = today.hrv / mean(baseline[-HRV_BASELINE_DAYS:])
    # ratio 1.0 -> 90, 0.85 -> ~52, >=1.05 -> 100
    return _clamp(90 + (ratio - 1.0) * 250)


def _resting_hr_score(today: Wellness, history: list[Wellness]) -> Optional[float]:
    """Elevated resting HR vs baseline costs points (≈12 per bpm above)."""
    if today.resting_hr is None:
        return None
    baseline = [w.resting_hr for w in history if w.resting_hr is not None and w.day < today.day]
    if len(baseline) < 3:
        return 75.0
    delta = today.resting_hr - mean(baseline[-HRV_BASELINE_DAYS:])
    return _clamp(95 - delta * 12)


def _sleep_score(today: Wellness) -> Optional[float]:
    if today.sleep_hours is None and today.sleep_quality is None:
        return None
    score = 0.0
    parts = 0
    if today.sleep_hours is not None:
        # 8h -> 100, 6h -> 60, 5h -> 40
        score += _clamp(100 - max(0.0, 8.0 - today.sleep_hours) * 20)
        parts += 1
    if today.sleep_quality is not None:
        score += _clamp(100 - (today.sleep_quality - 1) * 30)
        parts += 1
    return score / parts


def _subjective_score(today: Wellness) -> Optional[float]:
    """fatigue / soreness / stress are 1 (good) .. 4 (bad) in intervals.icu."""
    ratings = [r for r in (today.fatigue, today.soreness, today.stress) if r is not None]
    if not ratings:
        return None
    return _clamp(100 - (mean(ratings) - 1) * 33.3)


def _perceived_training_score(
    today: date, activities: list[PerceivedActivity]
) -> Optional[float]:
    """How well training has been perceived over the last week.

    "feel" (1 strong .. 5 weak) is the most direct signal; high session RPE
    sustained across the week compounds it. Feeling strong through a hard
    week scores high; dragging through easy sessions scores low.
    """
    recent = [
        a
        for a in activities
        if today - timedelta(days=PERCEIVED_WINDOW_DAYS) <= a.day <= today
        and (a.feel is not None or a.rpe is not None)
    ]
    if not recent:
        return None
    feels = [a.feel for a in recent if a.feel is not None]
    rpes = [a.rpe for a in recent if a.rpe is not None]
    score = 0.0
    parts = 0
    if feels:
        # feel 1 -> 100, 3 -> 50, 5 -> 0
        score += _clamp(100 - (mean(feels) - 1) * 25)
        parts += 1
    if rpes:
        # weekly mean RPE 4 or below -> 100; every point above costs 15
        score += _clamp(100 - max(0.0, mean(rpes) - 4.0) * 15)
        parts += 1
    return score / parts


def _adjustment_for(score: float) -> Adjustment:
    if score >= 65:
        return Adjustment.AS_PLANNED
    if score >= 50:
        return Adjustment.REDUCED
    if score >= 35:
        return Adjustment.EASY
    return Adjustment.REST


def compute_readiness(
    today: date,
    wellness: list[Wellness],
    activities: list[PerceivedActivity],
) -> Readiness:
    """Blend whichever signals exist into a 0–100 readiness score."""
    todays = next((w for w in wellness if w.day == today), Wellness(day=today))

    components: dict[str, Optional[float]] = {
        "hrv": _hrv_score(todays, wellness),
        "resting_hr": _resting_hr_score(todays, wellness),
        "sleep": _sleep_score(todays),
        "subjective": _subjective_score(todays),
        "perceived_training": _perceived_training_score(today, activities),
    }

    available = {k: v for k, v in components.items() if v is not None}
    if available:
        total_weight = sum(WEIGHTS[k] for k in available)
        score = sum(v * WEIGHTS[k] for k, v in available.items()) / total_weight
    else:
        score = 65.0  # no data at all: assume trainable

    explanation = []
    labels = {
        "hrv": "HRV vs 30-day baseline",
        "resting_hr": "Resting HR vs baseline",
        "sleep": "Sleep",
        "subjective": "Fatigue / soreness / stress",
        "perceived_training": "How training felt this week",
    }
    for key, value in components.items():
        if value is None:
            continue
        explanation.append(f"{labels[key]}: {value:.0f}/100")
    if not available:
        explanation.append("No wellness or perceived-effort data — defaulting to trainable.")

    return Readiness(
        day=today,
        score=round(score, 1),
        hrv_score=components["hrv"],
        resting_hr_score=components["resting_hr"],
        sleep_score=components["sleep"],
        subjective_score=components["subjective"],
        perceived_training_score=components["perceived_training"],
        adjustment=_adjustment_for(score),
        explanation=explanation,
    )
