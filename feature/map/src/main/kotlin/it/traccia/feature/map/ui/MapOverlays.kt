package it.traccia.feature.map.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.traccia.core.geo.ScaleBarSpec
import it.traccia.core.geo.formatCoordinates
import it.traccia.core.geo.formatDistance
import it.traccia.feature.map.CenterInfo
import it.traccia.feature.map.R
import kotlin.math.roundToInt

/** Dark ink with a white halo: readable on any map style. */
private val MarkInk = Color(0xFF2B2B2B)
private const val READOUT_ALPHA = 0.88f

/** Thin cross marking the point whose coordinates and elevation are shown. */
@Composable
internal fun Crosshair(modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val c = size.width / 2
        listOf(Color.White to 4.dp.toPx(), MarkInk to 1.6.dp.toPx()).forEach { (color, width) ->
            drawLine(color, Offset(c, 0f), Offset(c, size.height), width, StrokeCap.Round)
            drawLine(color, Offset(0f, c), Offset(size.width, c), width, StrokeCap.Round)
        }
    }
}

/** Graphic scale bar with its distance label. */
@Composable
internal fun ScaleBar(
    spec: ScaleBarSpec,
    modifier: Modifier = Modifier,
) {
    val label = formatDistance(spec.meters)
    Column(modifier.semantics { contentDescription = label }) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MarkInk)
        Canvas(Modifier.width(spec.widthDp.dp).size(width = spec.widthDp.dp, height = 6.dp)) {
            val y = size.height - 1.dp.toPx()
            listOf(Color.White to 4.dp.toPx(), MarkInk to 1.6.dp.toPx()).forEach { (color, w) ->
                drawLine(color, Offset(0f, y), Offset(size.width, y), w, StrokeCap.Square)
                drawLine(color, Offset(0f, 0f), Offset(0f, y), w, StrokeCap.Square)
                drawLine(color, Offset(size.width, 0f), Offset(size.width, y), w, StrokeCap.Square)
            }
        }
    }
}

/** Coordinates and elevation of the map center. */
@Composable
internal fun CenterReadout(
    center: CenterInfo,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = READOUT_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            val elevation = center.elevation
            Text(
                text =
                    if (elevation != null) {
                        stringResource(R.string.map_elevation_meters, elevation.roundToInt())
                    } else {
                        stringResource(R.string.map_elevation_unknown)
                    },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatCoordinates(center.latitude, center.longitude),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
