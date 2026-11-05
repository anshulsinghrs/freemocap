package com.physioar.engine.signal

import kotlin.math.PI
import kotlin.math.abs

/**
 * One Euro filter: an adaptive low-pass filter for noisy real-time signals.
 *
 * Casiez, Roussel & Vogel, "1€ Filter: A Simple Speed-based Low-pass Filter for Noisy
 * Input in Interactive Systems", CHI 2012.
 *
 * At low speeds the cutoff is [minCutoffHz] (strong smoothing of landmark jitter while
 * the patient holds still); as speed rises the cutoff rises by [beta] × |speed|, which
 * keeps lag low during real movement. Timestamps are used (not a fixed frame rate) because
 * camera/inference frame intervals vary on mid-range phones.
 *
 * Parameter values for each signal are set by the exercise analyzer; they are tuned on
 * synthetic data only and must be re-tuned on recorded patient video (see docs).
 */
class OneEuroFilter(
    private val minCutoffHz: Double = 1.0,
    private val beta: Double = 0.0,
    private val derivativeCutoffHz: Double = 1.0,
) {
    private var lastTimestampMs: Long? = null
    private var xPrev = 0.0
    private var dxPrev = 0.0

    /** Latest filtered derivative, in units per second. 0 until two samples are seen. */
    var lastDerivativePerSecond: Double = 0.0
        private set

    fun filter(value: Double, timestampMs: Long): Double {
        val prevT = lastTimestampMs
        if (prevT == null || timestampMs <= prevT) {
            // First sample (or non-monotonic clock): reset state to the raw value.
            lastTimestampMs = timestampMs
            xPrev = value
            dxPrev = 0.0
            lastDerivativePerSecond = 0.0
            return value
        }
        val dt = (timestampMs - prevT) / 1000.0
        lastTimestampMs = timestampMs

        val dx = (value - xPrev) / dt
        val dxHat = lowPass(dx, dxPrev, alpha(derivativeCutoffHz, dt))
        dxPrev = dxHat
        lastDerivativePerSecond = dxHat

        val cutoff = minCutoffHz + beta * abs(dxHat)
        val xHat = lowPass(value, xPrev, alpha(cutoff, dt))
        xPrev = xHat
        return xHat
    }

    fun reset() {
        lastTimestampMs = null
        xPrev = 0.0
        dxPrev = 0.0
        lastDerivativePerSecond = 0.0
    }

    private fun alpha(cutoffHz: Double, dtSeconds: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoffHz)
        return 1.0 / (1.0 + tau / dtSeconds)
    }

    private fun lowPass(x: Double, prev: Double, a: Double) = a * x + (1.0 - a) * prev
}
