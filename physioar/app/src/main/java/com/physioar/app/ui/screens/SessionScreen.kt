package com.physioar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.physioar.app.camera.CameraPreview
import com.physioar.app.session.SessionViewModel
import com.physioar.app.ui.components.SkeletonOverlay
import com.physioar.app.ui.theme.Caution
import com.physioar.app.ui.theme.Good
import com.physioar.app.ui.theme.Neutral
import com.physioar.app.ui.theme.Problem
import com.physioar.engine.exercise.Feedback
import com.physioar.engine.exercise.FeedbackCode
import com.physioar.engine.exercise.MovementPhase
import com.physioar.engine.exercise.MovementVerdict
import com.physioar.engine.exercise.SessionSummary
import com.physioar.engine.exercise.Severity
import com.physioar.engine.exercises.kneeextension.KneeExtensionParameters

/**
 * Live session: camera, skeleton, knee angle, phase, rep counter, feedback, stop controls.
 * Also serves as the calibration screen: until the baseline is captured the phase is SETUP
 * and the guidance text tells the patient how to position themselves.
 */
@Composable
fun SessionScreen(
    vm: SessionViewModel,
    params: KneeExtensionParameters,
    useFrontCamera: Boolean,
    onFinished: (SessionSummary) -> Unit,
) {
    LaunchedEffect(params) { vm.start(params) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { state.finished?.let(onFinished) }

    var lastMessage by remember { mutableStateOf<Feedback?>(null) }
    LaunchedEffect(state.analysis) {
        state.analysis?.feedback?.maxByOrNull { it.severity.ordinal }?.let { lastMessage = it }
    }

    val analysis = state.analysis
    val lastVerdict = analysis?.completedRepetition?.verdict
    val highlight = when {
        analysis?.frameVerdict == MovementVerdict.UNABLE_TO_EVALUATE -> Neutral
        lastMessage?.severity == Severity.WARNING -> Problem
        analysis?.phase == MovementPhase.HOLDING -> Good
        else -> Caution
    }

    Box(Modifier.fillMaxSize()) {
        CameraPreview(useFrontCamera, vm::onCameraFrame, Modifier.fillMaxSize())
        SkeletonOverlay(
            pose = state.pose,
            mirror = useFrontCamera,
            highlightSide = params.side,
            highlightColor = highlight,
            kneeAngleDeg = analysis?.angles?.get("knee"),
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(12.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text("Reps ${analysis?.validRepCount ?: 0} good / ${analysis?.repCount ?: 0} total",
                    color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(phaseLabel(analysis?.phase), color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            val knee = analysis?.angles?.get("knee")
            Text(
                "Knee ${knee?.let { "${it.toInt()}°" } ?: "–"}   target ${params.targetKneeAngleDeg.toInt()}°" +
                    (analysis?.holdRemainingMs?.let { "   hold ${(it + 999) / 1000}s" } ?: ""),
                color = Color.White,
            )
            LinearProgressIndicator(
                progress = { (analysis?.progressToTarget ?: 0.0).toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = highlight,
            )
            if (lastVerdict != null) Text("Last rep: ${verdictLabel(lastVerdict)}", color = Color.White)
            state.inferenceTimeMs?.let { Text("Pose inference ${it} ms", color = Color.LightGray, style = MaterialTheme.typography.labelSmall) }
            state.error?.let { Text(it, color = Problem) }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            lastMessage?.let {
                Text(
                    it.message, color = Color.White, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                        .background(severityColor(it.severity).copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.finish(FeedbackCode.STOPPED_BY_USER) }, modifier = Modifier.weight(1f)) {
                    Text("Finish", color = Color.White)
                }
                Button(
                    onClick = { vm.finish(FeedbackCode.STOPPED_BY_USER) },
                    colors = ButtonDefaults.buttonColors(containerColor = Problem),
                    modifier = Modifier.weight(1f),
                ) { Text("Stop – pain") }
            }
        }
    }
}

private fun phaseLabel(p: MovementPhase?) = when (p) {
    null, MovementPhase.SETUP -> "Get in position"
    MovementPhase.REST -> "Ready"
    MovementPhase.MOVING_TO_TARGET -> "Straightening"
    MovementPhase.HOLDING -> "Hold"
    MovementPhase.RETURNING -> "Lowering"
    MovementPhase.STOPPED -> "Stopped"
}

fun verdictLabel(v: MovementVerdict) = when (v) {
    MovementVerdict.CORRECT -> "correct"
    MovementVerdict.INCOMPLETE -> "incomplete"
    MovementVerdict.INCORRECT -> "incorrect technique"
    MovementVerdict.COMPENSATORY -> "compensation"
    MovementVerdict.UNCERTAIN_TRACKING -> "not scored (tracking)"
    MovementVerdict.UNABLE_TO_EVALUATE -> "unable to evaluate"
}

private fun severityColor(s: Severity) = when (s) {
    Severity.INFO -> Good
    Severity.GUIDANCE -> Neutral
    Severity.WARNING -> Caution
    Severity.STOP -> Problem
}
