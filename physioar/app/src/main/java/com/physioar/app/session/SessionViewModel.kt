package com.physioar.app.session

import android.app.Application
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import com.physioar.app.feedback.SpeechFeedback
import com.physioar.app.pose.MediaPipePoseMapper
import com.physioar.app.pose.PoseLandmarkerHelper
import com.physioar.app.pose.PoseModel
import com.physioar.engine.exercise.ExerciseAnalyzer
import com.physioar.engine.exercise.FeedbackCode
import com.physioar.engine.exercise.FrameAnalysis
import com.physioar.engine.exercise.SessionSummary
import com.physioar.engine.exercises.kneeextension.KneeExtensionParameters
import com.physioar.engine.exercises.kneeextension.SeatedKneeExtensionAnalyzer
import com.physioar.engine.pose.PoseFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SessionUiState(
    val pose: PoseFrame? = null,
    val analysis: FrameAnalysis? = null,
    val inferenceTimeMs: Long? = null,
    val error: String? = null,
    val finished: SessionSummary? = null,
)

/**
 * Glue between camera → MediaPipe → engine → UI. Holds no exercise logic itself.
 *
 * Threading: CameraX delivers frames on one executor; MediaPipe delivers results on its
 * own single callback thread, so the analyzer is only ever called from that thread.
 */
class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private var analyzer: ExerciseAnalyzer? = null
    private var helper: PoseLandmarkerHelper? = null
    private val speech = SpeechFeedback(application)
    private val lock = Any()

    fun start(params: KneeExtensionParameters, model: PoseModel = PoseModel.FULL) {
        synchronized(lock) {
            if (analyzer != null) return
            analyzer = SeatedKneeExtensionAnalyzer(params)
        }
        _state.value = SessionUiState()
        helper = PoseLandmarkerHelper(
            context = getApplication(),
            model = model,
            onResult = { bundle ->
                val frame = MediaPipePoseMapper.toPoseFrame(bundle)
                val analysis = synchronized(lock) {
                    analyzer?.process(frame, bundle.result.timestampMs())
                }
                if (analysis != null) {
                    speech.speak(analysis.feedback)
                    _state.update { it.copy(pose = frame, analysis = analysis, inferenceTimeMs = bundle.inferenceTimeMs) }
                    if (analysis.feedback.any { f -> f.code == FeedbackCode.SET_COMPLETE }) finish(null)
                }
            },
            onError = { msg -> _state.update { it.copy(error = msg) } },
        )
    }

    fun onCameraFrame(image: ImageProxy) {
        val h = helper
        if (h == null || h.isClosed()) image.close() else h.detectLiveStream(image)
    }

    /** Stop the session. [reason] null means it already stopped itself (set complete). */
    fun finish(reason: FeedbackCode? = FeedbackCode.STOPPED_BY_USER) {
        val summary = synchronized(lock) {
            val a = analyzer ?: return
            if (reason != null) a.stop(reason)
            a.summary()
        }
        _state.update { it.copy(finished = summary) }
    }

    fun reset() {
        helper?.close()
        helper = null
        synchronized(lock) { analyzer = null }
        _state.value = SessionUiState()
    }

    override fun onCleared() {
        helper?.close()
        speech.shutdown()
    }
}
