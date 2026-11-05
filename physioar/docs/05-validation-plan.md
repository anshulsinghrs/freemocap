# 05 — Testing and validation plan

## What has been tested so far

| Item | How | Result |
|---|---|---|
| Joint-angle maths (2D/3D, degenerate cases, non-square images) | JVM unit tests | pass |
| One Euro filter, windowed velocity | JVM unit tests | pass |
| Tracking quality gate | JVM unit tests | pass |
| Knee-extension state machine and every verdict, noise robustness, prescription-driven thresholds, set limit, stop | JVM unit tests on **synthetic** landmark sequences (`SyntheticSeatedPerson`) | pass |
| Exercise catalog completeness | JVM unit tests | pass |
| Android app compiles | — | **Not yet verified** (no Android SDK in the authoring environment) |
| App on a real phone: camera, MediaPipe, overlay alignment, TTS, latency | — | **Not yet tested** |
| Accuracy against real people / clinicians | — | **Not yet tested** |

Synthetic tests prove the logic does what it was designed to do. They say nothing about clinical
accuracy.

## Next validation steps

1. **Build and smoke-test** on 2–3 phones (one mid-range, e.g. 4–6 GB RAM, ~2021 SoC): frame rate,
   inference ms (shown on screen), overlay alignment, front and back camera, thermal behaviour
   over 10 minutes, *lite* vs *full* model.
2. **Recording protocol** (with consent, stored offline): seated knee extension from 0°, 15°, 30°,
   45° camera obliquity; 3 lighting conditions; varied clothing (shorts / loose trousers); varied
   body sizes; chair with and without armrests; deliberate errors (partial range, short hold,
   kick, drop, lean back, hip lift); partial occlusion; walking out of frame mid-rep.
3. **Ground truth:** physiotherapist labels each rep (verdict + issues) blind to app output;
   goniometer or marker-based reference for peak knee angle on a subset.
4. **Regression harness:** export per-frame `PoseFrame`s from recordings to JSON, replay through
   the engine in JVM tests (no device needed), and report a confusion matrix per verdict, rep-count
   error, and peak-angle error (bias, limits of agreement).
5. **Acceptance criteria** (to be agreed with clinicians before measuring): e.g. rep-count error,
   false-positive "compensation" rate, angle agreement limits.
6. **Re-tune** the placeholders (filters, view heuristic, 2D/3D limit) on a training split; report
   on a held-out split of different participants.
7. Check MediaPipe left/right labelling in side view (ADR-010).
