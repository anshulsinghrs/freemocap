package com.physioar.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.physioar.engine.exercise.MovementVerdict
import com.physioar.engine.exercise.SessionSummary

@Composable
fun ResultsScreen(summary: SessionSummary, onDone: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Session results", style = MaterialTheme.typography.headlineSmall)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Line("Total repetitions", "${summary.totalReps}")
                    Line("Correct", "${summary.validReps}")
                    Line("Incomplete", "${summary.count(MovementVerdict.INCOMPLETE)}")
                    Line("Incorrect technique", "${summary.count(MovementVerdict.INCORRECT)}")
                    Line("Compensation", "${summary.count(MovementVerdict.COMPENSATORY)}")
                    Line("Not scored (tracking)", "${summary.count(MovementVerdict.UNCERTAIN_TRACKING)}")
                    Line("Best knee angle", summary.bestTrustedPeakDeg?.let { "${it.toInt()}°" } ?: "–")
                    Line("Mean peak knee angle", summary.meanTrustedPeakDeg?.let { "${it.toInt()}°" } ?: "–")
                    Line("Duration", "${summary.durationMs / 1000} s")
                    Line("Frames tracked", "${(summary.trackedFrameFraction * 100).toInt()} %")
                }
            }
            Text(
                "Angles are 2D camera estimates for tracking your own progress. They are not a clinical " +
                    "measurement; discuss changes with your physiotherapist.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        items(summary.repetitions) { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Rep ${r.index}: ${verdictLabel(r.verdict)}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Peak ${r.peakAngleDeg.toInt()}°, range ${r.rangeOfMotionDeg.toInt()}°, hold ${r.holdDurationMs / 100 / 10.0}s" +
                            if (r.issues.isNotEmpty()) "\n" + r.issues.joinToString { it.name.lowercase().replace('_', ' ') } else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item { Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") } }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
