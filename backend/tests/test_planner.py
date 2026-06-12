from datetime import date, timedelta

from app.models import Adjustment, Phase, Race, RaceDistance, Readiness, Sport
from app.planner import build_plan, discipline_bias
from app.readiness import _adjustment_for

TODAY = date(2026, 6, 12)  # a Friday


def _race(weeks_out=16, distance=RaceDistance.OLYMPIC):
    return Race(name="Test Tri", day=TODAY + timedelta(weeks=weeks_out), distance=distance)


def _readiness(score):
    return Readiness(day=TODAY, score=score, adjustment=_adjustment_for(score))


def test_plan_spans_today_to_race_with_progressive_phases():
    plan = build_plan(_race(), TODAY)
    assert plan.weeks[0].start <= TODAY
    assert plan.weeks[-1].sessions[-1].title.startswith("RACE")
    phases = [w.phase for w in plan.weeks]
    assert phases[0] == Phase.BASE
    assert Phase.BUILD in phases
    assert Phase.PEAK in phases
    assert phases[-1] == Phase.TAPER
    assert Phase.RECOVERY in phases  # every 4th base/build week
    # Build weeks carry more hours than base, taper less than peak.
    base_h = next(w.target_hours for w in plan.weeks if w.phase == Phase.BASE)
    build_h = next(w.target_hours for w in plan.weeks if w.phase == Phase.BUILD)
    peak_h = next(w.target_hours for w in plan.weeks if w.phase == Phase.PEAK)
    assert base_h < build_h < peak_h


def test_no_sessions_scheduled_in_the_past():
    plan = build_plan(_race(), TODAY)
    for week in plan.weeks:
        for s in week.sessions:
            assert s.day >= TODAY


def test_short_runway_still_produces_taper_and_race():
    plan = build_plan(_race(weeks_out=2, distance=RaceDistance.SPRINT), TODAY)
    assert plan.weeks[-1].sessions[-1].title.startswith("RACE")
    assert all(w.phase in (Phase.PEAK, Phase.TAPER) for w in plan.weeks)


def test_full_distance_has_more_volume_than_sprint():
    full = build_plan(_race(distance=RaceDistance.FULL), TODAY)
    sprint = build_plan(_race(distance=RaceDistance.SPRINT), TODAY)
    full_peak = max(w.target_hours for w in full.weeks)
    sprint_peak = max(w.target_hours for w in sprint.weeks)
    assert full_peak > sprint_peak


def test_low_readiness_swaps_near_term_sessions_to_easy():
    plan = build_plan(_race(), TODAY, _readiness(40))
    near = [
        s
        for w in plan.weeks
        for s in w.sessions
        if TODAY <= s.day <= TODAY + timedelta(days=2)
    ]
    assert near
    assert all(s.adjustment == Adjustment.EASY for s in near)
    assert all(s.intensity in ("endurance", "recovery") for s in near)
    # Later sessions untouched.
    later = [s for w in plan.weeks for s in w.sessions if s.day > TODAY + timedelta(days=2)]
    assert all(s.adjustment == Adjustment.AS_PLANNED for s in later)


def test_very_low_readiness_turns_sessions_into_rest():
    plan = build_plan(_race(), TODAY, _readiness(20))
    near = [
        s
        for w in plan.weeks
        for s in w.sessions
        if TODAY <= s.day <= TODAY + timedelta(days=2)
    ]
    assert near
    assert all(s.sport == Sport.REST and s.duration_min == 0 for s in near)


def test_high_readiness_leaves_plan_untouched():
    plan = build_plan(_race(), TODAY, _readiness(85))
    assert all(
        s.adjustment == Adjustment.AS_PLANNED for w in plan.weeks for s in w.sessions
    )


def test_race_in_past_rejected():
    import pytest

    with pytest.raises(ValueError):
        build_plan(Race(name="x", day=TODAY, distance=RaceDistance.SPRINT), TODAY)


def _sport_minutes(plan, week_index):
    out = {}
    for s in plan.weeks[week_index].sessions:
        out[s.sport] = out.get(s.sport, 0) + s.duration_min
    return out


def test_low_ctl_anchors_early_volume_and_ramps_safely():
    template = build_plan(_race(), TODAY)
    anchored = build_plan(_race(), TODAY, ctl=20)  # ≈2.8 h/week of current training
    assert anchored.weeks[0].target_hours < template.weeks[0].target_hours
    # Every week stays inside the 8%/week ramp envelope from the start volume
    # (recovery weeks dip below it; the next week may rebound to the envelope).
    for i, week in enumerate(anchored.weeks[:-1]):  # race week recomputed
        envelope = 3.0 * 1.08**i
        assert week.target_hours <= envelope + 0.11  # 0.1 h display rounding
        assert week.target_hours <= template.weeks[i].target_hours
    # The plan still reaches template volume once the ramp catches up.
    assert anchored.weeks[-2].target_hours == template.weeks[-2].target_hours


def test_high_ctl_athlete_gets_full_template():
    template = build_plan(_race(), TODAY)
    anchored = build_plan(_race(), TODAY, ctl=80)  # ≈11 h/week, above Olympic peak
    assert [w.target_hours for w in anchored.weeks] == [
        w.target_hours for w in template.weeks
    ]


def test_weak_swimmer_gets_more_swim_time():
    bias = discipline_bias(ftp=240, weight=75, run_pace=270, swim_pace=135)
    assert bias[Sport.SWIM] > 1
    default = _sport_minutes(build_plan(_race(), TODAY), 1)
    biased = _sport_minutes(build_plan(_race(), TODAY, bias=bias), 1)
    assert biased[Sport.SWIM] > default[Sport.SWIM]
    # Total weekly time is unchanged (within rounding of 5-min sessions).
    assert abs(sum(biased.values()) - sum(default.values())) <= 10


def test_bias_is_capped():
    bias = discipline_bias(ftp=100, weight=90, run_pace=600, swim_pace=60)
    assert all(0.88 <= b <= 1.12 for b in bias.values())


def test_bias_needs_two_disciplines():
    assert discipline_bias(run_pace=270) == {}
    assert discipline_bias(ftp=250, run_pace=270) == {}  # bike needs weight too
