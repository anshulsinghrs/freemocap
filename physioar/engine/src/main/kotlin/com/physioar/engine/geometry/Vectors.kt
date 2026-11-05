package com.physioar.engine.geometry

import com.physioar.engine.pose.BodyLandmark
import com.physioar.engine.pose.PoseFrame
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

data class Vec2(val x: Double, val y: Double) {
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun times(s: Double) = Vec2(x * s, y * s)
    infix fun dot(o: Vec2) = x * o.x + y * o.y
    fun length() = sqrt(x * x + y * y)
    fun distanceTo(o: Vec2) = (this - o).length()
    companion object {
        fun midpoint(a: Vec2, b: Vec2) = Vec2((a.x + b.x) / 2.0, (a.y + b.y) / 2.0)
    }
}

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun length() = sqrt(x * x + y * y + z * z)
}

private const val EPS = 1e-9

/**
 * Interior angle in degrees at vertex [b] formed by segments b→a and b→c, in [0, 180].
 * Uses arccos of the normalised dot product (same formulation as the audited
 * pose-detection-android AngleUtils, re-implemented here). Returns null when either
 * segment is degenerate, so callers can treat the measurement as unavailable instead
 * of silently using a wrong value.
 */
fun interiorAngleDeg(a: Vec2, b: Vec2, c: Vec2): Double? {
    val ba = a - b
    val bc = c - b
    val la = ba.length()
    val lc = bc.length()
    if (la < EPS || lc < EPS) return null
    val cos = ((ba dot bc) / (la * lc)).coerceIn(-1.0, 1.0)
    return Math.toDegrees(acos(cos))
}

/** 3D version of [interiorAngleDeg], for metric world landmarks. */
fun interiorAngleDeg(a: Vec3, b: Vec3, c: Vec3): Double? {
    val ba = a - b
    val bc = c - b
    val la = ba.length()
    val lc = bc.length()
    if (la < EPS || lc < EPS) return null
    val cos = ((ba dot bc) / (la * lc)).coerceIn(-1.0, 1.0)
    return Math.toDegrees(acos(cos))
}

/**
 * Unsigned angle in degrees between segment from→to and the image vertical, in [0, 180].
 * 0 means `to` is directly above `from` (image y grows downwards).
 */
fun inclinationFromVerticalDeg(from: Vec2, to: Vec2): Double? {
    val d = to - from
    if (d.length() < EPS) return null
    // atan2 of horizontal offset against upward vertical component.
    return abs(Math.toDegrees(atan2(d.x, -d.y)))
}

/**
 * Converts a landmark into isotropic pixel coordinates. Normalised MediaPipe x and y are
 * scaled by different image dimensions, so angles must be measured in pixel space or
 * they are distorted for any non-square image.
 */
fun PoseFrame.pixel(landmark: BodyLandmark): Vec2 {
    val l = this[landmark]
    return Vec2(l.x.toDouble() * imageWidth, l.y.toDouble() * imageHeight)
}

fun PoseFrame.worldPoint(landmark: BodyLandmark): Vec3? =
    world(landmark)?.let { Vec3(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
