package com.physioar.engine.quality

import com.physioar.engine.pose.BodyLandmark
import com.physioar.engine.pose.PoseFrame

/** Thresholds a frame must satisfy before any measurement from it is trusted. */
data class TrackingRequirements(
    /** Minimum MediaPipe visibility for each required landmark. */
    val minVisibility: Float = 0.5f,
    /** Minimum MediaPipe presence for each required landmark (ignored if not reported). */
    val minPresence: Float = 0.5f,
    /**
     * Landmarks must lie inside the image with at least this normalised margin; MediaPipe
     * extrapolates off-screen joints, and those coordinates are not measurements.
     */
    val inFrameMargin: Float = 0.01f,
)

enum class TrackingStatus {
    /** All required landmarks are confidently tracked. */
    GOOD,
    /** No pose was detected in the frame at all. */
    NO_PERSON,
    /** A pose was detected but at least one required landmark is unreliable. */
    LOW_CONFIDENCE,
}

data class TrackingAssessment(
    val status: TrackingStatus,
    /** Required landmarks that failed (empty when [status] is GOOD). */
    val failedLandmarks: List<BodyLandmark> = emptyList(),
    /** Landmarks that failed specifically because they are outside the image. */
    val outOfFrame: List<BodyLandmark> = emptyList(),
) {
    val isGood: Boolean get() = status == TrackingStatus.GOOD
}

/** Verifies landmark confidence for the landmarks a particular exercise depends on. */
object TrackingQualityGate {

    fun assess(
        frame: PoseFrame?,
        required: Collection<BodyLandmark>,
        requirements: TrackingRequirements,
    ): TrackingAssessment {
        if (frame == null) return TrackingAssessment(TrackingStatus.NO_PERSON)
        val failed = mutableListOf<BodyLandmark>()
        val outOfFrame = mutableListOf<BodyLandmark>()
        val m = requirements.inFrameMargin
        for (lm in required) {
            val l = frame[lm]
            val inFrame = l.x in m..(1f - m) && l.y in m..(1f - m)
            val visOk = (l.visibility ?: 1f) >= requirements.minVisibility
            val presOk = (l.presence ?: 1f) >= requirements.minPresence
            if (!inFrame) outOfFrame += lm
            if (!inFrame || !visOk || !presOk) failed += lm
        }
        return if (failed.isEmpty()) {
            TrackingAssessment(TrackingStatus.GOOD)
        } else {
            TrackingAssessment(TrackingStatus.LOW_CONFIDENCE, failed, outOfFrame)
        }
    }
}
