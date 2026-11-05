package com.physioar.engine.pose

/**
 * The 33 body landmarks produced by MediaPipe Pose Landmarker, in model output order.
 *
 * Index order follows the MediaPipe Pose Landmarker model card
 * (https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker).
 * The engine never depends on MediaPipe classes directly; the Android layer maps
 * MediaPipe results into [PoseFrame] using these indices.
 */
enum class BodyLandmark(val index: Int) {
    NOSE(0),
    LEFT_EYE_INNER(1), LEFT_EYE(2), LEFT_EYE_OUTER(3),
    RIGHT_EYE_INNER(4), RIGHT_EYE(5), RIGHT_EYE_OUTER(6),
    LEFT_EAR(7), RIGHT_EAR(8),
    MOUTH_LEFT(9), MOUTH_RIGHT(10),
    LEFT_SHOULDER(11), RIGHT_SHOULDER(12),
    LEFT_ELBOW(13), RIGHT_ELBOW(14),
    LEFT_WRIST(15), RIGHT_WRIST(16),
    LEFT_PINKY(17), RIGHT_PINKY(18),
    LEFT_INDEX(19), RIGHT_INDEX(20),
    LEFT_THUMB(21), RIGHT_THUMB(22),
    LEFT_HIP(23), RIGHT_HIP(24),
    LEFT_KNEE(25), RIGHT_KNEE(26),
    LEFT_ANKLE(27), RIGHT_ANKLE(28),
    LEFT_HEEL(29), RIGHT_HEEL(30),
    LEFT_FOOT_INDEX(31), RIGHT_FOOT_INDEX(32);

    companion object {
        const val COUNT = 33
        private val byIndex = entries.associateBy { it.index }
        fun fromIndex(index: Int): BodyLandmark? = byIndex[index]
    }
}

/** Which side of the body an exercise targets. Patient's anatomical side, not image side. */
enum class BodySide {
    LEFT, RIGHT;

    val opposite: BodySide get() = if (this == LEFT) RIGHT else LEFT

    fun shoulder() = if (this == LEFT) BodyLandmark.LEFT_SHOULDER else BodyLandmark.RIGHT_SHOULDER
    fun elbow() = if (this == LEFT) BodyLandmark.LEFT_ELBOW else BodyLandmark.RIGHT_ELBOW
    fun wrist() = if (this == LEFT) BodyLandmark.LEFT_WRIST else BodyLandmark.RIGHT_WRIST
    fun hip() = if (this == LEFT) BodyLandmark.LEFT_HIP else BodyLandmark.RIGHT_HIP
    fun knee() = if (this == LEFT) BodyLandmark.LEFT_KNEE else BodyLandmark.RIGHT_KNEE
    fun ankle() = if (this == LEFT) BodyLandmark.LEFT_ANKLE else BodyLandmark.RIGHT_ANKLE
    fun heel() = if (this == LEFT) BodyLandmark.LEFT_HEEL else BodyLandmark.RIGHT_HEEL
    fun footIndex() = if (this == LEFT) BodyLandmark.LEFT_FOOT_INDEX else BodyLandmark.RIGHT_FOOT_INDEX
}
