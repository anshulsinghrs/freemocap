# 01 — Repository audit

**Date:** 2026-11-05 · **Method:** each repository was shallow-cloned (default branch HEAD) and its
README, build files, licence files and key source files were read directly. Statements below
reflect what is in the code at the audited commit; where something comes from outside knowledge
rather than the source, it is marked *(external knowledge — verify)*.

| # | Repository | Audited commit date | Accessible |
|---|---|---|---|
| 1 | JayeshBainwad/FlexiPose | 2025-09-07 | yes |
| 2 | google-ai-edge/mediapipe-samples | 2026-10-30 | yes |
| 3 | vmalikov/pose-detection-android | 2026-02-23 | yes |
| 4 | deepanshumody/physiocoach | 2026-06-12 | yes |
| 5 | arpitag0207/Physical-Rehabilitation-Assistant | 2026-04-15 | yes |
| 6 | Sujata005/PhysioAI | 2026-04-10 | yes |
| 7 | Unity-Technologies/arfoundation-samples | 2026-04-10 | yes |
| 8 | dilmerv/UnityARFoundationEssentials | 2023-04-25 | yes |

All eight were accessible; nothing below is inferred from a repository name alone.

---

## Licence summary (decides what can be reused at all)

| Repo | Licence found | Can we copy code? |
|---|---|---|
| FlexiPose | Apache-2.0 (`LICENSE.txt`) + `NOTICE.txt` | Yes, with notice. Its `PoseLandmarkerHelper.kt` is itself adapted from mediapipe-samples. |
| mediapipe-samples | Apache-2.0 | Yes, keep header + state changes. **Used.** |
| pose-detection-android | **No licence file.** README: "provided as-is for evaluation and open-source use" | **No.** Not an OSI licence grant; default copyright applies. Used as a *design reference only*; no code copied. |
| physiocoach | Apache-2.0 + `NOTICE` (built on NVIDIA Live VLM WebUI, Apache-2.0) | Yes, with both notices. Nothing copied (Python, see below). |
| Physical-Rehabilitation-Assistant | **No licence file** | **No.** Reference for ideas only. |
| PhysioAI | MIT | Yes. Nothing worth copying (see below). |
| arfoundation-samples | Unity Companion License (Unity-dependent projects only) | Yes, only inside a Unity project. Candidate for Phase 5. |
| UnityARFoundationEssentials | MIT | Yes, but code targets a 2019 API (see below). |

---

## 1. FlexiPose

| | |
|---|---|
| **Language** | Kotlin (38 files) |
| **UI** | **XML Views + Activities, not Jetpack Compose** (the brief assumed Compose). RecyclerView, navigation drawer. |
| **Architecture** | Described as MVVM; in practice exercise logic lives inside custom `View` classes. |
| **Key files** | `exercises/{Knee,Elbow,Shoulder}Exercise.kt`, `PoseLandmarkerHelper.kt`, `firebase/FirestoreClass.kt` (337 lines), `model/{Patient,Doctor,Exercise}.kt`, `activities/doctor/WeeklyReportActivity.kt`, `utils/AngleUtils.kt` |
| **Dependencies** | `com.google.mediapipe:tasks-vision:0.20230731` (2023, outdated), CameraX, Firebase BoM 33.4 (Auth, Firestore, Realtime DB, Storage), Glide, CircleImageView. compileSdk 34, minSdk 26. Package still `com.google.mediapipe.examples.poselandmarker`. |
| **Advantages** | Only audited repo with a real **patient/doctor split**: doctor searches patients, keeps a patient list, views per-patient exercise history and weekly reports. Firestore data model (`patientUsers/{id}/exercise/...`) is a usable starting point. No `google-services.json` committed. |
| **Limitations** | `KneeExercise extends View` and in one class: draws the skeleton, counts reps (boolean `isFlexing`), holds a `DatabaseReference`, writes to Firebase. `maxReps = 3` hardcoded. Angle computed with `atan2` on **normalised** coordinates (distorted for non-square images). No exercise state machine, no confidence gating, no tests beyond the template. |
| **Security/privacy** | Firestore security rules are not in the repo, so access control cannot be assessed. Profile images in Firebase Storage. |
| **Integration difficulty** | Medium–high: UI paradigm differs (Views vs Compose) and logic is entangled with UI and Firebase. |
| **Decision** | **Reuse the domain model and doctor/patient workflow as a reference for Phase 6. Rewrite all code.** Discard the exercise Views. |

## 2. google-ai-edge/mediapipe-samples (pose_landmarker/android)

| | |
|---|---|
| **Language** | Kotlin, XML Views/Fragments |
| **Key files** | `PoseLandmarkerHelper.kt` (live stream / video / image), `OverlayView.kt`, `fragment/CameraFragment.kt`, `download_tasks.gradle` |
| **Dependencies** | `tasks-vision:1.0.0` (used by 9 of 10 Android samples in the repo), CameraX 1.4.2. Toolchain is old: AGP 7.4.2, Kotlin 1.8.0. Uses `de.undercouch.download` plugin to fetch `pose_landmarker_{lite,full,heavy}.task`. |
| **Advantages** | Official, maintained reference for the Pose Landmarker API: 33 image landmarks, 33 world landmarks (metres, hip-centred), per-landmark visibility/presence, LIVE_STREAM mode with async callback, CPU/GPU delegate. |
| **Limitations found in the code** | (a) `bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)` ignores row stride; breaks on devices with padded RGBA buffers. (b) For the front camera the bitmap is **mirrored before inference**. A mirrored body swaps the model's anatomical left/right labels, so a side-specific exercise could analyse the wrong limb. (c) Sample-grade error handling. |
| **Integration difficulty** | Low. |
| **Decision** | **Reuse `PoseLandmarkerHelper` (adapted, Apache header kept, changes listed) and the model URLs. Rewrite the overlay in Compose. Fix (a) and (b).** |

## 3. vmalikov/pose-detection-android

| | |
|---|---|
| **Language** | Kotlin, Jetpack Compose, Hilt |
| **Architecture** | Best-structured repo audited: `domain/` (ports `PoseDetector`, `FrameSource`; `PoseProcessor`; `ExerciseStateMachine`; `Validator`s), `data/` (LiteRT MoveNet, CameraX `Flow<VideoFrame>`, device-capability probe), `ui/`. Unit tests for angles, processor, state machine and each validator. |
| **Dependencies** | MoveNet SinglePose Lightning (17 COCO keypoints) on LiteRT 1.4.1 (+GPU), CameraX 1.4.1, Compose BoM 2024.12.01, AGP 9.0.1, Kotlin 2.0.21. A `.tflite` is committed. |
| **Exercise logic** | Squat only. Phases START→DESCENDING→BOTTOM→ASCENDING with frame-count debounce. Validators: depth, torso angle, knee-over-toe. "Fitness" vs "rehab" configs. |
| **Limitations** | **No licence.** MoveNet has 17 keypoints (no heel/foot-index, needed for heel raises) and no 3D world coordinates. Debounce in frames, not time (behaves differently at 15 vs 30 fps). Validators are stateless per frame, so repetitions are not classified as a whole. |
| **Integration difficulty** | n/a for code (licence). |
| **Decision** | **Design reference only.** PhysioAR's engine adopts the same separation (estimator port → features → state machine → validation, all JVM-testable) but is written from scratch, uses time-based thresholds, and classifies whole repetitions. **MoveNet is not adopted** (see ADR-002). |

## 4. deepanshumody/physiocoach

| | |
|---|---|
| **Language** | Python 3.10+ (aiohttp/aiortc WebRTC server, browser UI) |
| **Key files** | `pose_detector.py` (MediaPipe → angles → `AngleRepCounter`, ROM), `exercise_library.py` (15 exercise definitions with joint triplets, rep thresholds, ROM targets, VLM prompts), `session_manager.py` (SQLite), `vlm_service.py` |
| **Dependencies** | MediaPipe, OpenCV, aiortc, openai client, `nvidia-ml-py`. Form coaching uses a **local Qwen2.5-VL-7B** (~800 ms/frame, needs a workstation GPU). |
| **Advantages** | Covers 5 of our 6 exercises (squat, calf raise, seated knee extension, standing hip abduction, wall push-up, shoulder raise). Exercise definitions list phases and common mistakes; useful as a **checklist** for our definitions. Clear NOTICE/attribution practice. |
| **Limitations** | Rep counting is a two-threshold crossing on one angle for every exercise (the "generic validator" the brief forbids). Some joint choices are not valid measures: e.g. **calf raise counted on hip–knee–ankle angle**, which does not measure ankle plantarflexion. Visibility threshold 0.3. VLM coaching cannot run on a phone. Tests are mostly inherited server/GPU-monitor tests. |
| **Integration difficulty** | High for code (Python server, GPU). Low for ideas. |
| **Decision** | **Reuse ideas (exercise checklists, ROM reporting, voice cues). Discard code and the VLM path.** Possible future use: offline Python experimentation only. |

## 5. arpitag0207/Physical-Rehabilitation-Assistant

| | |
|---|---|
| **Language** | Python, one 738-line script (`post_surgery_rehab.py`) |
| **Content** | 5 upper-body exercises (incl. shoulder flexion), class per exercise with `_target_angle`/`_start_angle`, peak-angle log per rep, threaded `pyttsx3`/Windows SAPI voice queue, matplotlib report. Bundles `pose_landmarker_lite.task`. |
| **Limitations** | **No licence.** Docstring states "no state machine — just two thresholds and a boolean flag". Windows-specific TTS. |
| **Decision** | **Discard code.** Two ideas kept: log the *peak angle per repetition*, and a non-blocking voice queue that drops cues instead of backlogging (PhysioAR's `FeedbackThrottle` + `SpeechFeedback`). |

## 6. Sujata005/PhysioAI

| | |
|---|---|
| **Language** | Python, Flask, OpenCV, MediaPipe |
| **Content** | `app.py` (385 lines, much of it commented-out earlier versions). Three modes (arms/legs/shoulders), each a fixed threshold pair (e.g. `angle < 30` / `> 160`), green/red colouring. Video processed server-side and streamed back as MJPEG. `test_app.py` is 10 lines. |
| **Security/privacy** | **A Windows virtualenv is committed, including 12 `.exe` binaries.** Do not run anything from it. README suggests AWS EC2 deployment, which would send patient camera video to a server. |
| **Decision** | **Discard.** Server-side video processing conflicts with PhysioAR's on-device privacy requirement. MIT licence would allow reuse, but nothing exceeds what the other repos provide. |

## 7. Unity-Technologies/arfoundation-samples

| | |
|---|---|
| **Language** | C#, Unity |
| **Version** | Unity **6000.5.0b2 (a beta editor)**, AR Foundation 6.5.0, ARCore XR Plug-in 6.5.0, ARKit 6.5.0, plus Meta/Android XR OpenXR packages. |
| **Relevant samples** | SimpleAR / PlaneDetection / Anchors (placing a demonstration model on the floor), Occlusion (`SimpleOcclusion`, `DepthImages`), BodyTracking (`HumanBodyTracking2D/3D`, `HumanBodyTracker.cs`, `BoneController.cs`). |
| **Key finding** | Body tracking and human-segmentation occlusion are gated at runtime on `descriptor.supportsHumanBody2D/3D` and `humanSegmentation*Supported` (`RequiresBodyTracking.cs`, `RequiresOcclusion.cs`). *(External knowledge — verify on device):* the ARCore plug-in does not implement human body tracking or human segmentation; ARCore provides environment depth occlusion only. **So AR Foundation cannot replace MediaPipe for body tracking on Android**, which confirms the brief's warning. |
| **Decision** | **Use as the Phase 5 base** for plane detection + anchored 3D demonstration avatar, on a *stable* Unity 6 LTS rather than the beta the samples pin. Discard body-tracking scenes. |

## 8. dilmerv/UnityARFoundationEssentials

| | |
|---|---|
| **Version** | Unity 2019.2.2f1, AR Foundation **3.0.0-preview.3** (≈2019) |
| **Content** | Placement, drag/drop, selection, measurement, image tracking, face tracking, people occlusion, and body-tracking scenes (cube skeleton, particles). |
| **Limitations** | Body tracking / people occlusion scenes rely on ARKit features. Old API (`ARSessionOrigin`, `ARReferencePoint`) that has since been replaced (`XROrigin`, `ARAnchor`) *(external knowledge — verify when porting)*. |
| **Decision** | **Concepts only** (selection/drag interactions, skeleton-from-primitives visual idea). Do not import the project. |

---

## Overall: reuse / rewrite / discard

| Component | Source | Decision |
|---|---|---|
| Pose Landmarker wrapper | mediapipe-samples | **Reuse (adapted)** → `app/.../pose/PoseLandmarkerHelper.kt` |
| Model download URLs | mediapipe-samples | **Reuse** (re-implemented as a Gradle task without the extra plugin) |
| Skeleton overlay | mediapipe-samples / FlexiPose | **Rewrite** in Compose with correct FILL_CENTER mapping and front-camera mirroring at draw time |
| Clean-architecture split, testable domain | pose-detection-android | **Re-implement the pattern** (no code; no licence) |
| Squat state machine / validators | pose-detection-android | **Discard** (no licence; squat not in our list) |
| MoveNet / LiteRT | pose-detection-android | **Discard** (one pose engine only; see ADR-002) |
| Exercise checklists (phases, mistakes, ROM targets) | physiocoach, Physical-Rehab-Assistant | **Reuse as reference** when writing each exercise definition |
| Generic threshold rep counters | physiocoach, Rehab-Assistant, PhysioAI | **Discard** |
| VLM coaching | physiocoach | **Discard** (not on-device) |
| Patient/doctor model, weekly report | FlexiPose | **Reference for Phase 6**, rewrite |
| Firebase integration | FlexiPose | **Defer**; backend choice in Phase 6 |
| Flask server-side video | PhysioAI | **Discard** (privacy) |
| AR placement/anchors/occlusion | arfoundation-samples | **Reuse in Phase 5** (Unity project) |
| AR body tracking | arfoundation-samples, Essentials | **Discard** (not available on ARCore) |

## Security and privacy issues found

1. PhysioAI commits Windows executables (`mediapipe-env/Scripts/*.exe`).
2. PhysioAI's architecture streams raw patient video to a server.
3. FlexiPose's Firestore security rules are absent from the repo; access control is unknown.
4. Two repositories have no licence, so their code cannot legally be reused.
5. physiocoach requires a local LLM server and exposes an HTTP/WebRTC server; not suitable for a patient phone.
