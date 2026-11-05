package com.physioar.engine.exercise

import com.physioar.engine.quality.TrackingStatus

/** Generic phase labels. Each exercise uses the subset that applies to it. */
enum class MovementPhase {
    /** Waiting for a stable starting position to capture the patient's baseline. */
    SETUP,
    /** At rest in the starting position, between repetitions. */
    REST,
    /** Moving from the starting position towards the target. */
    MOVING_TO_TARGET,
    /** At (or beyond) the prescribed target, holding. */
    HOLDING,
    /** Returning to the starting position. */
    RETURNING,
    /** Session stopped (set complete, or stopped by the user). */
    STOPPED,
}

/**
 * Classification of a repetition (or of a frame, for the last two values).
 * PhysioAR never reports a movement as clinically incorrect from a single 2D angle;
 * these labels describe what the engine observed against the prescription.
 */
enum class MovementVerdict {
    /** Reached the prescribed range, held as prescribed, no compensation observed. */
    CORRECT,
    /** Movement pattern acceptable but prescribed range or hold was not achieved. */
    INCOMPLETE,
    /** Movement executed in a way the prescription excludes (e.g. ballistic, dropped). */
    INCORRECT,
    /** Target reached by substituting another body segment (e.g. leaning back). */
    COMPENSATORY,
    /** Tracking was unreliable for part of the repetition; result not trusted. */
    UNCERTAIN_TRACKING,
    /** The current frame cannot be evaluated (person not visible, wrong camera view). */
    UNABLE_TO_EVALUATE,
}

enum class Severity { INFO, GUIDANCE, WARNING, STOP }

/** Stable identifiers for feedback, so UI/TTS/localisation can key on them. */
enum class FeedbackCode {
    // Setup and tracking
    STEP_INTO_VIEW,
    MOVE_FULL_BODY_INTO_FRAME,
    TURN_SIDE_TO_CAMERA,
    HOLD_STARTING_POSITION,
    TRACKING_LOST,
    READY,
    // Knee extension
    STRAIGHTEN_KNEE_MORE,
    HOLD_POSITION,
    HOLD_COMPLETE,
    HOLD_TOO_SHORT,
    LOWER_SLOWLY,
    AVOID_KICKING,
    KEEP_BACK_STILL,
    KEEP_HIPS_ON_CHAIR,
    // Generic
    GOOD_REPETITION,
    SET_COMPLETE,
    STOPPED_BY_USER,
}

data class Feedback(
    val code: FeedbackCode,
    val message: String,
    val severity: Severity,
)

/** Everything the UI needs to render one processed frame. */
data class FrameAnalysis(
    val timestampMs: Long,
    val phase: MovementPhase,
    val tracking: TrackingStatus,
    /** Null while the frame is evaluable; otherwise why it is not. */
    val frameVerdict: MovementVerdict?,
    /** Named, smoothed angles in degrees (e.g. "knee"), for display. */
    val angles: Map<String, Double>,
    val repCount: Int,
    val validRepCount: Int,
    /** Progress 0..1 from starting position to prescribed target, if meaningful. */
    val progressToTarget: Double?,
    /** Remaining hold time in ms while HOLDING, else null. */
    val holdRemainingMs: Long?,
    val feedback: List<Feedback>,
    /** Result of a repetition that finished on this frame, if any. */
    val completedRepetition: RepetitionResult?,
)

data class RepetitionResult(
    val index: Int,
    val verdict: MovementVerdict,
    val startMs: Long,
    val endMs: Long,
    /** Peak of the primary angle reached during the repetition (degrees). */
    val peakAngleDeg: Double,
    /** Angle at the start of the repetition (degrees). */
    val startAngleDeg: Double,
    val holdDurationMs: Long,
    /** Every issue observed, not only the one that decided the verdict. */
    val issues: List<FeedbackCode>,
    /** Fraction of the repetition's duration with unreliable tracking, 0..1. */
    val untrackedFraction: Double,
) {
    val durationMs: Long get() = endMs - startMs
    val rangeOfMotionDeg: Double get() = peakAngleDeg - startAngleDeg
}

data class SessionSummary(
    val exerciseId: String,
    val startedAtMs: Long?,
    val endedAtMs: Long?,
    val repetitions: List<RepetitionResult>,
    /** Fraction of processed frames with GOOD tracking. */
    val trackedFrameFraction: Double,
    val stoppedReason: FeedbackCode?,
) {
    val totalReps: Int get() = repetitions.size
    val validReps: Int get() = repetitions.count { it.verdict == MovementVerdict.CORRECT }
    fun count(verdict: MovementVerdict): Int = repetitions.count { it.verdict == verdict }
    val durationMs: Long get() = if (startedAtMs != null && endedAtMs != null) endedAtMs - startedAtMs else 0
    /** Best peak angle among repetitions whose tracking was trusted. */
    val bestTrustedPeakDeg: Double? get() =
        repetitions.filter { it.verdict != MovementVerdict.UNCERTAIN_TRACKING }.maxOfOrNull { it.peakAngleDeg }
    val meanTrustedPeakDeg: Double? get() =
        repetitions.filter { it.verdict != MovementVerdict.UNCERTAIN_TRACKING }
            .map { it.peakAngleDeg }.takeIf { it.isNotEmpty() }?.average()
}
