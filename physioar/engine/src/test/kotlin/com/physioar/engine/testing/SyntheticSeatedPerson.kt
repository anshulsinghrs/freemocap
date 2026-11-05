package com.physioar.engine.testing

import com.physioar.engine.pose.BodyLandmark
import com.physioar.engine.pose.BodySide
import com.physioar.engine.pose.Landmark
import com.physioar.engine.pose.PoseFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates SYNTHETIC landmark sequences of a person seated side-on to the camera doing
 * knee extensions. These exercise the engine's logic with known ground truth; they are
 * not a substitute for validation on recorded patient video.
 */
data class BodyState(
    val kneeAngleDeg: Double,
    /** Trunk lean backwards from vertical, degrees. */
    val trunkLeanDeg: Double = 0.0,
    /** Upward hip displacement in pixels. */
    val hipLiftPx: Double = 0.0,
    val visibility: Float = 0.99f,
    /** If true the person faces the camera (frontal) instead of side-on. */
    val frontal: Boolean = false,
)

data class Segment(val durationMs: Long, val from: BodyState, val to: BodyState)

class SyntheticSeatedPerson(
    private val side: BodySide = BodySide.RIGHT,
    private val imageWidth: Int = 1000,
    private val imageHeight: Int = 1000,
    private val fps: Int = 30,
    private val kneeNoiseSdDeg: Double = 0.0,
    private val pixelNoiseSd: Double = 0.0,
    seed: Int = 42,
    private val withWorldLandmarks: Boolean = false,
) {
    private val random = Random(seed)
    private val frames = mutableListOf<PoseFrame>()
    private var t = 0L
    private var current = BodyState(90.0)

    fun hold(state: BodyState, durationMs: Long) = apply { move(state, state, durationMs) }
    fun hold(durationMs: Long) = hold(current, durationMs)

    /** Cosine-interpolated movement from the current state to [to]. */
    fun moveTo(to: BodyState, durationMs: Long) = apply { move(current, to, durationMs) }

    /** One textbook repetition: extend, hold, lower, rest. */
    fun repetition(
        peak: Double = 170.0, rest: Double = 90.0, extendMs: Long = 1500, holdMs: Long = 3000,
        returnMs: Long = 2000, restMs: Long = 1000,
    ) = apply {
        moveTo(current.copy(kneeAngleDeg = peak), extendMs)
        hold(holdMs)
        moveTo(current.copy(kneeAngleDeg = rest), returnMs)
        hold(restMs)
    }

    fun frames(): List<PoseFrame> = frames.toList()

    private fun move(from: BodyState, to: BodyState, durationMs: Long) {
        val step = 1000L / fps
        var elapsed = 0L
        while (elapsed < durationMs) {
            val u = elapsed.toDouble() / durationMs
            val s = (1 - cos(PI * u)) / 2 // ease in-out
            frames += render(lerp(from, to, s, u), t)
            t += step
            elapsed += step
        }
        current = to
    }

    private fun lerp(a: BodyState, b: BodyState, s: Double, u: Double) = BodyState(
        kneeAngleDeg = a.kneeAngleDeg + (b.kneeAngleDeg - a.kneeAngleDeg) * s,
        trunkLeanDeg = a.trunkLeanDeg + (b.trunkLeanDeg - a.trunkLeanDeg) * s,
        hipLiftPx = a.hipLiftPx + (b.hipLiftPx - a.hipLiftPx) * s,
        visibility = if (u < 0.5) a.visibility else b.visibility,
        frontal = if (u < 0.5) a.frontal else b.frontal,
    )

    private fun render(st: BodyState, timestampMs: Long): PoseFrame {
        val lm = MutableList(BodyLandmark.COUNT) { Landmark(0.5f, 0.3f, 0f, 0.99f, 0.99f) }
        val px = mutableMapOf<BodyLandmark, Pair<Double, Double>>()

        val hip = 400.0 to 600.0 - st.hipLiftPx
        val knee = 600.0 to 600.0
        val theta = st.kneeAngleDeg + kneeNoiseSdDeg * random.nextGaussian()
        val phi = Math.toRadians(theta - 90.0)
        val ankle = knee.first + 200 * sin(phi) to knee.second + 200 * cos(phi)
        val lean = Math.toRadians(st.trunkLeanDeg)
        val shoulder = hip.first - 300 * sin(lean) to hip.second - 300 * cos(lean)
        val spread = if (st.frontal) 250.0 else 10.0

        px[side.hip()] = hip
        px[side.knee()] = knee
        px[side.ankle()] = ankle
        px[side.shoulder()] = shoulder
        px[side.opposite.hip()] = hip.first + spread * 0.6 to hip.second
        px[side.opposite.shoulder()] = shoulder.first + spread to shoulder.second
        px[side.opposite.knee()] = knee.first + spread * 0.6 to knee.second
        px[side.opposite.ankle()] = knee.first + spread * 0.6 to knee.second + 200

        for ((landmark, p) in px) {
            val vis = if (landmark in setOf(side.hip(), side.knee(), side.ankle(), side.shoulder())) st.visibility else 0.3f
            lm[landmark.index] = Landmark(
                ((p.first + pixelNoiseSd * random.nextGaussian()) / imageWidth).toFloat(),
                ((p.second + pixelNoiseSd * random.nextGaussian()) / imageHeight).toFloat(),
                0f, vis, 0.99f,
            )
        }
        val world = if (withWorldLandmarks) {
            // Metric copy (1 px = 1 mm) — consistent with the 2D projection for a perpendicular camera.
            lm.mapIndexed { i, l ->
                val p = px[BodyLandmark.fromIndex(i)!!]
                if (p == null) Landmark(0f, 0f, 0f) else Landmark((p.first / 1000).toFloat(), (p.second / 1000).toFloat(), 0f)
            }
        } else null
        return PoseFrame(timestampMs, lm, world, imageWidth, imageHeight)
    }

    private fun Random.nextGaussian(): Double {
        // Box–Muller.
        val u1 = nextDouble().coerceAtLeast(1e-12)
        val u2 = nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * cos(2 * PI * u2)
    }
}
