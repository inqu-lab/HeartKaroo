"""Project FTP and threshold pacing forward to race day.

The model simulates the classic CTL/ATL impulse-response over the *planned*
training load, then maps fitness gained (CTL above today's level) to
performance: roughly +0.35% FTP per CTL point, scaled by how well the athlete
is absorbing training (today's readiness — wellness plus perceived effort),
plus a small freshness bonus when the taper leaves them fresh (positive TSB).
Run pace improves at ~60% of the relative FTP gain and swim pace at ~40%,
reflecting how much slower economy adapts in those sports.
"""

from __future__ import annotations

from datetime import timedelta

from .models import FitnessSnapshot, Forecast, Plan

# Estimated intensity factor per planned intensity; TSS ≈ IF² × hours × 100.
INTENSITY_IF = {
    "recovery": 0.55,
    "endurance": 0.65,
    "tempo": 0.78,
    "threshold": 0.88,
    "vo2": 0.95,
}

CTL_TC = 42.0  # days
ATL_TC = 7.0
GAIN_PER_CTL_POINT = 0.0035  # fractional FTP gain
FRESHNESS_GAIN_PER_TSB = 0.0005
RUN_FACTOR = 0.6
SWIM_FACTOR = 0.4
MAX_GAIN = 0.10
MAX_LOSS = -0.05


def _weekly_tss(week) -> float:
    return sum(
        INTENSITY_IF.get(s.intensity, 0.65) ** 2 * (s.duration_min / 60) * 100
        for s in week.sessions
    )


def _clamp(x: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, x))


def build_forecast(
    plan: Plan,
    current: FitnessSnapshot,
    readiness_score: float | None = None,
) -> Forecast:
    ctl0 = current.ctl if current.ctl is not None else 40.0
    # How well load converts to fitness: readiness 65 ≈ neutral (1.0).
    score = readiness_score if readiness_score is not None else 65.0
    absorption = _clamp(0.6 + score / 100 * 0.6, 0.6, 1.15)

    ctl, atl = ctl0, ctl0
    weekly: list[FitnessSnapshot] = []

    def snapshot(day, gain, ctl_now) -> FitnessSnapshot:
        return FitnessSnapshot(
            day=day,
            ftp=round(current.ftp * (1 + gain), 1) if current.ftp else None,
            run_threshold_pace=(
                round(current.run_threshold_pace / (1 + RUN_FACTOR * gain), 1)
                if current.run_threshold_pace
                else None
            ),
            swim_threshold_pace=(
                round(current.swim_threshold_pace / (1 + SWIM_FACTOR * gain), 1)
                if current.swim_threshold_pace
                else None
            ),
            ctl=round(ctl_now, 1),
        )

    gain = 0.0
    for week in plan.weeks:
        daily_tss = _weekly_tss(week) / 7
        for _ in range(7):
            ctl += (daily_tss - ctl) / CTL_TC
            atl += (daily_tss - atl) / ATL_TC
        fitness_gain = GAIN_PER_CTL_POINT * (ctl - ctl0) * absorption
        freshness = FRESHNESS_GAIN_PER_TSB * _clamp(ctl - atl, -30, 25)
        gain = _clamp(fitness_gain + freshness, MAX_LOSS, MAX_GAIN)
        weekly.append(snapshot(week.start + timedelta(days=6), gain, ctl))

    race_day = snapshot(plan.race.day, gain, ctl)

    explanation = [
        f"Planned load ramps CTL from {ctl0:.0f} to {ctl:.0f} by race day.",
        f"Training absorption {absorption:.2f}x from current readiness ({score:.0f}/100).",
    ]
    if current.ftp:
        explanation.append(
            f"Projected FTP {current.ftp:.0f} W → {race_day.ftp:.0f} W ({gain * 100:+.1f}%)."
        )

    return Forecast(
        race=plan.race,
        current=current,
        race_day=race_day,
        weekly=weekly,
        explanation=explanation,
    )
