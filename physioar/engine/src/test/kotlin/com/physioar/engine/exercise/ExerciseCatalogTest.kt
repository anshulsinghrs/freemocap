package com.physioar.engine.exercise

import com.physioar.engine.exercises.kneeextension.SeatedKneeExtension
import com.physioar.engine.pose.BodySide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExerciseCatalogTest {
    @Test fun `contains the six initial exercises with unique ids`() {
        val ids = ExerciseCatalog.all.map { it.id }
        assertEquals(6, ids.size)
        assertEquals(ids.toSet().size, ids.size)
        assertTrue(listOf("seated_knee_extension", "shoulder_flexion", "sit_to_stand", "heel_raises",
            "hip_abduction", "wall_push_ups").all { it in ids })
    }

    @Test fun `only knee extension claims an analyzer`() {
        val implemented = ExerciseCatalog.all.filter { it.analyzerStatus == AnalyzerStatus.IMPLEMENTED }.map { it.id }
        assertEquals(listOf(SeatedKneeExtension.ID), implemented)
    }

    @Test fun `nothing claims clinical review yet`() =
        assertTrue(ExerciseCatalog.all.all { it.clinicalReview == ClinicalReviewStatus.NOT_REVIEWED })

    @Test fun `every definition is complete`() {
        for (d in ExerciseCatalog.all) {
            for (s in BodySide.entries) assertTrue(d.requiredLandmarks(s).isNotEmpty(), d.id)
            assertTrue(d.phases.isNotEmpty() && d.measuredAngles.isNotEmpty() && d.commonErrors.isNotEmpty(), d.id)
            assertTrue(d.safetyConditions.isNotEmpty() && d.instructions.isNotEmpty(), d.id)
        }
    }

    @Test fun `engine-detected errors carry a feedback code`() {
        for (d in ExerciseCatalog.all) for (e in d.commonErrors) {
            if (e.detectedByEngine) assertTrue(e.code != null, "${d.id}: ${e.description}")
        }
    }
}
