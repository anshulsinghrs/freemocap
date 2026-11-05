package com.physioar.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.physioar.engine.exercise.AnalyzerStatus
import com.physioar.engine.exercise.ClinicalReviewStatus
import com.physioar.engine.exercise.ExerciseCatalog
import com.physioar.engine.exercise.ExerciseDefinition
import com.physioar.engine.exercises.kneeextension.KneeExtensionParameters
import com.physioar.engine.exercises.kneeextension.SeatedKneeExtension
import com.physioar.engine.pose.BodySide

/**
 * Exercise library + (temporary) local prescription entry. In Phase 6 the prescription
 * comes from the physiotherapist's account; until then it is entered here and is clearly
 * labelled as such.
 */
@Composable
fun ExerciseLibraryScreen(onStart: (KneeExtensionParameters, useFrontCamera: Boolean) -> Unit) {
    var expandedId by remember { mutableStateOf<String?>(SeatedKneeExtension.ID) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("PhysioAR", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Exercise library. Camera frames are analysed on this phone and never uploaded.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(ExerciseCatalog.all, key = { it.id }) { def ->
            ExerciseCard(
                def = def,
                expanded = expandedId == def.id,
                onToggle = { expandedId = if (expandedId == def.id) null else def.id },
                onStart = onStart,
            )
        }
    }
}

@Composable
private fun ExerciseCard(
    def: ExerciseDefinition,
    expanded: Boolean,
    onToggle: () -> Unit,
    onStart: (KneeExtensionParameters, Boolean) -> Unit,
) {
    Card(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(def.name, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(def.bodyRegion.name.lowercase().replace('_', ' ')) })
                if (def.analyzerStatus == AnalyzerStatus.PLANNED) AssistChip(onClick = {}, label = { Text("Coming soon") })
                if (def.clinicalReview == ClinicalReviewStatus.NOT_REVIEWED) {
                    AssistChip(onClick = {}, label = { Text("Not clinically reviewed") })
                }
            }
            if (expanded) {
                Text("Camera: ${def.cameraSetup.description}", style = MaterialTheme.typography.bodySmall)
                Text("Start: ${def.startingPosition}", style = MaterialTheme.typography.bodySmall)
                def.instructions.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall) }
                def.safetyConditions.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
                if (def.id == SeatedKneeExtension.ID) PrescriptionForm(onStart)
            }
        }
    }
}

@Composable
private fun PrescriptionForm(onStart: (KneeExtensionParameters, Boolean) -> Unit) {
    var side by remember { mutableStateOf(BodySide.RIGHT) }
    var target by remember { mutableStateOf(165) }
    var holdSec by remember { mutableStateOf(2) }
    var reps by remember { mutableStateOf(10) }
    var front by remember { mutableStateOf(true) }

    Spacer(Modifier.width(8.dp))
    Text("Prescription (enter as given by your physiotherapist)", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BodySide.entries.forEach { s ->
            FilterChip(selected = side == s, onClick = { side = s }, label = { Text("${s.name.lowercase()} leg") })
        }
    }
    Stepper("Target knee angle", "$target°", { target = (target - 5).coerceAtLeast(135) }, { target = (target + 5).coerceAtMost(180) })
    Stepper("Hold", "$holdSec s", { holdSec = (holdSec - 1).coerceAtLeast(0) }, { holdSec = (holdSec + 1).coerceAtMost(10) })
    Stepper("Repetitions", "$reps", { reps = (reps - 1).coerceAtLeast(1) }, { reps = (reps + 1).coerceAtMost(30) })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = front, onClick = { front = true }, label = { Text("Front camera") })
        FilterChip(selected = !front, onClick = { front = false }, label = { Text("Back camera") })
    }
    Button(
        onClick = {
            onStart(
                KneeExtensionParameters(
                    side = side,
                    targetKneeAngleDeg = target.toDouble(),
                    holdDurationMs = holdSec * 1000L,
                    maxRepsPerSet = reps,
                ),
                front,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Start exercise") }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onMinus) { Text("−") }
        Text(value, modifier = Modifier.padding(horizontal = 12.dp))
        OutlinedButton(onClick = onPlus) { Text("+") }
    }
}
