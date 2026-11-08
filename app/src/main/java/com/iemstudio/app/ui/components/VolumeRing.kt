package com.iemstudio.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iemstudio.app.ui.theme.Amber
import com.iemstudio.app.ui.theme.Danger
import com.iemstudio.app.ui.theme.Surface
import com.iemstudio.app.ui.theme.SurfaceHigh
import com.iemstudio.app.ui.theme.TextPrimary
import com.iemstudio.app.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Circular volume dial. Drag anywhere on the ring: up/right increases,
 * down/left decreases. Shows the level and mute state in the center.
 */
@Composable
fun VolumeRing(
    volume: Int,
    muted: Boolean,
    onVolumeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 232.dp,
) {
    val stroke = 20.dp
    val progress = (volume / 100f).coerceIn(0f, 1f)

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(volume) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // drag up or right = louder
                        val delta = (-dragAmount.y + dragAmount.x) / 2.2f
                        onVolumeChange(volume + delta.roundToInt())
                    }
                }
        ) {
            val strokePx = stroke.toPx()
            val radius = (size.toPx() - strokePx) / 2f
            val center = this.center
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            // track
            drawCircle(
                color = SurfaceHigh,
                radius = radius,
                center = center,
                style = Stroke(width = strokePx),
            )

            // progress arc + knob
            if (progress > 0f) {
                drawArc(
                    color = if (muted) TextSecondary else Amber,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
                val angle = Math.toRadians(-90.0 + 360.0 * progress)
                val knob = Offset(
                    center.x + radius * cos(angle).toFloat(),
                    center.y + radius * sin(angle).toFloat(),
                )
                drawCircle(color = Surface, radius = strokePx / 2f + 3.dp.toPx(), center = knob)
                drawCircle(color = Amber, radius = strokePx / 2f, center = knob)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (muted) "—" else "$volume",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text("%", color = TextSecondary, fontSize = 13.sp)
            if (muted) {
                Text(
                    "MUTED",
                    color = Danger,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp,
                )
            }
        }
    }
}
