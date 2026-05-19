# HeartKaroo

A single Hammerhead Karoo extension that combines two earlier extensions:

| Data field | ID | Description |
|---|---|---|
| Pw:Hr Decoupling | `decoupling` | Real-time aerobic decoupling (%) computed over a rolling 30-minute window using the Friel method. |
| HRV (RMSSD) | `hrv_rmssd` | Root-mean-square of successive RR-interval differences, in milliseconds. Calculated over a 30-beat sliding window from a Polar H10 (or any standard BLE HRM) read directly over Bluetooth. |

When the HRV stream is active the extension also writes RMSSD into the
recorded `.fit` file as a developer field (`hrv_rmssd`, units `ms`),
alongside the standard heart-rate record.

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
│   ├── DecouplingCalculator.kt     Pure-Kotlin rolling-window math
│   └── DecouplingDataType.kt       Subscribes to power + HR streams
└── hrv/
    ├── HRVCalculator.kt            RMSSD over a 30-beat sliding window
    ├── HRVDataType.kt              Streams RMSSD to Karoo
    ├── HeartRateMeasurement.kt     Pure parser for BLE 0x2A37 payloads
    └── PolarBleManager.kt          BLE scan / GATT lifecycle
```

## Permissions

| Permission | Reason |
|---|---|
| `BLUETOOTH_SCAN` | Scan for the Polar H10 |
| `BLUETOOTH_CONNECT` | Connect via GATT |
| `BLUETOOTH` / `ACCESS_FINE_LOCATION` | Required on Android < 12 for BLE scanning |
