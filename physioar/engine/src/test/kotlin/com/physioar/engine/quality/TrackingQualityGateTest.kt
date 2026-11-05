package com.physioar.engine.quality

import com.physioar.engine.pose.BodyLandmark
import com.physioar.engine.pose.Landmark
import com.physioar.engine.pose.PoseFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackingQualityGateTest {
    private val req = setOf(BodyLandmark.RIGHT_KNEE, BodyLandmark.RIGHT_HIP)

    private fun frame(knee: Landmark) = PoseFrame(
        0,
        MutableList(BodyLandmark.COUNT) { Landmark(0.5f, 0.5f, 0f, 0.9f, 0.9f) }.also { it[BodyLandmark.RIGHT_KNEE.index] = knee },
        null, 100, 100,
    )

    @Test fun `no frame means no person`() =
        assertEquals(TrackingStatus.NO_PERSON, TrackingQualityGate.assess(null, req, TrackingRequirements()).status)

    @Test fun `confident landmarks pass`() =
        assertTrue(TrackingQualityGate.assess(frame(Landmark(0.4f, 0.4f, 0f, 0.9f, 0.9f)), req, TrackingRequirements()).isGood)

    @Test fun `low visibility fails`() {
        val a = TrackingQualityGate.assess(frame(Landmark(0.4f, 0.4f, 0f, 0.2f, 0.9f)), req, TrackingRequirements())
        assertEquals(TrackingStatus.LOW_CONFIDENCE, a.status)
        assertEquals(listOf(BodyLandmark.RIGHT_KNEE), a.failedLandmarks)
        assertTrue(a.outOfFrame.isEmpty())
    }

    @Test fun `low presence fails`() =
        assertEquals(TrackingStatus.LOW_CONFIDENCE,
            TrackingQualityGate.assess(frame(Landmark(0.4f, 0.4f, 0f, 0.9f, 0.1f)), req, TrackingRequirements()).status)

    @Test fun `out of frame fails even when visibility is high`() {
        val a = TrackingQualityGate.assess(frame(Landmark(1.2f, 0.4f, 0f, 0.99f, 0.99f)), req, TrackingRequirements())
        assertEquals(listOf(BodyLandmark.RIGHT_KNEE), a.outOfFrame)
    }

    @Test fun `missing confidence values are not treated as failures`() =
        assertTrue(TrackingQualityGate.assess(frame(Landmark(0.4f, 0.4f)), req, TrackingRequirements()).isGood)
}
