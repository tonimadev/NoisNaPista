# FIDD — Ferramenta Inteligente de Detecção de Danos

**FIDD** ("Intelligent Damage Detection Tool") is an Android app that turns any
phone into a crowdsourced pothole detector. It watches the accelerometer while you drive,
tags every bump against the phone's GPS fix, and — once a threshold is crossed — logs it as a
detected pothole. Detections sync to a backend so they can be aggregated into a
community-wide map and a city ranking.

## Features

- **Background tracking** — a foreground service samples the accelerometer and GPS while the
  app is backgrounded, so detection keeps running while you drive with the screen off.
- **Impact detection** — a simple threshold-and-cooldown detector on the raw Z-axis
  acceleration, filtered against GPS speed so potholes aren't confused with a stationary phone
  being handled or a parked car.
- **Home dashboard** — live sensor reading, GPS/sensor status, current location, and a
  scrollable list of recently detected potholes.
- **Map** — every detected pothole plus community-reported ones from the backend, color-coded
  by severity.
- **History** — a full log of past detections.
- **City ranking** — aggregated pothole counts by city, sourced from the backend.
- **Debug/labeling screen** (debug builds only) — inspects the raw sensor window captured
  around each detection, for manually labeling data used to train the ML classifier under
  `ml/`.
- **Offline-first sync** — detections are written to a local Room database immediately and
  pushed to the backend by a `WorkManager` job, so a dropped connection never loses data.
- **Adaptive layout** — on wide screens (tablets, foldables) the Home screen and the full map
  render side by side instead of behind bottom navigation.

## Architecture

Multi-module Gradle project following a `core` / `feature` split, each feature split further
into `bridge` (navigation contract, no UI) and `impl` (screens + ViewModel):

```
app/                     App shell: navigation graph, theme, onboarding gate
core/
  model/                 Plain data classes shared across modules
  ui/                    Shared Compose theming
  location/              LocationProvider abstraction over FusedLocationProviderClient
  sensor/                Accelerometer sampling + PotholeDetector (the detection state machine)
  data/                  Room database, repositories, DataStore preferences, sync WorkManager job
  network/               Retrofit/OkHttp/Moshi backend client
feature/
  tracker/bridge, impl/  Home (tracking) screen, History, Debug/labeling screen
  onboarding/            First-run onboarding flow
  map/bridge, impl/      Full map screen
  ranking/bridge, impl/  City ranking screen
ml/                      Offline Python pipeline (pandas/scikit-learn) for training the
                         real-event classifier from labeled sensor windows
```

- **UI**: Jetpack Compose + Material 3, adaptive navigation (`NavigationSuiteScaffold`) and
  Navigation 3.
- **DI**: Hilt.
- **Async**: Kotlin Coroutines & Flow throughout; `PotholeDetector` is a process-wide singleton
  that is the single source of truth for whether tracking is active, so UI state survives
  process death/recreation while the foreground service keeps running.
- **Persistence**: Room (detections, sensor windows) + DataStore (onboarding flag).
- **Maps**: Google Maps Compose.
- **Networking**: Retrofit + Moshi against a companion Spring Boot backend
  (`NoisNaPistaBackend`, separate repository).

## Requirements

- Android Studio (current stable channel) with an Android SDK supporting API 37.
- JDK 11+ (configured via the Gradle toolchain).
- A physical device or emulator running Android 7.0 (API 24) or later.
- A Google Maps API key.

## Setup

1. Clone the repository.
2. Add your Google Maps API key. The project reads it via the
   [Secrets Gradle Plugin](https://github.com/google/secrets-gradle-plugin):
   ```bash
   echo "MAPS_API_KEY=your_real_key_here" > secrets.properties
   ```
   `secrets.properties` is gitignored; `local.defaults.properties` only holds a placeholder so
   the project still compiles without a key (maps just won't render tiles).
3. The app talks to the deployed backend at `https://fidd.com.br/` (see
   `core/network/.../NetworkModule.kt`). To develop against a local copy of the companion
   `NoisNaPistaBackend` Spring Boot project instead, point `BASE_URL` at
   `http://localhost:8080/` and expose it to the device:
   ```bash
   ./gradlew bootRun            # in the backend project
   adb reverse tcp:8080 tcp:8080  # works for the emulator and a USB-connected physical device
   ```
4. Open the project in Android Studio and run the `app` configuration, or from the command
   line:
   ```bash
   ./gradlew installDebug
   ```

## Building & testing

```bash
./gradlew assembleDebug     # build a debug APK
./gradlew testDebugUnitTest :core:model:test  # unit tests (JVM + Robolectric, incl. Compose UI tests)
./gradlew koverHtmlReportCoverage             # aggregated coverage -> build/reports/kover/htmlCoverage/
./gradlew koverVerifyCoverage                 # fails below 90% line / 75% branch coverage
./gradlew connectedAndroidTest  # instrumented tests (needs a connected device/emulator)
```

Shared fakes and test helpers live in `core/testing`. See `CLAUDE.md` for the testing conventions.

## Permissions

The app requests, at first use of tracking:

- **Location** (fine + coarse) — required; tracking cannot start without it.
- **Notifications** (Android 13+) — required to show the persistent "tracking active"
  notification for the foreground service.

## Machine learning pipeline (`ml/`)

Raw accelerometer windows captured around each detection are stored locally and can be
exported, labeled through the in-app Debug screen, and fed to `ml/analyze_and_train.py` to
train a scikit-learn classifier (`ml/model/real_event_classifier.joblib`) that distinguishes
real potholes from false positives (e.g. speed bumps, phone handling). This is an offline,
manual workflow — it is not currently wired into the app's live detection path.
