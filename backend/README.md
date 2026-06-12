# TriPlanner backend

FastAPI service that plans triathlon training toward your next race and
adapts it daily using wellness data and perceived exertion from
[intervals.icu](https://intervals.icu).

## How it works

1. **Readiness** (`app/readiness.py`) — blends today's HRV (vs 30-day
   baseline), resting HR, sleep, and subjective fatigue/soreness/stress with
   *how training has been perceived* over the last 7 days (session RPE and
   the 1–5 post-workout "feel" rating from intervals.icu). Output: a 0–100
   score and an adjustment level (`as_planned` / `reduced` / `easy` / `rest`).
2. **Planner** (`app/planner.py`) — periodizes the weeks from today to race
   day (base → build → peak → taper, recovery every 4th week), scales weekly
   hours to race distance (sprint/olympic/half/full), and lays out
   swim/bike/run/brick sessions. The template is fitted to the athlete:
   weekly volume starts near their current training load (from CTL) and
   ramps ≤8%/week until the template catches up, and the swim/bike/run time
   split shifts up to ±12% toward their weakest discipline (thresholds from
   intervals.icu sport settings vs. mid-pack references; bike uses W/kg).
   Today's readiness then adjusts the next ~3 days: lower volume, intensity
   swapped to aerobic work, or full rest.
3. **Garmin sync** (`app/workouts.py`) — `POST /sync` pushes the next two
   weeks to the intervals.icu calendar as structured workouts (warm-up /
   repeats with zone targets / cool-down in intervals.icu step syntax).
   With the athlete's Garmin connected to intervals.icu, each workout lands
   on the watch on its day and can be followed step-by-step. Bricks become
   two back-to-back events (bike, then run). Re-syncing replaces previously
   synced TriPlanner workouts, so readiness adjustments propagate.
4. **Forecast** (`app/forecast.py`) — `GET /forecast` simulates CTL/ATL over
   the *planned* load to race day and maps fitness gained to performance:
   projected FTP (~0.35% per CTL point, scaled by how well training is being
   absorbed per today's readiness, plus a taper-freshness bonus), with run
   threshold pace improving at ~60% and swim pace at ~40% of the relative
   FTP gain. Current FTP/paces come from intervals.icu sport settings.
5. **API** (`app/main.py`) — the iPhone app sends the athlete's intervals.icu
   credentials in headers (`X-Athlete-Id`, `X-Api-Key`); the backend stores
   only the race list (SQLite).

## Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/readiness` | Today's readiness score with per-component breakdown |
| GET | `/plan` | Full plan to the next race, readiness-adjusted |
| GET | `/forecast` | Projected FTP and run/swim pacing at race day |
| POST | `/sync` | Push the next two weeks to intervals.icu → Garmin |
| GET/POST | `/races` | List / add races |
| DELETE | `/races/{id}` | Remove a race |

## Run

```bash
cd backend
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## Test

```bash
cd backend
python -m pytest tests
```

intervals.icu credentials: intervals.icu → Settings → Developer Settings
(athlete ID looks like `i12345`; the key is used as the password of HTTP
Basic auth with username `API_KEY`).
