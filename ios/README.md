# TriPlanner iPhone app

SwiftUI app for the TriPlanner backend (`../backend`). Plans triathlon
training toward your next race and adapts each day to your intervals.icu
wellness data and how your recent training felt.

## Screens

- **Today** — readiness gauge (0–100) with a per-signal breakdown (HRV,
  resting HR, sleep, subjective wellness, perceived training) and today's
  sessions, flagged when they've been reduced/swapped/rested for readiness.
- **Plan** — every week from now to race day with phase, target hours and
  the session list.
- **Races** — add/remove upcoming races (sprint/olympic/half/full, A/B/C
  priority). The plan targets the next upcoming race.
- **Settings** — backend URL, intervals.icu athlete ID and API key (key is
  kept in the iOS Keychain).

## Build

Requires Xcode 15+ and [XcodeGen](https://github.com/yonaskolb/XcodeGen):

```bash
cd ios/TriPlanner
xcodegen generate
open TriPlanner.xcodeproj
```

Run the backend somewhere the phone can reach (for the simulator,
`uvicorn app.main:app --port 8000` and a backend URL of
`http://127.0.0.1:8000` works).
