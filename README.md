# HeartKaroo

A single Hammerhead Karoo extension that bundles a suite of physiology
metrics. The power, pace and decoupling fields are computed on-device from
Karoo's own power/HR/speed streams; the HRV fields come from a Polar H10
(or another modern Polar HR strap) over Bluetooth via Polar's official BLE
SDK. No cloud, no account, no network access — the app requests no
`INTERNET` permission.

## Live data fields

### Pace / Power vs Heart Rate

| Data field | ID | Description |
|---|---|---|
| Pw:Hr Decoupling | `decoupling` | Real-time aerobic decoupling (%) over a rolling 30-min window (Friel method). |
| Pa:Hr Decoupling | `pace_hr_decoupling` | Same method but speed-vs-HR — works for riders without a power meter, and for runners. |
| Efficiency Factor | `efficiency_factor` | Coggan NP / avg HR over the last 30 min. Higher is more aerobically efficient. |
| Cardiac cost | `cardiac_cost` | HR/W average over the last 30 min — inverse of EF, more intuitive scale. |
| W′ balance | `w_prime_balance` | Skiba 2012 "matches left" anaerobic capacity in joules. Defaults: CP 250 W, W′ 20 000 J (override in Rider Settings). |
| W′ % | `w_prime_pct` | Same Skiba balance as a 0–100 % fuel gauge, colour-coded — readable at a glance without knowing your W′ in joules. |
| Cardiac pop @ min | `cardiac_pop_minute` | Latches the minute at which decoupling first sustains above 5%. |
| AeT estimate | `aet_estimate` | Live aerobic-threshold power (watts) — fits DFA α1 vs power and solves for α1 = 0.75. Per-ride finals are persisted; the rolling mean shows on the Readiness screen. |

### Power analytics

| Data field | ID | Description |
|---|---|---|
| Variability Index | `variability_index` | NP / AP. Spiky ride indicator. |
| Intensity Factor | `intensity_factor` | NP / FTP. Default FTP 270 W (override in Rider Settings). |
| Training Stress Score | `tss` | Live cumulative TSS. |
| Kilojoules | `kilojoules` | Cumulative mechanical work. |
| Coasting % | `coasting_pct` | % of ride time at &lt;5 W. |
| Quadrant | `quadrant` | Coggan quadrant analysis (1-4) from power+cadence over the last 60 s. |
| Best 5 s / 1 min / 5 min / 20 min / 60 min power | `mmp_5s` / `mmp_1min` / `mmp_5min` / `mmp_20min` / `mmp_60min` | Highest mean power for that duration seen so far in the ride. |
| Power W/kg | `watts_per_kg` | Power-to-weight ratio — 3-second smoothed power over rider weight (default 75 kg, override in Rider Settings). |
| eFTP | `eftp` | Live FTP estimate — 95 % of the best 20-min power so far this ride. |
| VAM | `vam` | Vertical Ascent Meters per hour over the last 60 s of elevation gain. |
| Optimal cadence | `optimal_cadence` | Within-ride best-efficiency cadence, reported in RPM — bins power+HR by cadence and reports the centre of the bucket with the highest watts-per-beat. Persisted per-ride; rolling mean on the Readiness screen. |

The power/pace analytics use Karoo's native power, speed, cadence and
elevation streams (from whatever sensors are paired) plus heart rate, so
most of them work without the Polar strap. The HRV fields below require it.

### HRV (from the Polar strap)

| Data field | ID | Description |
|---|---|---|
| HRV (RMSSD) | `hrv_rmssd` | RMSSD over a sliding 30-beat window, in milliseconds. |
| HRV Stress % | `hrv_stress` | Current RMSSD vs in-ride 20-min EMA baseline, clamped 0–100 %. Reads `--` for the first ~5 min while the baseline settles. |
| DFA α1 | `dfa_alpha1` | Detrended Fluctuation Analysis short-term scaling exponent. α1 ≈ 0.75 is a widely-used non-invasive aerobic-threshold marker (Rogell et al., 2021). Needs ~2 min of RR data; withheld when the recent artifact rate is too high to trust. |
| HRV (SDNN) | `hrv_sdnn` | Standard deviation of RR intervals over a 60-beat window. |
| HRV pNN50 | `hrv_pnn50` | % of successive RR-interval differences > 50 ms — classic vagal marker. |
| Poincaré SD1 / SD2 / SD1-SD2 ratio | `hrv_sd1` / `hrv_sd2` / `hrv_sd1_sd2_ratio` | Short- vs long-term variability from the Poincaré plot. |
| Resp rate | `respiratory_rate` | Breaths/min derived from respiratory sinus arrhythmia in the RR stream — no chest strap or mask needed. |
| Irregular beats/min | `ectopic_rate` | Rough count of RR intervals deviating >20 % from the previous accepted interval. NOT a clinical diagnostic. |

The variability metrics (RMSSD/stress, SDNN, pNN50, Poincaré, DFA α1) are
fed only the RR intervals that survive artifact rejection, and are paused
while the strap reports poor skin contact. The ectopic count and
respiratory rate use the raw RR stream (the former counts artifacts; the
latter is timing-sensitive).

### FIT developer fields

Recorded alongside the standard HR record when the strap is active:

| FIT field | Units |
|---|---|
| `hrv_rmssd` | ms |
| `hrv_stress_pct` | pct |
| `Alpha1` | (dimensionless) |
| `respiratory_rate` | brpm |
| `sdnn` | ms |
| `aet_estimate` | watts |

`respiratory_rate` is also tagged with the native FIT record respiration
field number (108), so apps that understand it (intervals.icu, Garmin
Connect) read it as real respiration instead of an opaque custom stream.

The DFA α1 field is named `Alpha1` (the alphaHRV Connect IQ convention)
because that is the exact name intervals.icu looks for — it then computes
"Average DFA a1" from the stream itself.

The rest have no native FIT equivalent and appear as named developer
fields (intervals.icu surfaces them under Custom Streams).

**Per-ride summary (session message).** Written periodically to the ride's
session message (latest value wins), so they show up as after-ride numbers
(custom activity fields in intervals.icu) rather than 1 Hz streams:

| FIT field | Units | Meaning |
|---|---|---|
| `aet_estimate` | watts | Final aerobic-threshold estimate (DFA α1 = 0.75). |
| `vt2_estimate` | watts | Second-threshold estimate from the same fit (DFA α1 = 0.50). |
| `optimal_cadence` | rpm | Best-efficiency cadence (highest W per beat). |
| `w_prime_min` | J | Lowest W′ balance reached — depth into anaerobic reserve. |
| `matches_burned` | — | Count of fresh dips below 25% W′ (re-armed above 30%). Written only alongside `w_prime_min`. |
| `dfa_a1_aerobic_s` | s | Time with DFA α1 ≥ 0.75 (below LT1). |
| `dfa_a1_threshold_s` | s | Time with 0.50 ≤ DFA α1 &lt; 0.75 (between LT1 and LT2). |
| `dfa_a1_hard_s` | s | Time with DFA α1 &lt; 0.50 (above LT2). |
| `cardiac_pop_min` | min | Minute at which Pw:Hr decoupling first sustained above 5%. |
| `quadrant1_pct` … `quadrant4_pct` | pct | Share of ride time in each Coggan quadrant. |

These need power/HR/cadence and (for the DFA/threshold fields) an active
strap to populate; fields without enough data are simply absent. The DFA α1
zone seconds are always written (0 is a valid "no time there").

The other secondary HRV metrics (pNN50, SD1/SD2, ectopic rate) are
derivable post-ride from the recorded RR data, so they stay as
live-display-only.

## In-app screens

- **Intro** (`MainActivity`) — what each data field means and how to add
  it to a profile.
- **HRV Readiness** (`ReadinessActivity`) — a 2-minute pre-ride resting
  RMSSD measurement, compared against a rolling 7-day baseline (lnRMSSD,
  z-score) and surfaced as a "go hard / go easy / normal" verdict (needs at
  least 3 days of history before it gives a verdict). The baseline lives in
  `SharedPreferences` and updates each time you measure. The screen also
  shows the rolling AeT estimate and rolling optimal cadence accumulated
  from previous rides.
- **Rider Settings** (`SettingsActivity`) — FTP, critical power, W′, HR
  max, and weight. These feed Intensity Factor / TSS (FTP), W′ balance (CP
  and W′), and any future power-to-weight fields. Range-validated on save,
  with a "Reset to defaults" button.

## How it works

**Decoupling and the power metrics.** A single `RidePowerEngine` owns every
per-ride power/HR/cadence/climb calculator and feeds them from one
long-lived set of Karoo stream collectors, so the metrics accumulate for
the whole ride regardless of which page is on screen. Each data field just
mirrors the matching `StateFlow` (null → Searching, value → Streaming).

For decoupling, the engine takes the most recent 30 min of riding, splits
the window in half, and compares the mean power-to-heart-rate ratio of each
half:

```
decoupling % = (firstHalfRatio - secondHalfRatio) / firstHalfRatio * 100
```

A positive value means HR has drifted up relative to power (cardiac
drift). Under ~5 % is generally considered well-coupled aerobic effort.
The field reads `--` for the first 10 min while the window fills.

**HRV.** The extension talks to the strap through Polar's official BLE SDK
(`com.polar.sdk`), the same channel Polar's own app uses, rather than the
raw Heart Rate Service:

1. Scans for nearby Polar straps (`searchForDevice`) and exposes each as a
   Karoo sensor named `<strap> (HR+HRV)`.
2. On pairing, connects directly to the strap by its BT MAC address and
   subscribes to HR, battery and device-info features, with automatic
   reconnection enabled.
3. RR intervals arrive at the strap's native precision (finer than the
   standard `0x2A37` characteristic, which truncates to 1/1024 s and can
   drop intermediate beats). They are run through artifact rejection and
   fed into the RMSSD / SDNN / pNN50 / Poincaré / DFA α1 calculators; the
   raw stream drives respiratory rate and the ectopic count.
4. HR is published as the standard `HEART_RATE` data point so it populates
   the real FIT HR field, while the HRV metrics are exposed both as live
   data fields and (a subset) as FIT developer fields.

The BLE link is owned by a process-wide singleton (`PolarBleManager`)
shared by the extension service and the Readiness screen, so the strap
stays connected even as Karoo tears down and recreates the service at ride
start. The strap allows only one BLE link at a time, so pair the
`(HR+HRV)` entry only — it provides both HR and HRV from the single
connection. The extension also raises a one-shot in-ride alert when the
strap battery drops to 15% (re-arming once it recovers above 25%).

No Polar account, no cloud, no network access.

## Build

### Prerequisites

- Android Studio or the Android command-line tools
- JDK 21. The app's Java source/target compatibility is 17, but the
  Robolectric tests run against the Android 16 (SDK 36) runtime, which
  requires Java 21 — CI uses 21 for both build and test.
- A GitHub Personal Access Token with `read:packages` scope (to download
  `io.hammerhead:karoo-ext` from GitHub Packages)

The Polar BLE SDK and RxJava are pulled from JitPack, which is already
configured in `settings.gradle.kts`; no extra credentials are needed for
them.

### Configure GitHub Packages credentials

Add to `~/.gradle/gradle.properties`:

```
gpr.user=YOUR_GITHUB_USERNAME
gpr.token=YOUR_GITHUB_TOKEN
```

Or set environment variables `GITHUB_ACTOR` and `GITHUB_TOKEN`.

### Build the APK

```bash
./gradlew assembleRelease
```

The APK lands at `app/build/outputs/apk/release/app-release-unsigned.apk`.

### Install on Karoo

```bash
adb install app/build/outputs/apk/release/app-release-unsigned.apk
```

## Run the tests

```bash
./gradlew test
```

Most of the suite is pure-Kotlin or runs on the JVM via
[Robolectric](https://robolectric.org/) (no device or emulator needed):

- **Algorithms** — decoupling (warmup gating, drift, window eviction,
  reset), HRV RMSSD / SDNN / pNN50 / Poincaré, DFA α1, the RR artifact
  corrector, the DFA α1 zone timer, respiratory rate, the ectopic detector,
  Normalized Power, kilojoules, coasting, MMP, quadrant analysis,
  efficiency factor, cardiac cost, VAM, W′ balance, the AeT calibrator, the
  optimal-cadence calculator, and the shared `PowerMetrics` ratio formulas.
- **Orchestration** — `RidePowerEngine` is driven through its stream
  handlers and asserted on the resulting `StateFlow`s (IF/VI/TSS, kJ,
  coasting, W′, MMP, EF, cardiac cost, decoupling, quadrant, optimal
  cadence, VAM, AeT) plus `resetRide`; the strap low-battery hysteresis
  (`StrapBatteryAlerter`), the ride-stop persistence gate
  (`shouldPersistRollingFinal`), the session-summary field assembly
  (`SessionSummaryFields`), and the Karoo stream bridge (`KarooStreamExt`).
- **Persistence (Robolectric)** — `ReadinessStore`, `AerobicThresholdStore`,
  `OptimalCadenceStore`, and `RiderSettings` exercise real
  `SharedPreferences`: record/read round-trips, the 7- and 90-day window
  eviction, input validation, defaults, and tolerance of malformed values.
- **Screens (Robolectric)** — `MainActivity`, `ReadinessActivity`, and
  `SettingsActivity` cover on-launch rendering and input handling.

`HeartRateMeasurementTest` still covers the standalone `0x2A37` payload
parser, but that parser is no longer on the live HRV path (the Polar SDK
replaced it). The 2-minute BLE measurement loop and the `KarooExtension`
service / `PolarBleManager` lifecycle still need a real device.

A single instrumented test (`ReadinessStoreInstrumentedTest`) runs on a
device/emulator via `./gradlew connectedDebugAndroidTest`.

### Coverage

```bash
./gradlew koverHtmlReportDebug   # HTML report under app/build/reports/kover/
./gradlew koverXmlReportDebug    # XML for CI tooling
./gradlew koverLogDebug          # one-line summary to the console
```

Coverage is measured with [Kover](https://github.com/Kotlin/kotlinx-kover)
over the `debug` unit tests and is uploaded as a CI artifact; it is
reported, not gated.

### Continuous integration

`.github/workflows/android-tests.yml` runs on every push and pull request:

- **JVM unit tests** — `testDebugUnitTest` plus the Kover reports on
  `ubuntu-latest` with Java 21, uploading the test and coverage reports.
- **Instrumented tests** — `connectedDebugAndroidTest` on an API 34
  emulator.

## Adding the data fields to a ride profile

1. On the Karoo: **Settings → Profiles → [your profile] → Data Fields**.
2. Tap a slot and search for **Pw:Hr Decoupling** or **HRV (RMSSD)**.
3. Save.

For HRV, also pair the strap under **Sensors → Add sensor** — the
extension shows up as a Bluetooth HR source named `<strap> (HR+HRV)`. Pair
that one (not the strap's plain native HR entry) so the single connection
serves both HR and HRV.

## Layout

```
app/src/main/kotlin/com/inqulab/heartkaroo/
├── HeartKarooExtension.kt          KarooExtension service: data types, FIT fields, BLE wiring
├── MainActivity.kt                 In-app intro screen
├── StrapBatteryAlerter.kt          Low-battery alert hysteresis + rolling-final persistence gate
├── karoo/
│   └── KarooStreamExt.kt           Karoo stream → Flow helpers
├── decoupling/
│   ├── DecouplingCalculator.kt     Rolling Pw:Hr / Pa:Hr math
│   ├── DecouplingDataType.kt       Pw:Hr decoupling field
│   ├── PaHrDecouplingDataType.kt   Pa:Hr decoupling field
│   ├── CardiacPopDetector.kt       Latching pop-time math
│   └── CardiacPopDataType.kt       Cardiac-pop minute field
├── efficiency/
│   ├── EfficiencyFactorCalculator.kt
│   ├── EfficiencyFactorDataType.kt
│   ├── CardiacCostCalculator.kt
│   └── CardiacCostDataType.kt
├── wprime/
│   ├── WPrimeBalanceCalculator.kt  Skiba 2012 model
│   └── WPrimeBalanceDataType.kt
├── aet/
│   ├── AerobicThresholdCalibrator.kt   α1-vs-power linear fit → AeT
│   ├── AerobicThresholdStore.kt        SharedPreferences rolling history
│   └── AerobicThresholdDataType.kt     Live AeT field
├── power/
│   ├── RidePowerEngine.kt              Single owner of every per-ride power/HR/cadence/climb metric
│   ├── NormalizedPowerCalculator.kt    NP + AP shared by VI/IF/TSS
│   ├── PowerMetrics.kt                 Pure VI/IF/TSS ratio formulas
│   ├── KilojoulesCalculator.kt
│   ├── CoastingCalculator.kt
│   ├── MmpCalculator.kt                Per-duration best mean power
│   ├── QuadrantAnalysisCalculator.kt   Force × CPV quadrants
│   └── *DataType.kt                    VI, IF, TSS, kJ, Coast %, MMP, Quadrant
├── climb/
│   ├── VamCalculator.kt
│   └── VamDataType.kt
├── cadence/
│   ├── OptimalCadenceCalculator.kt     Per-bin W/HR efficiency
│   ├── OptimalCadenceStore.kt          SharedPreferences rolling history
│   └── OptimalCadenceDataType.kt       Live optimal-cadence field
├── settings/
│   ├── RiderSettings.kt                FTP / CP / W′ / HRmax / weight prefs
│   └── SettingsActivity.kt             Edit-rider-settings screen
├── readiness/
│   ├── ReadinessStore.kt           SharedPreferences-backed baseline
│   └── ReadinessActivity.kt        2-min resting RMSSD UI
└── hrv/
    ├── PolarBleManager.kt          Polar BLE SDK scan / connect / HR+RR streaming (process singleton)
    ├── RrArtifactCorrector.kt      RR artifact rejection feeding the variability metrics
    ├── HRVCalculator.kt            RMSSD
    ├── HRVStressCalculator.kt      EMA-baseline stress %
    ├── DfaAlpha1Calculator.kt      Detrended Fluctuation Analysis
    ├── DfaAlphaZoneTimer.kt        Time-in-zone for the DFA α1 thresholds
    ├── SdnnCalculator.kt           Standard deviation of NN
    ├── Pnn50Calculator.kt          % successive diffs > 50 ms
    ├── PoincareCalculator.kt       SD1, SD2, SD1/SD2
    ├── RespiratoryRateCalculator.kt RSA-derived breaths/min
    ├── EctopicDetector.kt          Irregular-beat heuristic
    ├── HrvFlowDataType.kt          Shared DataType wrapper
    ├── HRVDataType.kt              RMSSD field
    ├── HRVStressDataType.kt        Stress % field
    ├── DfaAlpha1DataType.kt        DFA α1 field
    └── HeartRateMeasurement.kt     Legacy BLE 0x2A37 payload parser (kept + tested, not on the live path)
```

## Permissions

| Permission | Reason |
|---|---|
| `BLUETOOTH_SCAN` | Scan for the Polar strap (Android 12+) |
| `BLUETOOTH_CONNECT` | Connect via the Polar SDK (Android 12+) |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` / `ACCESS_FINE_LOCATION` | Required on Android ≤ 11 (`maxSdkVersion=30`) for BLE scanning |

The app declares `android.hardware.bluetooth_le` as a required feature and
requests no `INTERNET` permission.
