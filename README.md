# HeartKaroo

A single Hammerhead Karoo extension that bundles a suite of physiology
metrics — every one of them computed on-device from existing Karoo
power/HR/speed streams plus a Polar H10 (or any standard BLE HRM).
No cloud, no Polar SDK, no network access.

## Live data fields

### Pace / Power vs Heart Rate

| Data field | ID | Description |
|---|---|---|
| Pw:Hr Decoupling | `decoupling` | Real-time aerobic decoupling (%) over a rolling 30-min window (Friel method). |
| Pa:Hr Decoupling | `pace_hr_decoupling` | Same method but speed-vs-HR — works for riders without a power meter, and for runners. |
| Efficiency Factor | `efficiency_factor` | Coggan NP / avg HR over the last 30 min. Higher is more aerobically efficient. |
| Cardiac cost | `cardiac_cost` | HR/W average over the last 30 min — inverse of EF, more intuitive scale. |
| W′ balance | `w_prime_balance` | Skiba 2012 "matches left" anaerobic capacity in joules. Defaults: CP 250 W, W′ 20 000 J. |
| Cardiac pop @ min | `cardiac_pop_minute` | Latches the minute at which decoupling first sustains above 5%. |
| AeT estimate | `aet_estimate` | Live aerobic-threshold power (watts) — fits DFA α1 vs power and solves for α1 = 0.75. Per-ride finals are persisted; the rolling mean shows on the Readiness screen. |

### Power analytics

| Data field | ID | Description |
|---|---|---|
| Variability Index | `variability_index` | NP / AP. Spiky ride indicator. |
| Intensity Factor | `intensity_factor` | NP / FTP. Default FTP 270 W. |
| Training Stress Score | `tss` | Live cumulative TSS. |
| Kilojoules | `kilojoules` | Cumulative mechanical work. |
| Coasting % | `coasting_pct` | % of ride time at &lt;5 W. |
| Quadrant | `quadrant` | Coggan quadrant analysis (1-4) from power+cadence. |
| Best 5 s / 1 min / 5 min / 20 min / 60 min power | `mmp_5s` / `mmp_1min` / `mmp_5min` / `mmp_20min` / `mmp_60min` | Highest mean power for that duration seen so far in the ride. |
| VAM | `vam` | Vertical Ascent Meters per hour over the last 60 s of elevation gain. |

### HRV (from the BLE strap)

| Data field | ID | Description |
|---|---|---|
| HRV (RMSSD) | `hrv_rmssd` | RMSSD over a sliding 30-beat window, in milliseconds. |
| HRV Stress % | `hrv_stress` | Current RMSSD vs in-ride 20-min EMA baseline, clamped 0–100 %. |
| DFA α1 | `dfa_alpha1` | Detrended Fluctuation Analysis short-term scaling exponent. α1 ≈ 0.75 is a widely-used non-invasive aerobic-threshold marker (Rogell et al., 2021). |
| HRV (SDNN) | `hrv_sdnn` | Standard deviation of RR intervals over a 60-beat window. |
| HRV pNN50 | `hrv_pnn50` | % of successive RR-interval differences > 50 ms — classic vagal marker. |
| Poincaré SD1 / SD2 / SD1-SD2 ratio | `hrv_sd1` / `hrv_sd2` / `hrv_sd1_sd2_ratio` | Short- vs long-term variability from the Poincaré plot. |
| Resp rate | `respiratory_rate` | Breaths/min derived from respiratory sinus arrhythmia in the RR stream — no chest strap or mask needed. |
| Irregular beats/min | `ectopic_rate` | Rough count of RR intervals deviating >20 % from the previous accepted interval. NOT a clinical diagnostic. |

### FIT developer fields

Recorded alongside the standard HR record when the strap is active:

| FIT field | Units |
|---|---|
| `hrv_rmssd` | ms |
| `hrv_stress_pct` | pct |
| `dfa_alpha1` | (dimensionless) |
| `respiratory_rate` | brpm |
| `sdnn` | ms |
| `aet_estimate` | watts |

The other secondary HRV metrics (pNN50, SD1/SD2, ectopic rate) are
derivable post-ride from the recorded RR data, so they stay as
live-display-only.

## In-app screens

- **Intro** — what each data field means and how to add it to a profile.
- **HRV Readiness** — a 2-minute pre-ride resting RMSSD measurement,
  compared against a rolling 7-day baseline (lnRMSSD, z-score) and
  surfaced as a "go hard / go easy / normal" verdict. Baseline lives in
  `SharedPreferences` and updates each time you measure. Also displays
  the rolling AeT estimate accumulated from previous rides.
- **Rider Settings** — FTP, critical power, W′, HR max, and weight.
  These feed Intensity Factor / TSS (FTP), W′ balance (CP and W′), and
  any future power-to-weight fields. Range-validated on save, with a
  "Reset to defaults" button.

## How it works

**Decoupling.** Subscribes to Karoo's existing power and HR streams.
For the most recent 30 min of riding, the field splits the window in half
and compares the mean power-to-heart-rate ratio of each half:

```
decoupling % = (firstHalfRatio - secondHalfRatio) / firstHalfRatio * 100
```

A positive value means HR has drifted up relative to power (cardiac
drift). Under ~5 % is generally considered well-coupled aerobic effort.
The field reads `--` for the first 10 min while the window fills.

**HRV.** The extension acts as a Karoo sensor source:

1. Scans for any device advertising the standard Heart Rate Service
   (UUID `0x180D`).
2. On finding the Polar H10 (or any compatible HRM), connects via BLE
   GATT and subscribes to the Heart Rate Measurement characteristic
   (`0x2A37`).
3. RR intervals embedded in each notification are parsed (1/1024 s units
   per the BLE spec) and fed into the RMSSD calculator.
4. Results push to the Karoo display once at least two intervals have
   been collected.
5. HR is published as the standard `HEART_RATE` data point so it
   populates the real FIT HR field, while RMSSD is exposed both as a
   live data field and as a FIT developer field.

No Polar SDK, no cloud account, no network access.

## Build

### Prerequisites

- Android Studio or the Android command-line tools
- JDK 17
- A GitHub Personal Access Token with `read:packages` scope (to download
  `io.hammerhead:karoo-ext` from GitHub Packages)

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

Unit tests cover the pure-Kotlin pieces:

- `DecouplingCalculatorTest` — warmup gating, stable/drifting input,
  invalid-sample filtering, window eviction, reset.
- `HRVCalculatorTest` — RMSSD formula, the 300–2000 ms physiological
  filter, sliding window.
- `HeartRateMeasurementTest` — BLE Heart Rate Measurement parsing for
  8/16-bit HR and zero/one/many RR intervals.

## Adding the data fields to a ride profile

1. On the Karoo: **Settings → Profiles → [your profile] → Data Fields**.
2. Tap a slot and search for **Pw:Hr Decoupling** or **HRV (RMSSD)**.
3. Save.

For HRV, also pair the Polar H10 under **Sensors → Add sensor** — the
extension shows up as a Bluetooth HR source.

## Layout

```
app/src/main/kotlin/com/inqulab/heartkaroo/
├── HeartKarooExtension.kt          KarooExtension service entry point
├── MainActivity.kt                 In-app intro screen
├── decoupling/
│   ├── DecouplingCalculator.kt     Rolling Pw:Hr / Pa:Hr math
│   ├── DecouplingDataType.kt       Pw:Hr decoupling field
│   ├── PaHrDecouplingDataType.kt   Pa:Hr decoupling field
│   ├── CardiacPopDetector.kt       Latching pop-time math
│   └── CardiacPopDataType.kt       Cardiac-pop minute field
├── efficiency/
│   ├── EfficiencyFactorCalculator.kt
│   └── EfficiencyFactorDataType.kt
├── wprime/
│   ├── WPrimeBalanceCalculator.kt  Skiba 2012 model
│   └── WPrimeBalanceDataType.kt
├── aet/
│   ├── AerobicThresholdCalibrator.kt   α1-vs-power linear fit → AeT
│   ├── AerobicThresholdStore.kt        SharedPreferences rolling history
│   └── AerobicThresholdDataType.kt     Live AeT field
├── power/
│   ├── NormalizedPowerCalculator.kt    NP + AP shared by VI/IF/TSS
│   ├── KilojoulesCalculator.kt
│   ├── CoastingCalculator.kt
│   ├── MmpCalculator.kt                Per-duration best mean power
│   ├── QuadrantAnalysisCalculator.kt   Force × CPV quadrants
│   └── *DataType.kt                    VI, IF, TSS, kJ, Coast %, MMP, Quadrant
├── climb/
│   ├── VamCalculator.kt
│   └── VamDataType.kt
├── settings/
│   └── RiderSettings.kt                FTP / CP / W′ / HRmax / weight prefs
├── readiness/
│   ├── ReadinessStore.kt           SharedPreferences-backed baseline
│   └── ReadinessActivity.kt        2-min resting RMSSD UI
└── hrv/
    ├── HRVCalculator.kt            RMSSD
    ├── HRVStressCalculator.kt      EMA-baseline stress %
    ├── DfaAlpha1Calculator.kt      Detrended Fluctuation Analysis
    ├── SdnnCalculator.kt           Standard deviation of NN
    ├── Pnn50Calculator.kt          % successive diffs > 50 ms
    ├── PoincareCalculator.kt       SD1, SD2, SD1/SD2
    ├── RespiratoryRateCalculator.kt RSA-derived breaths/min
    ├── EctopicDetector.kt          Irregular-beat heuristic
    ├── HrvFlowDataType.kt          Shared DataType wrapper
    ├── HRVDataType.kt              RMSSD field
    ├── HRVStressDataType.kt        Stress % field
    ├── DfaAlpha1DataType.kt        DFA α1 field
    ├── HeartRateMeasurement.kt     BLE 0x2A37 payload parser
    └── PolarBleManager.kt          BLE scan / GATT lifecycle
```

## Permissions

| Permission | Reason |
|---|---|
| `BLUETOOTH_SCAN` | Scan for the Polar H10 |
| `BLUETOOTH_CONNECT` | Connect via GATT |
| `BLUETOOTH` / `ACCESS_FINE_LOCATION` | Required on Android < 12 for BLE scanning |
