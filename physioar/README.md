# PhysioAR

AI-assisted physiotherapy exercise support for Android. The phone camera tracks the patient's body
(MediaPipe Pose Landmarker), an on-device exercise engine measures joint angles, follows the
movement phases, counts and classifies repetitions against the physiotherapist's prescription,
and gives spoken and on-screen cues.

> **Status: early prototype, not for clinical use.** Movement parameters are engineering
> placeholders and have **not** been reviewed by a physiotherapist. The app does not diagnose.

## Current status

| Area | State |
|---|---|
| Repository audit, architecture, decision log | Done: [`docs/`](docs) |
| Exercise engine (Kotlin/JVM) | Implemented. **51 unit tests pass**, all on synthetic data |
| Seated knee extension analyzer | Implemented and unit-tested; not yet validated on real recordings |
| Other 5 exercises | Definitions only (`AnalyzerStatus.PLANNED`); shown as "Coming soon" |
| Android app (camera, MediaPipe, skeleton overlay, live HUD, TTS, results) | Code written; **not yet compiled or run on a device** |
| AR module (Unity) | Not started: [`ar/README.md`](ar/README.md) |
| Backend / therapist dashboard | Not started (Phase 6) |

## Repository layout

```
physioar/
├── engine/   pure-Kotlin exercise intelligence engine (own Gradle build, no Android)
├── app/      Android app: Compose UI, CameraX, MediaPipe adapter, TTS
├── ar/       Unity AR module (planned)
└── docs/     01 audit · 02 architecture · 03 decisions · 04 knee-extension spec · 05 validation plan
```

## Build and test the engine (any machine with JDK 17+)

```bash
cd physioar/engine
./gradlew test
```

## Build the Android app

Requirements: Android Studio (Ladybug or newer) or the Android SDK with platform 35, JDK 17+.
Internet access is needed on the first build to download Gradle dependencies and the MediaPipe
`.task` models (fetched automatically into `app/src/main/assets/` by the `downloadPoseModels`
task).

```bash
cd physioar
./gradlew :app:assembleDebug      # or open physioar/ in Android Studio
./gradlew :app:installDebug
```

The app needs a physical device (camera). It requests only the CAMERA permission. Nothing is
uploaded.

### Using the prototype
1. Pick **Seated knee extension**. Enter the prescription (leg, target angle, hold, repetitions).
2. Put the phone upright 2–3 m away at knee–hip height, side-on to the exercised leg.
3. Sit still with the knee bent until the status changes from "Get in position" to "Ready".
4. Straighten, hold, lower slowly. Each repetition is classified: correct, incomplete,
   incorrect technique, compensation, or not scored (tracking).
5. **Finish** or **Stop – pain** ends the session and shows the results.

## Adding an exercise
See [`docs/02-architecture.md`](docs/02-architecture.md), "How an exercise is added". Each exercise gets its own
package, parameters, analyzer, and tests (ADR-006).

## Licences
Third-party code and models: see [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md). A licence
for PhysioAR's own code has not been chosen yet. Because this directory sits inside the FreeMoCap
repository, that repository's licence (**AGPL-3.0**) applies by default until PhysioAR is moved
or given its own licence. Decide this before distributing the app.
