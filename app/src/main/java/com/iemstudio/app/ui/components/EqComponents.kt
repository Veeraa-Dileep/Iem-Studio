package com.iemstudio.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.iemstudio.app.data.model.EQ_BAND_COUNT
import com.iemstudio.app.data.model.EQ_MAX_DB
import com.iemstudio.app.data.model.EQ_MIN_DB
import com.iemstudio.app.ui.theme.Amber
import com.iemstudio.app.ui.theme.InfoBlue
import com.iemstudio.app.ui.theme.TextSecondary

/**
 * Frequency-response curve for the current EQ gains: grid, filled area,
 * smoothed curve and band dots. Sliders are drawn on top at the same
 * x-positions (one weighted column per band), so knobs align with the curve.
 */
@Composable
fun EqCurve(gains: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val padY = 28.dp.toPx()

        fun yOf(gain: Float): Float {
            val t = ((gain - EQ_MIN_DB) / (EQ_MAX_DB - EQ_MIN_DB)).coerceIn(0f, 1f)
            return h - padY - t * (h - 2 * padY)
        }

        fun xOf(band: Int): Float = (band + 0.5f) * w / EQ_BAND_COUNT

        // horizontal dB gridlines
        listOf(-12f, -6f, 0f, 6f, 12f).forEach { g ->
            val y = yOf(g)
            val isZero = g == 0f
            drawLine(
                color = if (isZero) Amber.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.06f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = if (isZero) 1.5f else 1f,
            )
        }

        // vertical band guides
        repeat(EQ_BAND_COUNT) { i ->
            val x = xOf(i)
            drawLine(
                color = Color.White.copy(alpha = 0.04f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1f,
            )
        }

        val points = gains.mapIndexed { i, g -> Offset(xOf(i), yOf(g)) }

        // area fill under the curve
        val fillPath = Path().apply {
            moveTo(points.first().x, h)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, h)
            close()
        }
        drawPath(fillPath, color = Amber.copy(alpha = 0.08f))

        // smoothed curve (midpoint cubic splines)
        val curve = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val midX = (p0.x + p1.x) / 2f
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }
        drawPath(curve, color = Amber, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

        // band dots
        points.forEach {
            drawCircle(color = Amber, radius = 3.5.dp.toPx(), center = it)
        }
    }
}

/**
 * One vertical EQ band: drag up/down to change gain (-12..+12 dB, 0.5 steps).
 * Positive boosts draw amber, cuts draw blue, from the 0 dB line.
 */
@Composable
fun EqBandSlider(
    bandIndex: Int,
    gainDb: Float,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(bandIndex) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val range = EQ_MAX_DB - EQ_MIN_DB
                    val dbPerPx = range / size.height
                    onGainChange(gainDb - dragAmount.y * dbPerPx)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val padY = 28.dp.toPx()
            val x = w / 2f

            fun yOf(gain: Float): Float {
                val t = ((gain - EQ_MIN_DB) / (EQ_MAX_DB - EQ_MIN_DB)).coerceIn(0f, 1f)
                return h - padY - t * (h - 2 * padY)
            }

            // track
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1f,
            )

            val knobY = yOf(gainDb)
            if (gainDb != 0f) {
                val zeroY = yOf(0f)
                drawLine(
                    color = if (gainDb > 0f) Amber else InfoBlue,
                    start = Offset(x, zeroY),
                    end = Offset(x, knobY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(
                color = if (gainDb == 0f) TextSecondary else Amber,
                radius = 4.5.dp.toPx(),
                center = Offset(x, knobY),
            )
        }
    }
}
