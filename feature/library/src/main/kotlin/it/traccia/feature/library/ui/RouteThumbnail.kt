package it.traccia.feature.library.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import it.traccia.core.geo.GeoPoint
import kotlin.math.cos
import kotlin.math.max

/** Shape of the route, fitted in a square (M7.6). No map tiles: instant and offline. */
@Composable
fun RouteThumbnail(
    preview: List<GeoPoint>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.tertiary
    val startColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier
            .size(THUMB_SIZE.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (preview.size < 2) return@Canvas
        val latScale = 1.0
        val lonScale = cos(Math.toRadians(preview.first().latitude))
        val minX = preview.minOf { it.longitude * lonScale }
        val maxX = preview.maxOf { it.longitude * lonScale }
        val minY = preview.minOf { it.latitude * latScale }
        val maxY = preview.maxOf { it.latitude * latScale }
        val span = max(maxX - minX, maxY - minY).takeIf { it > 0 } ?: return@Canvas
        val pad = PADDING.dp.toPx()
        val side = size.minDimension - 2 * pad
        // Centred, north up, same scale on both axes.
        val offsetX = pad + (side - (maxX - minX) / span * side) / 2
        val offsetY = pad + (side - (maxY - minY) / span * side) / 2

        fun at(p: GeoPoint) =
            Offset(
                (offsetX + (p.longitude * lonScale - minX) / span * side).toFloat(),
                (offsetY + (maxY - p.latitude * latScale) / span * side).toFloat(),
            )
        val path = Path()
        preview.forEachIndexed { i, p -> at(p).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
        drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(startColor, radius = 3.dp.toPx(), center = at(preview.first()))
    }
}

private const val THUMB_SIZE = 56
private const val PADDING = 6
