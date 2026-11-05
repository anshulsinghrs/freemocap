package com.physioar.engine.exercise

import com.physioar.engine.pose.PoseFrame

/**
 * Stateful, per-session analyzer for one exercise. Implementations are not thread-safe;
 * feed frames from a single thread in timestamp order.
 *
 * Contract per frame: verify landmark confidence → compute angles → smooth →
 * update phase state machine → detect errors → classify finished repetitions →
 * emit feedback. A null frame means "no person detected".
 */
interface ExerciseAnalyzer {
    val definition: ExerciseDefinition

    fun process(frame: PoseFrame?, timestampMs: Long = frame?.timestampMs ?: 0L): FrameAnalysis

    /** Stop the session (e.g. patient pressed STOP or reported pain). */
    fun stop(reason: FeedbackCode = FeedbackCode.STOPPED_BY_USER)

    fun summary(): SessionSummary
}

/**
 * Suppresses repeats of the same feedback code within [minIntervalMs], so the UI and
 * text-to-speech are not flooded at 30 frames per second.
 */
class FeedbackThrottle(private val minIntervalMs: Long = 2500) {
    private val lastEmitted = HashMap<FeedbackCode, Long>()

    fun admit(feedback: Feedback, nowMs: Long): Boolean {
        if (feedback.severity == Severity.STOP) return true
        val last = lastEmitted[feedback.code]
        if (last != null && nowMs - last < minIntervalMs) return false
        lastEmitted[feedback.code] = nowMs
        return true
    }

    fun reset() = lastEmitted.clear()
}
