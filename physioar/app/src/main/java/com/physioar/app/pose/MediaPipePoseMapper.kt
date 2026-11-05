package com.physioar.app.pose

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.physioar.engine.pose.BodyLandmark
import com.physioar.engine.pose.Landmark
import com.physioar.engine.pose.PoseFrame

/**
 * The single place where MediaPipe types are converted into engine types. Swapping the
 * pose estimator (e.g. MoveNet) means writing another mapper; the engine is unchanged.
 */
object MediaPipePoseMapper {

    /** Returns null when no person was detected. */
    fun toPoseFrame(bundle: PoseResultBundle): PoseFrame? {
        val result: PoseLandmarkerResult = bundle.result
        val image = result.landmarks().firstOrNull() ?: return null
        if (image.size != BodyLandmark.COUNT) return null
        val world = result.worldLandmarks().firstOrNull()?.takeIf { it.size == BodyLandmark.COUNT }

        return PoseFrame(
            timestampMs = result.timestampMs(),
            imageLandmarks = image.map {
                Landmark(it.x(), it.y(), it.z(), it.visibility().orElse(null), it.presence().orElse(null))
            },
            worldLandmarks = world?.map {
                Landmark(it.x(), it.y(), it.z(), it.visibility().orElse(null), it.presence().orElse(null))
            },
            imageWidth = bundle.inputImageWidth,
            imageHeight = bundle.inputImageHeight,
        )
    }
}
