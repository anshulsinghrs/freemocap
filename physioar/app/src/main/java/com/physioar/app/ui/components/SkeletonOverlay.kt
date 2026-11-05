package com.physioar.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.runtime.Composable
import com.physioar.engine.pose.BodyLandmark
import com.physioar.engine.pose.BodyLandmark.*
import com.physioar.engine.pose.BodySide
import com.physioar.engine.pose.PoseFrame
import kotlin.math.max

/** Body connections drawn for the skeleton (subset of MediaPipe POSE_CONNECTIONS, no face). */
private val CONNECTIONS = listOf(
    LEFT_SHOULDER to RIGHT_SHOULDER, LEFT_HIP to RIGHT_HIP,
    LEFT_SHOULDER to LEFT_HIP, RIGHT_SHOULDER to RIGHT_HIP,
    LEFT_SHOULDER to LEFT_ELBOW, LEFT_ELBOW to LEFT_WRIST,
    RIGHT_SHOULDER to RIGHT_ELBOW, RIGHT_ELBOW to RIGHT_WRIST,
    LEFT_HIP to LEFT_KNEE, LEFT_KNEE to LEFT_ANKLE, LEFT_ANKLE to LEFT_HEEL, LEFT_HEEL to LEFT_FOOT_INDEX,
    RIGHT_HIP to RIGHT_KNEE, RIGHT_KNEE to RIGHT_ANKLE, RIGHT_ANKLE to RIGHT_HEEL, RIGHT_HEEL to RIGHT_FOOT_INDEX,
)

/**
 * Draws the skeleton over a FILL_CENTER camera preview. Landmarks are in the (unmirrored)
 * inference image; for the front camera they are mirrored here so they line up with the
 * mirrored preview.
 */
@Composable
fun SkeletonOverlay(
    pose: PoseFrame?,
    mirror: Boolean,
    highlightSide: BodySide,
    highlightColor: Color,
    kneeAngleDeg: Double?,
    modifier: Modifier = Modifier,
    minVisibility: Float = 0.5f,
) {
    Canvas(modifier) {
        val p = pose ?: return@Canvas
        val scale = max(size.width / p.imageWidth, size.height / p.imageHeight)
        val dx = (size.width - p.imageWidth * scale) / 2f
        val dy = (size.height - p.imageHeight * scale) / 2f
        fun pt(lm: BodyLandmark): Offset {
            val l = p[lm]
            val x = (if (mirror) 1f - l.x else l.x) * p.imageWidth * scale + dx
            return Offset(x, l.y * p.imageHeight * scale + dy)
        }
        fun visible(lm: BodyLandmark) = (p[lm].visibility ?: 1f) >= minVisibility

        val highlighted = setOf(highlightSide.hip(), highlightSide.knee(), highlightSide.ankle())
        for ((a, b) in CONNECTIONS) {
            if (!visible(a) || !visible(b)) continue
            val hl = a in highlighted && b in highlighted
            drawLine(
                color = if (hl) highlightColor else Color.White.copy(alpha = 0.8f),
                start = pt(a), end = pt(b),
                strokeWidth = if (hl) 12f else 6f, cap = StrokeCap.Round,
            )
        }
        for (lm in BodyLandmark.entries) {
            if (lm.index in 1..10 || !visible(lm)) continue // skip face detail
            drawCircle(if (lm in highlighted) highlightColor else Color(0xFFFFEB3B), radius = 9f, center = pt(lm))
        }
        if (kneeAngleDeg != null && visible(highlightSide.knee())) {
            drawLabel("${kneeAngleDeg.toInt()}°", pt(highlightSide.knee()) + Offset(24f, -24f))
        }
    }
}

private fun DrawScope.drawLabel(text: String, at: Offset) {
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 56f
        isAntiAlias = true
        setShadowLayer(6f, 0f, 0f, android.graphics.Color.BLACK)
    }
    drawContext.canvas.nativeCanvas.drawText(text, at.x, at.y, paint)
}
