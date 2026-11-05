package com.physioar.engine.geometry

import com.physioar.engine.pose.BodyLandmark
import com.physioar.engine.pose.Landmark
import com.physioar.engine.pose.PoseFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VectorsTest {
    private val tol = 1e-6

    @Test fun `right angle`() =
        assertEquals(90.0, interiorAngleDeg(Vec2(1.0, 0.0), Vec2(0.0, 0.0), Vec2(0.0, 1.0))!!, tol)

    @Test fun `straight line is 180`() =
        assertEquals(180.0, interiorAngleDeg(Vec2(-1.0, 0.0), Vec2(0.0, 0.0), Vec2(2.0, 0.0))!!, tol)

    @Test fun `folded segments are 0`() =
        assertEquals(0.0, interiorAngleDeg(Vec2(1.0, 0.0), Vec2(0.0, 0.0), Vec2(3.0, 0.0))!!, tol)

    @Test fun `angle is symmetric in end points`() {
        val a = Vec2(3.0, 1.0); val b = Vec2(0.5, -2.0); val c = Vec2(-1.0, 4.0)
        assertEquals(interiorAngleDeg(a, b, c)!!, interiorAngleDeg(c, b, a)!!, tol)
    }

    @Test fun `135 degrees`() =
        assertEquals(135.0, interiorAngleDeg(Vec2(1.0, 0.0), Vec2(0.0, 0.0), Vec2(-1.0, 1.0))!!, tol)

    @Test fun `degenerate segment returns null`() =
        assertNull(interiorAngleDeg(Vec2(0.0, 0.0), Vec2(0.0, 0.0), Vec2(1.0, 1.0)))

    @Test fun `3D angle`() {
        assertEquals(90.0, interiorAngleDeg(Vec3(0.0, 0.0, 1.0), Vec3(0.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0))!!, tol)
        assertNull(interiorAngleDeg(Vec3(0.0, 0.0, 0.0), Vec3(0.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0)))
    }

    @Test fun `inclination from vertical with image y down`() {
        val hip = Vec2(0.0, 0.0)
        assertEquals(0.0, inclinationFromVerticalDeg(hip, Vec2(0.0, -10.0))!!, tol)   // straight up
        assertEquals(90.0, inclinationFromVerticalDeg(hip, Vec2(10.0, 0.0))!!, tol)   // horizontal
        assertEquals(45.0, inclinationFromVerticalDeg(hip, Vec2(-10.0, -10.0))!!, tol)
        assertNull(inclinationFromVerticalDeg(hip, hip))
    }

    @Test fun `angles are measured in pixel space for non-square images`() {
        // True 90 degree angle in a 2000x1000 image. In normalised coordinates it would look like ~63 degrees.
        val lm = MutableList(BodyLandmark.COUNT) { Landmark(0.5f, 0.5f) }
        lm[BodyLandmark.RIGHT_HIP.index] = Landmark(400f / 2000, 500f / 1000)
        lm[BodyLandmark.RIGHT_KNEE.index] = Landmark(600f / 2000, 500f / 1000)
        lm[BodyLandmark.RIGHT_ANKLE.index] = Landmark(600f / 2000, 700f / 1000)
        val f = PoseFrame(0, lm, null, 2000, 1000)
        val angle = interiorAngleDeg(f.pixel(BodyLandmark.RIGHT_HIP), f.pixel(BodyLandmark.RIGHT_KNEE), f.pixel(BodyLandmark.RIGHT_ANKLE))!!
        assertEquals(90.0, angle, 1e-3)
    }
}
