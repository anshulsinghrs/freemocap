package com.physioar.engine.pose

/**
 * One landmark as reported by the pose estimator.
 *
 * For image landmarks, [x] and [y] are normalised to [0, 1] by image width and height
 * (y grows downwards) and [z] is a relative depth with no metric meaning.
 * For world landmarks, x/y/z are metres relative to the hip centre (MediaPipe convention).
 *
 * [visibility] and [presence] are model confidences in [0, 1]; null if the estimator
 * does not provide them.
 */
data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float? = null,
    val presence: Float? = null,
)

/**
 * A single pose-estimation result for one camera frame. This is the only input the
 * engine needs; it is deliberately independent of MediaPipe / MoveNet types.
 *
 * @param timestampMs monotonic capture time in milliseconds.
 * @param imageLandmarks 33 landmarks in normalised image coordinates (see [BodyLandmark]).
 * @param worldLandmarks optional 33 landmarks in metric 3D (MediaPipe "world" landmarks).
 * @param imageWidth width in pixels of the image the landmarks were computed on.
 * @param imageHeight height in pixels of that image. Used to convert normalised
 *        coordinates into an isotropic pixel space before measuring angles.
 */
data class PoseFrame(
    val timestampMs: Long,
    val imageLandmarks: List<Landmark>,
    val worldLandmarks: List<Landmark>? = null,
    val imageWidth: Int,
    val imageHeight: Int,
) {
    init {
        require(imageLandmarks.size == BodyLandmark.COUNT) {
            "Expected ${BodyLandmark.COUNT} image landmarks, got ${imageLandmarks.size}"
        }
        require(worldLandmarks == null || worldLandmarks.size == BodyLandmark.COUNT) {
            "Expected ${BodyLandmark.COUNT} world landmarks, got ${worldLandmarks?.size}"
        }
        require(imageWidth > 0 && imageHeight > 0) { "Image size must be positive" }
    }

    operator fun get(landmark: BodyLandmark): Landmark = imageLandmarks[landmark.index]

    fun world(landmark: BodyLandmark): Landmark? = worldLandmarks?.get(landmark.index)
}
