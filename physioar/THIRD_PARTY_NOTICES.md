# Third-party notices

## google-ai-edge/mediapipe-samples — Apache License 2.0
Copyright 2022–2023 The TensorFlow Authors. https://github.com/google-ai-edge/mediapipe-samples

- `app/src/main/java/com/physioar/app/pose/PoseLandmarkerHelper.kt` is adapted from
  `examples/pose_landmarker/android/app/src/main/java/com/google/mediapipe/examples/poselandmarker/PoseLandmarkerHelper.kt`.
  The original licence header is kept in the file, followed by a list of PhysioAR's changes.
- The model download URLs in `app/build.gradle.kts` come from that example's `download_tasks.gradle`.

Full licence text: http://www.apache.org/licenses/LICENSE-2.0

## MediaPipe Pose Landmarker models and `com.google.mediapipe:tasks-vision` — Apache License 2.0
Models are downloaded at build time from storage.googleapis.com/mediapipe-models and are not
committed to this repository. See the model card for intended use and limitations:
https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker

## Design references (no code copied)
- vmalikov/pose-detection-android — no licence file; used only as an architectural reference.
- arpitag0207/Physical-Rehabilitation-Assistant — no licence file; ideas only.
- deepanshumody/physiocoach (Apache-2.0; built on NVIDIA Live VLM WebUI, Apache-2.0) — exercise
  checklists consulted; no code copied.
- JayeshBainwad/FlexiPose (Apache-2.0) — doctor/patient workflow consulted; no code copied.
- Sujata005/PhysioAI (MIT) — reviewed; no code copied.

## Algorithms
- One Euro filter: G. Casiez, N. Roussel, D. Vogel. "1€ Filter: A Simple Speed-based Low-pass
  Filter for Noisy Input in Interactive Systems." CHI 2012. Implemented from the paper.
