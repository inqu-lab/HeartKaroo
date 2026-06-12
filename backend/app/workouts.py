"""Turn planned sessions into structured workouts on the intervals.icu calendar.

intervals.icu parses the event description as workout steps ("- 8m Z4" etc.)
and its Garmin Connect integration pushes the planned workout to the athlete's
watch for that day, where it can be followed step-by-step. Bricks become two
back-to-back events because Garmin can't follow steps across multisport legs.
"""

from __future__ import annotations

from datetime import date, timedelta

from .models import Plan, Session, Sport

EVENT_TAG = "triplanner"

EVENT_TYPE = {Sport.SWIM: "Swim", Sport.BIKE: "Ride", Sport.RUN: "Run"}

# Interval sessions: (work_min, work_zone, recovery_min) per repeat.
REPEATS = {
    "tempo": (10, "Z3", 3),
    "threshold": (8, "Z4", 4),
    "vo2": (3, "Z5", 3),
}


def workout_text(session: Session) -> str:
    """intervals.icu workout syntax for one session."""
    d = session.duration_min
    if session.intensity == "recovery":
        return f"Recovery\n- {d}m Z1"
    if session.intensity == "endurance":
        warmup = 10 if d >= 40 else 5
        cooldown = 5
        main = d - warmup - cooldown
        return (
            f"Warmup\n- {warmup}m Z1\n\n"
            f"Main\n- {main}m Z2\n\n"
            f"Cooldown\n- {cooldown}m Z1"
        )
    work, zone, recovery = REPEATS[session.intensity]
    warmup = 15 if d >= 60 else 10
    rep = work + recovery
    n = max(1, (d - warmup - 5) // rep)
    cooldown = max(5, d - warmup - n * rep)
    return (
        f"Warmup\n- {warmup}m Z1\n\n"
        f"Main {n}x\n- {work}m {zone}\n- {recovery}m Z1\n\n"
        f"Cooldown\n- {cooldown}m Z1"
    )


def _event(session: Session, sport: Sport, name: str, text: str, idx: int) -> dict:
    return {
        "category": "WORKOUT",
        "start_date_local": f"{session.day.isoformat()}T00:00:00",
        "type": EVENT_TYPE[sport],
        "name": name,
        "description": text,
        "external_id": f"{EVENT_TAG}-{session.day.isoformat()}-{idx}",
    }


def build_events(plan: Plan, today: date, horizon_days: int) -> list[dict]:
    """Calendar events for the next `horizon_days` of the plan.

    Only the near term is pushed: further out the plan will still be
    reshaped by future readiness, so syncing it would churn the calendar.
    """
    horizon = today + timedelta(days=horizon_days)
    events: list[dict] = []
    idx = 0
    for week in plan.weeks:
        for s in week.sessions:
            if not (today <= s.day <= horizon):
                continue
            if s.sport == Sport.REST or s.duration_min <= 0 or s.title.startswith("RACE"):
                continue
            if s.sport == Sport.BRICK:
                bike = s.model_copy(update={"duration_min": round(s.duration_min * 0.6 / 5) * 5})
                run = s.model_copy(update={"duration_min": round(s.duration_min * 0.4 / 5) * 5})
                events.append(
                    _event(s, Sport.BIKE, f"{s.title} (1/2 bike)", workout_text(bike), idx)
                )
                idx += 1
                events.append(
                    _event(s, Sport.RUN, f"{s.title} (2/2 run)", workout_text(run), idx)
                )
            else:
                events.append(_event(s, s.sport, s.title, workout_text(s), idx))
            idx += 1
    return events
