"""Periodized triathlon plan generation.

Builds weeks from today to race day (base → build → peak → taper, with a
recovery week every fourth week), distributes swim/bike/run/brick sessions
across each week, then adjusts the near-term sessions using the athlete's
readiness — which itself reflects how training has been perceived.
"""

from __future__ import annotations

from datetime import date, timedelta

from .models import (
    Adjustment,
    Phase,
    Plan,
    Race,
    RaceDistance,
    Readiness,
    Session,
    Sport,
    WeekPlan,
)

# Peak weekly hours per race distance; earlier phases scale down from this.
PEAK_HOURS = {
    RaceDistance.SPRINT: 7.0,
    RaceDistance.OLYMPIC: 9.0,
    RaceDistance.HALF: 12.0,
    RaceDistance.FULL: 15.0,
}

PHASE_HOURS_FACTOR = {
    Phase.BASE: 0.70,
    Phase.BUILD: 0.85,
    Phase.PEAK: 1.00,
    Phase.TAPER: 0.50,
    Phase.RECOVERY: 0.55,
}

TAPER_WEEKS = {
    RaceDistance.SPRINT: 1,
    RaceDistance.OLYMPIC: 1,
    RaceDistance.HALF: 2,
    RaceDistance.FULL: 3,
}

# (sport, weekday 0=Mon, share of weekly hours, intensity, title)
# Intensity of the quality sessions varies by phase below.
WEEK_TEMPLATE = [
    (Sport.SWIM, 0, 0.12, "quality", "Swim — main set"),
    (Sport.BIKE, 1, 0.16, "quality", "Bike — intervals"),
    (Sport.RUN, 2, 0.12, "quality", "Run — intervals"),
    (Sport.SWIM, 3, 0.10, "endurance", "Swim — technique + aerobic"),
    (Sport.BRICK, 4, 0.14, "endurance", "Brick — bike into run"),
    (Sport.BIKE, 5, 0.22, "endurance", "Long ride"),
    (Sport.RUN, 6, 0.14, "endurance", "Long run"),
]

QUALITY_BY_PHASE = {
    Phase.BASE: "tempo",
    Phase.BUILD: "threshold",
    Phase.PEAK: "vo2",
    Phase.TAPER: "threshold",
    Phase.RECOVERY: "endurance",
}

DESCRIPTIONS = {
    "endurance": "Steady zone 2. Should feel comfortable — RPE 3-4.",
    "tempo": "Main set at zone 3 / RPE 5-6 after a thorough warm-up.",
    "threshold": "Main set at threshold (RPE 7-8), e.g. 3-4 x 8-10 min with short recoveries.",
    "vo2": "Short hard repeats at RPE 9, e.g. 5-6 x 3 min with equal recovery.",
    "recovery": "Very easy spin/jog/swim, RPE 2 at most. Skip if still tired.",
}


def _monday(d: date) -> date:
    return d - timedelta(days=d.weekday())


def _phases(n_weeks: int, distance: RaceDistance) -> list[Phase]:
    """Assign a phase to each week, last week ending at the race."""
    taper = min(TAPER_WEEKS[distance], max(1, n_weeks - 1)) if n_weeks > 1 else n_weeks
    remaining = n_weeks - taper
    peak = min(2, remaining)
    remaining -= peak
    build = min(max(remaining // 2, 0), remaining)
    base = remaining - build

    phases = (
        [Phase.BASE] * base + [Phase.BUILD] * build + [Phase.PEAK] * peak + [Phase.TAPER] * taper
    )
    # Every 4th week of base/build is a recovery week (never the first week).
    for i in range(3, base + build, 4):
        phases[i] = Phase.RECOVERY
    return phases


def _sessions_for_week(monday: date, phase: Phase, hours: float, today: date) -> list[Session]:
    sessions = []
    quality_intensity = QUALITY_BY_PHASE[phase]
    for sport, weekday, share, kind, title in WEEK_TEMPLATE:
        day = monday + timedelta(days=weekday)
        if day < today:
            continue
        intensity = quality_intensity if kind == "quality" else "endurance"
        if phase == Phase.RECOVERY:
            intensity = "recovery" if kind == "quality" else "endurance"
        duration = round(hours * 60 * share / 5) * 5
        if duration < 20:
            continue
        sessions.append(
            Session(
                day=day,
                sport=sport,
                title=title,
                description=DESCRIPTIONS[intensity],
                duration_min=duration,
                intensity=intensity,
                phase=phase,
            )
        )
    return sessions


def _race_week_sessions(monday: date, race: Race, hours: float, today: date) -> list[Session]:
    """Race week: sharpen early, rest, then race."""
    template = [
        (Sport.SWIM, 0, 30, "endurance", "Swim — easy with a few race-pace 50s"),
        (Sport.BIKE, 1, 40, "endurance", "Bike — easy with 3 x 1 min race-pace"),
        (Sport.RUN, 2, 25, "endurance", "Run — easy with strides"),
        (Sport.REST, 3, 0, "recovery", "Rest"),
        (Sport.BIKE, 4, 20, "recovery", "Openers — short spin, 2-3 pickups"),
    ]
    sessions = []
    for sport, weekday, duration, intensity, title in template:
        day = monday + timedelta(days=weekday)
        if day < today or day >= race.day:
            continue
        sessions.append(
            Session(
                day=day,
                sport=sport,
                title=title,
                description=DESCRIPTIONS[intensity] if sport != Sport.REST else "Full rest day.",
                duration_min=duration,
                intensity=intensity,
                phase=Phase.TAPER,
            )
        )
    sessions.append(
        Session(
            day=race.day,
            sport=Sport.BRICK,
            title=f"RACE: {race.name}",
            description=f"{race.distance.value.capitalize()} triathlon. Execute your plan — trust the taper.",
            duration_min=0,
            intensity="vo2",
            phase=Phase.TAPER,
        )
    )
    return sessions


def _apply_readiness(sessions: list[Session], readiness: Readiness, today: date) -> None:
    """Adjust the next few days in place based on today's readiness.

    Only the near term is touched: readiness is a daily signal, and the plan
    regenerates as new wellness/perception data arrives.
    """
    horizon = today + timedelta(days=2)
    for s in sessions:
        if not (today <= s.day <= horizon) or s.title.startswith("RACE"):
            continue
        if readiness.adjustment == Adjustment.REDUCED:
            s.adjustment = Adjustment.REDUCED
            s.duration_min = round(s.duration_min * 0.75 / 5) * 5
            if s.intensity in ("vo2", "threshold"):
                s.intensity = "tempo"
                s.description = DESCRIPTIONS["tempo"]
            s.description += " (Reduced: readiness is moderate — training has felt harder than it should.)"
        elif readiness.adjustment == Adjustment.EASY:
            s.adjustment = Adjustment.EASY
            s.duration_min = round(s.duration_min * 0.6 / 5) * 5
            if s.intensity != "recovery":
                s.intensity = "endurance"
            s.description = (
                DESCRIPTIONS["endurance"]
                + " (Swapped to easy aerobic: low readiness from wellness and perceived effort.)"
            )
        elif readiness.adjustment == Adjustment.REST:
            s.adjustment = Adjustment.REST
            s.sport = Sport.REST
            s.title = "Rest — readiness very low"
            s.duration_min = 0
            s.intensity = "recovery"
            s.description = (
                "Recovery markers and how training has felt both point to deep fatigue. "
                "Take the day off; the plan resumes when readiness recovers."
            )


def build_plan(race: Race, today: date, readiness: Readiness | None = None) -> Plan:
    if race.day <= today:
        raise ValueError("Race date must be in the future")

    first_monday = _monday(today)
    race_monday = _monday(race.day)
    n_weeks = (race_monday - first_monday).days // 7 + 1
    phases = _phases(n_weeks, race.distance)
    peak_hours = PEAK_HOURS[race.distance]

    weeks = []
    for i in range(n_weeks):
        monday = first_monday + timedelta(weeks=i)
        phase = phases[i]
        hours = peak_hours * PHASE_HOURS_FACTOR[phase]
        if monday == race_monday:
            sessions = _race_week_sessions(monday, race, hours, today)
            hours = sum(s.duration_min for s in sessions) / 60
        else:
            sessions = _sessions_for_week(monday, phase, hours, today)
        weeks.append(
            WeekPlan(start=monday, phase=phase, target_hours=round(hours, 1), sessions=sessions)
        )

    if readiness is not None and weeks:
        for week in weeks[:2]:
            _apply_readiness(week.sessions, readiness, today)

    return Plan(race=race, generated_on=today, readiness=readiness, weeks=weeks)
