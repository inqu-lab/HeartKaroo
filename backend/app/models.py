"""Domain models shared by the intervals.icu client, readiness engine and planner."""

from __future__ import annotations

from datetime import date
from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field


class Sport(str, Enum):
    SWIM = "swim"
    BIKE = "bike"
    RUN = "run"
    BRICK = "brick"  # bike straight into run
    REST = "rest"


class RaceDistance(str, Enum):
    SPRINT = "sprint"
    OLYMPIC = "olympic"
    HALF = "half"  # 70.3
    FULL = "full"  # 140.6


class Phase(str, Enum):
    BASE = "base"
    BUILD = "build"
    PEAK = "peak"
    TAPER = "taper"
    RECOVERY = "recovery"


class Adjustment(str, Enum):
    """How a planned session was modified by readiness."""

    AS_PLANNED = "as_planned"
    REDUCED = "reduced"  # same session, lower volume/intensity
    EASY = "easy"  # intensity replaced with easy aerobic work
    REST = "rest"  # session dropped entirely


class Wellness(BaseModel):
    """One day of wellness data from intervals.icu."""

    day: date
    hrv: Optional[float] = None  # rMSSD, ms
    resting_hr: Optional[float] = None  # bpm
    sleep_hours: Optional[float] = None
    sleep_quality: Optional[int] = None  # 1 (great) .. 4 (poor)
    fatigue: Optional[int] = None  # 1 (fresh) .. 4 (very tired)
    soreness: Optional[int] = None  # 1 (none) .. 4 (very sore)
    stress: Optional[int] = None  # 1 (relaxed) .. 4 (very stressed)
    ctl: Optional[float] = None  # fitness
    atl: Optional[float] = None  # fatigue (load model)


class PerceivedActivity(BaseModel):
    """A completed activity with the athlete's perception of it."""

    day: date
    sport: Sport
    moving_time_s: int = 0
    training_load: Optional[float] = None  # intervals.icu icu_training_load
    rpe: Optional[float] = None  # session RPE, 1 (easy) .. 10 (maximal)
    feel: Optional[int] = None  # 1 (strong) .. 5 (weak)


class Race(BaseModel):
    id: Optional[int] = None
    name: str
    day: date
    distance: RaceDistance
    priority: str = Field(default="A", pattern="^[ABC]$")


class Readiness(BaseModel):
    day: date
    score: float  # 0..100
    hrv_score: Optional[float] = None
    resting_hr_score: Optional[float] = None
    sleep_score: Optional[float] = None
    subjective_score: Optional[float] = None
    perceived_training_score: Optional[float] = None
    adjustment: Adjustment
    explanation: list[str] = []


class Session(BaseModel):
    day: date
    sport: Sport
    title: str
    description: str
    duration_min: int
    intensity: str  # "recovery" | "endurance" | "tempo" | "threshold" | "vo2"
    phase: Phase
    adjustment: Adjustment = Adjustment.AS_PLANNED


class WeekPlan(BaseModel):
    start: date  # Monday
    phase: Phase
    target_hours: float
    sessions: list[Session]


class Plan(BaseModel):
    race: Race
    generated_on: date
    readiness: Optional[Readiness] = None
    weeks: list[WeekPlan]
