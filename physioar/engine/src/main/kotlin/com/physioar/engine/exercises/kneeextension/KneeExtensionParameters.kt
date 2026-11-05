package com.physioar.engine.exercises.kneeextension

import com.physioar.engine.pose.BodySide
import com.physioar.engine.quality.TrackingRequirements

/**
 * Patient-specific prescription for seated knee extension.
 *
 * Every clinical limit lives here and is supplied per patient by the physiotherapist's
 * prescription; the analyzer contains no hardcoded clinical thresholds.
 *
 * IMPORTANT: the default values are ENGINEERING PLACEHOLDERS chosen to make the
 * synthetic tests meaningful. They have NOT been reviewed by a physiotherapist and
 * must be replaced by clinically reviewed, patient-specific values before any use
 * with patients. See docs/04-seated-knee-extension.md.
 *
 * Angles are the interior knee angle hip–knee–ankle in degrees:
 * ~90° = knee bent at a right angle (seated start), 180° = fully straight.
 */
data class KneeExtensionParameters(
    /** Leg being exercised (patient's anatomical side). */
    val side: BodySide,
    /** Knee angle that counts as reaching the prescribed extension. */
    val targetKneeAngleDeg: Double = 165.0,
    /** The knee must return below this angle for a repetition to finish. */
    val restMaxKneeAngleDeg: Double = 115.0,
    /** Hysteresis above [restMaxKneeAngleDeg] before a repetition is considered started. */
    val startHysteresisDeg: Double = 5.0,
    /** Movements whose peak is less than this above the rest limit are ignored as noise. */
    val minExcursionDeg: Double = 15.0,
    /** Required hold at target, in milliseconds. 0 disables the hold requirement. */
    val holdDurationMs: Long = 2000,
    /** While holding, the knee may sag this much below target before the hold is broken. */
    val holdToleranceDeg: Double = 5.0,
    /** A drop of this much from the peak (before target) means the patient is returning. */
    val reversalDeg: Double = 10.0,
    /** Smoothed knee angular velocity above this during extension is treated as kicking. */
    val maxExtensionVelocityDegPerSec: Double = 250.0,
    /**
     * Smoothed knee flexion velocity above this while lowering is treated as dropping the leg.
     * (A duration-based limit was rejected: the observed window from target to the rest limit
     * covers only part of the arc and depends on the prescription, so it misfired.)
     */
    val maxReturnVelocityDegPerSec: Double = 150.0,
    /** Trunk inclination change from the seated baseline that indicates leaning back. */
    val trunkLeanToleranceDeg: Double = 15.0,
    /** Upward hip displacement, as a fraction of thigh length, that indicates hip lift. */
    val hipLiftToleranceThighFraction: Double = 0.15,
    /** Repetitions with more than this fraction of untracked time are UNCERTAIN_TRACKING. */
    val maxUntrackedFraction: Double = 0.2,
    /** Stop and report SET_COMPLETE after this many repetitions (any verdict). */
    val maxRepsPerSet: Int = 10,
    /** How long the starting position must be held to capture the baseline. */
    val calibrationHoldMs: Long = 1000,
    /**
     * Side-view check: shoulder width / torso length above this means the patient is
     * facing the camera rather than side-on (heuristic; validate on recordings).
     */
    val maxFrontalViewRatio: Double = 0.5,
    /**
     * If world (3D) landmarks are available and the 3D knee angle differs from the 2D
     * image angle by more than this, the frame is treated as untrusted. Null disables.
     */
    val maxImageWorldAngleDisagreementDeg: Double? = 30.0,
    /** Gap in tracking after which the smoothing filters are reset. */
    val filterResetGapMs: Long = 500,
    val tracking: TrackingRequirements = TrackingRequirements(),
) {
    init {
        require(targetKneeAngleDeg in 100.0..180.0) { "targetKneeAngleDeg out of range: $targetKneeAngleDeg" }
        require(restMaxKneeAngleDeg in 60.0..170.0) { "restMaxKneeAngleDeg out of range: $restMaxKneeAngleDeg" }
        require(targetKneeAngleDeg - restMaxKneeAngleDeg >= minExcursionDeg) {
            "Target must exceed rest limit by at least minExcursionDeg"
        }
        require(holdDurationMs >= 0 && calibrationHoldMs >= 0)
        require(maxExtensionVelocityDegPerSec > 0 && maxReturnVelocityDegPerSec > 0)
        require(maxUntrackedFraction in 0.0..1.0)
        require(maxRepsPerSet > 0)
    }
}
