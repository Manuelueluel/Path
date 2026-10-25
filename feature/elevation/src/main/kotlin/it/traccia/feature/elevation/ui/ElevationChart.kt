package it.traccia.feature.elevation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.traccia.feature.elevation.ElevationProfile
import it.traccia.feature.elevation.SlopeClass
import kotlin.math.roundToInt

/**
 * Elevation profile chart (M6.3): area coloured by gradient (M6.5), a marker at [highlightMeters],
 * and scrubbing that reports the distance under the finger (M6.4).
 */
@Composable
fun ElevationChart(
    profile: ElevationProfile,
    highlightMeters: Double?,
    onScrub: (Double?) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val markerColor = MaterialTheme.colorScheme.onSurface
    val grades = remember(profile) { profile.grades() }
    val length = profile.lengthMeters.takeIf { it > 0 } ?: 1.0
    Canvas(
        modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT.dp)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(profile) {
                detectTapGestures { onScrub(it.x / size.width * length) }
            }.pointerInput(profile) {
                detectDragGestures(
                    onDragEnd = { onScrub(null) },
                    onDragCancel = { onScrub(null) },
                ) { change, _ -> onScrub((change.position.x / size.width).coerceIn(0f, 1f) * length) }
            },
    ) {
        val labelWidth = LABEL_WIDTH.dp.toPx()
        val chartWidth = size.width - labelWidth
        val top = PADDING.dp.toPx()
        val bottom = size.height - PADDING.dp.toPx()
        val range = (profile.maxElevation - profile.minElevation).coerceAtLeast(MIN_RANGE_M)
        val minY = profile.minElevation - range * MARGIN
        val maxY = profile.maxElevation + range * MARGIN

        fun x(d: Double) = (d / length * chartWidth).toFloat()

        fun y(e: Double) = (bottom - (e - minY) / (maxY - minY) * (bottom - top)).toFloat()

        // Filled area, one slice per sample coloured by its gradient.
        profile.points.zipWithNext().forEachIndexed { i, (a, b) ->
            val slice =
                Path().apply {
                    moveTo(x(a.distanceMeters), bottom)
                    lineTo(x(a.distanceMeters), y(a.elevationMeters))
                    lineTo(x(b.distanceMeters), y(b.elevationMeters))
                    lineTo(x(b.distanceMeters), bottom)
                    close()
                }
            drawPath(slice, Color(SlopeClass.of(grades[i]).color).copy(alpha = FILL_ALPHA))
        }
        val outline = Path()
        profile.points.forEachIndexed { i, p ->
            if (i ==
                0
            ) {
                outline.moveTo(x(p.distanceMeters), y(p.elevationMeters))
            } else {
                outline.lineTo(x(p.distanceMeters), y(p.elevationMeters))
            }
        }
        drawPath(outline, axisColor, style = Stroke(width = 1.5.dp.toPx()))

        drawLabel(
            textMeasurer,
            "${profile.maxElevation.roundToInt()} m",
            Offset(
                chartWidth + 4.dp.toPx(),
                y(profile.maxElevation) - 6.dp.toPx(),
            ),
            axisColor,
        )
        drawLabel(
            textMeasurer,
            "${profile.minElevation.roundToInt()} m",
            Offset(
                chartWidth + 4.dp.toPx(),
                y(profile.minElevation) - 6.dp.toPx(),
            ),
            axisColor,
        )

        highlightMeters?.let { d ->
            val point = profile.at(d) ?: return@let
            val px = x(point.distanceMeters)
            drawLine(markerColor, Offset(px, top), Offset(px, bottom), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(markerColor, radius = 4.dp.toPx(), center = Offset(px, y(point.elevationMeters)))
        }
    }
}

private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    at: Offset,
    color: Color,
) {
    drawText(measurer, text, at, style = TextStyle(color = color, fontSize = LABEL_SP.sp))
}

private const val CHART_HEIGHT = 120
private const val LABEL_WIDTH = 48
private const val PADDING = 8
private const val MIN_RANGE_M = 20.0
private const val MARGIN = 0.1
private const val FILL_ALPHA = 0.55f
private const val LABEL_SP = 10
