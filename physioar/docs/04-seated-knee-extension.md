# 04 — Seated knee extension (first implemented exercise)

Code: `engine/src/main/kotlin/com/physioar/engine/exercises/kneeextension/`

## Why this exercise first (instead of a squat)

- **It is in the required six; a squat is not.**
- **Single joint, single plane.** The primary measure is one sagittal-plane knee angle, the
  case a single side-on RGB camera handles best.
- **Stable reference segment.** The patient is seated, so the thigh and trunk should barely
  move. That makes compensation (trunk lean, hip lift) easy to define as deviation from a
  seated baseline.
- **Low self-occlusion** for the leg nearest the camera. In a squat the far leg and the hands
  often hide the near knee.
- **Safe** to perform alone at home.

Known weakness: when seated, MediaPipe's hip landmark is partly hidden by the thigh and chair.
The hip only defines the thigh direction, and the thigh is nearly still, so this mainly affects
the hip-lift check. It must be checked on recordings.

## Specification

| Item | Value |
|---|---|
| Identifier | `seated_knee_extension` |
| Required landmarks | exercised side: shoulder, hip, knee, ankle (visibility ≥ 0.5, presence ≥ 0.5, inside frame) |
| Camera | Side view, perpendicular to the leg, exercised side towards the camera, ~knee–hip height, 2–3 m |
| Starting position | Seated upright, back supported, knee ~90°, foot flat |
| Phases | SETUP → REST → MOVING_TO_TARGET → HOLDING ⇄ RETURNING → REST |
| Primary angle | Interior hip–knee–ankle angle in pixel space (180° = straight) |
| Secondary | Trunk inclination (hip→shoulder vs vertical), hip vertical displacement / thigh length, 3D knee angle from world landmarks (cross-check only) |
| Rep starts | knee > `restMaxKneeAngleDeg + startHysteresisDeg` |
| Rep ends | knee ≤ `restMaxKneeAngleDeg` |
| Ignored as noise | peak − rest limit < `minExcursionDeg` |
| Target reached | knee ≥ `targetKneeAngleDeg` |
| Hold | time with knee ≥ target − `holdToleranceDeg`, must reach `holdDurationMs` |
| Errors detected | not straight enough · hold too short · kicking (extension velocity) · dropping (lowering velocity) · trunk lean · hip lift |
| Not detectable from one side view | hip rotation, foot turned out, frontal-plane knee deviation |
| Safety | stop/pain button ends the session immediately; set stops at `maxRepsPerSet`; tracking loss pauses evaluation; on-screen and documented "does not diagnose" |

### Verdicts

| Verdict | Condition (in precedence order) |
|---|---|
| UNCERTAIN_TRACKING | untracked time > `maxUntrackedFraction` of the rep |
| COMPENSATORY | trunk deviation > `trunkLeanToleranceDeg` or hip lift > `hipLiftToleranceThighFraction` |
| INCORRECT | extension velocity > `maxExtensionVelocityDegPerSec` or lowering velocity > `maxReturnVelocityDegPerSec` |
| INCOMPLETE | target not reached, or hold shorter than prescribed |
| CORRECT | none of the above |
| UNABLE_TO_EVALUATE (frame) | no person, low-confidence landmarks, out of frame, frontal view, or 2D/3D disagreement |

### Default parameters: engineering placeholders, NOT clinically reviewed

| Parameter | Default | Note |
|---|---|---|
| targetKneeAngleDeg | 165 | Patient-specific; post-op patients may be prescribed far less |
| restMaxKneeAngleDeg | 115 | |
| holdDurationMs | 2000 | |
| maxExtensionVelocityDegPerSec | 250 | Over a 200 ms window |
| maxReturnVelocityDegPerSec | 150 | Over a 200 ms window |
| trunkLeanToleranceDeg | 15 | |
| hipLiftToleranceThighFraction | 0.15 | |
| maxUntrackedFraction | 0.2 | |
| maxFrontalViewRatio | 0.5 | Shoulder width / torso length heuristic |
| maxImageWorldAngleDisagreementDeg | 30 | Needs tuning; MediaPipe world z is noisy |

**These need review by a physiotherapist before any patient use.** The camera-view heuristic,
the 2D/3D disagreement limit and the One Euro filter settings were tuned on synthetic data only.

## Single-RGB-camera adequacy

A single camera is adequate for a 2D knee angle **only when** it is roughly perpendicular to the
sagittal plane. The projected angle error grows with obliquity: a straight leg always projects
straight, but a 90° knee viewed 30° off-axis projects noticeably wider than 90° *(geometry; size
to be measured on recordings)*. Mitigations implemented: a frontal-view rejection heuristic, and
an optional cross-check against MediaPipe's 3D world landmarks. Not implemented: on-screen camera
placement guidance using phone orientation sensors, or a calibration pose. Both are candidates if
validation shows obliquity errors.
