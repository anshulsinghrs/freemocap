# 03 — Integration decisions (ADR log)

Short architecture decision records. Add new ones at the end; do not rewrite accepted ones.

### ADR-001 — Project location
PhysioAR lives in `physioar/` inside this repository (branch-based development was requested
here). It has no dependency on the surrounding FreeMoCap Python code. **Consequence:** it can be
moved to its own repository without changes.

### ADR-002 — One pose engine: MediaPipe Pose Landmarker
MoveNet (pose-detection-android) was evaluated against MediaPipe. MediaPipe wins on what the
exercises need: 33 landmarks including heel and foot index (needed for heel raises; MoveNet's 17
COCO points have none), metric world landmarks (used for a 2D/3D cross-check), and per-landmark
presence and visibility. MoveNet Lightning is faster, but MediaPipe *lite* is available for slow
devices. Only one model runs at a time. **Consequence:** the engine reads a neutral `PoseFrame`;
swapping estimators means writing a new mapper, not changing exercise logic.

### ADR-003 — Engine is a separate, Android-free Gradle build
The engine is an included build with only the Kotlin stdlib. **Why:** the most important logic
must be testable without a device or emulator, and so CI and Python-free offline experiments can
run the exact same code. **Consequence:** `engine/` builds with `./gradlew test` on any JDK 17+.

### ADR-004 — Rule-based finite-state machines, not ML, for movement evaluation (for now)
Per component:
- Landmark extraction: ML (MediaPipe). Necessary.
- Angles, smoothing, phases, rep counting: geometry + filters + FSM. ML adds nothing, and an FSM
  can be explained to a clinician.
- Error detection: rules on interpretable features (trunk deviation, hip lift, velocity). There
  is no labelled data yet, and clinicians need to know why a repetition was flagged.
- *Possible later ML:* exercise recognition (auto-detect which exercise is being done) and
  learned error classifiers, **only** once physiotherapist-labelled recordings exist. A model
  must beat the rules on held-out patients before replacing them.

### ADR-005 — Measure angles in isotropic pixel space
MediaPipe normalises x by width and y by height. Angles computed on normalised coordinates (as
FlexiPose and several Python repos do) are distorted for any non-square frame. The engine
converts to pixels first (`PoseFrame.pixel`). Covered by a unit test.

### ADR-006 — Each exercise has its own analyzer and its own validation rules
No shared "two thresholds on one angle" counter. Shared code is limited to building blocks
(tracking gate, filters, geometry, verdict types). **Consequence:** more code per exercise, but
each exercise's rules can be reviewed by a clinician on their own.

### ADR-007 — No clinical limits in code; every threshold comes from the prescription
All limits are fields of a per-exercise `Parameters` class supplied per patient. Defaults exist
only so that the tests and demo can run, and are labelled **NOT CLINICALLY REVIEWED** in code, in
the definition (`ClinicalReviewStatus.NOT_REVIEWED`), and in the UI.

### ADR-008 — Six-way verdict; never judge what was not seen
Repetitions are CORRECT / INCOMPLETE / INCORRECT / COMPENSATORY / UNCERTAIN_TRACKING; frames that
cannot be judged are UNABLE_TO_EVALUATE. Precedence: tracking uncertainty first, then
compensation, then incorrect execution, then incomplete. A repetition with more than the
prescribed fraction of untracked time is not scored. A single 2D angle alone is never reported
as a clinical error: the cues name the observed behaviour ("Keep your back still") and the
results screen says the angles are camera estimates, not clinical measurements.

### ADR-009 — Time-based thresholds
Hold, calibration and velocity use timestamps, not frame counts, so behaviour is the same at 15
and 30 fps (pose-detection-android's frame-count debounce is not).

### ADR-010 — Front camera: do not mirror before inference
The MediaPipe sample mirrors front-camera frames before inference. A mirrored body swaps the
model's anatomical left/right labels, so a left-knee prescription could be evaluated on the
right knee. PhysioAR runs inference on the unmirrored image and mirrors only the overlay.
**Still to verify on device:** MediaPipe's left/right labelling in a pure side view (where one
side is occluded) must be checked on recordings. If it is unreliable, the analyzer should select
the limb nearest the camera instead of trusting labels.

### ADR-011 — Lowering speed measured as velocity, not duration
First implementation flagged "dropping the leg" when the time from leaving the hold to reaching the
rest limit was under 800 ms. Tests showed a normal 2 s lowering measured ~780 ms, because only part
of the arc lies between those two thresholds. Replaced by a peak lowering velocity over a 200 ms
window (`maxReturnVelocityDegPerSec`), symmetric with the kick check. Frame-to-frame velocity was
also rejected: with realistic jitter it spiked above the limit during normal movement.

### ADR-012 — No network permission in the MVP
The MVP app requests only CAMERA. Nothing leaves the phone. Phase 6 will add sync of *summaries*
(rep results, angles), never video, behind explicit consent.

### ADR-013 — AR is optional and outside the measurement loop
Unity/AR Foundation will be used for demonstrations (placing a reference avatar, trajectories),
launched as a separate activity. It does not do body tracking: on ARCore, AR Foundation has no
human body tracking *(external knowledge — verify)*, and running ARCore plus MediaPipe at the same
time would compete for the camera and CPU. Measurement always uses the MediaPipe path.
