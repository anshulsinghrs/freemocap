/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ---------------------------------------------------------------------------
 * Adapted for PhysioAR from google-ai-edge/mediapipe-samples
 *   examples/pose_landmarker/android/.../PoseLandmarkerHelper.kt
 * Modifications (PhysioAR, 2026):
 *  - Reduced to LIVE_STREAM mode only; image/video modes removed.
 *  - Uses ImageProxy.toBitmap() instead of copyPixelsFromBuffer(), which breaks on
 *    devices whose RGBA buffers have row-stride padding.
 *  - Does NOT mirror front-camera frames before inference: a mirrored body swaps the
 *    model's anatomical left/right labels, which would make side-specific exercises
 *    evaluate the wrong limb. Mirroring is applied only when drawing (SkeletonOverlay).
 *  - Automatic CPU fallback if the GPU delegate fails to initialise.
 *  - Result type reduced to what PhysioAR needs; Kotlin callbacks instead of a listener.
 */
package com.physioar.app.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

enum class PoseModel(val assetName: String) {
    LITE("pose_landmarker_lite.task"),
    FULL("pose_landmarker_full.task"),
}

data class PoseResultBundle(
    val result: PoseLandmarkerResult,
    val inputImageWidth: Int,
    val inputImageHeight: Int,
    val inferenceTimeMs: Long,
)

class PoseLandmarkerHelper(
    private val context: Context,
    private val model: PoseModel = PoseModel.FULL,
    private val preferGpu: Boolean = false,
    private val minPoseDetectionConfidence: Float = 0.5f,
    private val minTrackingConfidence: Float = 0.5f,
    private val minPosePresenceConfidence: Float = 0.5f,
    private val onResult: (PoseResultBundle) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var poseLandmarker: PoseLandmarker? = null
    @Volatile private var lastFrameTimeMs = -1L

    init {
        setup()
    }

    private fun setup() {
        poseLandmarker = create(if (preferGpu) Delegate.GPU else Delegate.CPU)
            ?: if (preferGpu) create(Delegate.CPU) else null
        if (poseLandmarker == null) onError("Pose Landmarker failed to initialise. Is the model in assets/?")
    }

    private fun create(delegate: Delegate): PoseLandmarker? = try {
        val baseOptions = BaseOptions.builder()
            .setDelegate(delegate)
            .setModelAssetPath(model.assetName)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
            .setMinTrackingConfidence(minTrackingConfidence)
            .setMinPosePresenceConfidence(minPosePresenceConfidence)
            .setResultListener(this::returnLivestreamResult)
            .setErrorListener { e -> onError(e.message ?: "Unknown MediaPipe error") }
            .build()
        PoseLandmarker.createFromOptions(context, options)
    } catch (e: RuntimeException) {
        Log.e(TAG, "Failed to create PoseLandmarker with $delegate", e)
        null
    }

    fun isClosed(): Boolean = poseLandmarker == null

    fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    /** Called from the CameraX analysis thread. Always closes [imageProxy]. */
    fun detectLiveStream(imageProxy: ImageProxy) {
        val landmarker = poseLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }
        // MediaPipe requires strictly increasing timestamps.
        var frameTime = SystemClock.uptimeMillis()
        if (frameTime <= lastFrameTimeMs) frameTime = lastFrameTimeMs + 1
        lastFrameTimeMs = frameTime

        val rotation = imageProxy.imageInfo.rotationDegrees
        val bitmap: Bitmap = imageProxy.use { it.toBitmap() }
        val upright = if (rotation == 0) bitmap else {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        }
        landmarker.detectAsync(BitmapImageBuilder(upright).build(), frameTime)
    }

    private fun returnLivestreamResult(result: PoseLandmarkerResult, input: MPImage) {
        val inferenceTime = SystemClock.uptimeMillis() - result.timestampMs()
        onResult(PoseResultBundle(result, input.width, input.height, inferenceTime))
    }

    companion object {
        private const val TAG = "PoseLandmarkerHelper"
    }
}
