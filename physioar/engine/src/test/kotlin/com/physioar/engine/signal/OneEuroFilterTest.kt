package com.physioar.engine.signal

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OneEuroFilterTest {

    @Test fun `first sample passes through`() {
        assertEquals(42.0, OneEuroFilter().filter(42.0, 0), 0.0)
    }

    @Test fun `constant signal is unchanged`() {
        val f = OneEuroFilter(1.0, 0.01)
        repeat(100) { assertEquals(90.0, f.filter(90.0, it * 33L), 1e-9) }
    }

    @Test fun `reduces jitter of a still signal`() {
        val r = Random(1)
        val f = OneEuroFilter(minCutoffHz = 1.5, beta = 0.02)
        val raw = (0 until 300).map { 90.0 + 3.0 * (r.nextDouble() * 2 - 1) }
        val out = raw.mapIndexed { i, v -> f.filter(v, i * 33L) }.drop(30)
        assertTrue(sd(out) < sd(raw.drop(30)) / 2, "sd raw=${sd(raw)} filtered=${sd(out)}")
    }

    @Test fun `follows a step change`() {
        val f = OneEuroFilter(minCutoffHz = 1.5, beta = 0.02)
        repeat(30) { f.filter(90.0, it * 33L) }
        var last = 0.0
        for (i in 30 until 90) last = f.filter(170.0, i * 33L)
        assertTrue(abs(last - 170.0) < 1.0, "last=$last")
    }

    @Test fun `non monotonic timestamp resets`() {
        val f = OneEuroFilter()
        f.filter(10.0, 1000)
        assertEquals(50.0, f.filter(50.0, 500), 0.0)
    }

    private fun sd(xs: List<Double>): Double {
        val m = xs.average()
        return sqrt(xs.sumOf { (it - m) * (it - m) } / xs.size)
    }
}

class WindowedVelocityTest {
    @Test fun `constant ramp gives its slope`() {
        val v = WindowedVelocity(200)
        var last = 0.0
        for (i in 0..30) last = v.update(90.0 + 0.1 * (i * 33), i * 33L) // 100 deg/s
        assertEquals(100.0, last, 1e-6)
    }

    @Test fun `first sample is zero and resets on time going backwards`() {
        val v = WindowedVelocity(200)
        assertEquals(0.0, v.update(5.0, 1000), 0.0)
        assertEquals(0.0, v.update(50.0, 500), 0.0)
    }
}
