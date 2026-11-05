package com.physioar.engine.signal

/**
 * Velocity estimated as the slope between the newest sample and the oldest sample within
 * [windowMs]. Far less sensitive to per-frame landmark jitter than a frame-to-frame
 * difference, while still responding to genuinely fast (ballistic) movement.
 */
class WindowedVelocity(private val windowMs: Long = 200) {
    private val times = ArrayDeque<Long>()
    private val values = ArrayDeque<Double>()

    /** Adds a sample and returns the velocity in units per second (0 until the window has span). */
    fun update(value: Double, timestampMs: Long): Double {
        if (times.isNotEmpty() && timestampMs <= times.last()) reset()
        times.addLast(timestampMs)
        values.addLast(value)
        while (times.size > 2 && timestampMs - times[1] >= windowMs) {
            times.removeFirst(); values.removeFirst()
        }
        val span = timestampMs - times.first()
        return if (span <= 0) 0.0 else (value - values.first()) * 1000.0 / span
    }

    fun reset() {
        times.clear(); values.clear()
    }
}
