package it.traccia.feature.map.ui

import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.traccia.feature.heatmap.session.SessionState
import it.traccia.feature.heatmap.ui.heatmapColorLabel
import it.traccia.feature.heatmap.ui.heatmapSportLabel
import it.traccia.feature.map.HeatmapUi
import it.traccia.feature.map.MapEvent
import it.traccia.feature.map.R

/** Heatmap part of the layer sheet: on/off, sport, color and opacity (M3.5, M3.6). */
@Composable
internal fun HeatmapSection(
    heatmap: HeatmapUi,
    onEvent: (MapEvent) -> Unit,
    onOpenStravaLogin: () -> Unit,
) {
    val settings = heatmap.settings
    LayerSwitch(stringResource(R.string.map_layer_heatmap), settings.visible) { onEvent(MapEvent.HeatmapToggled(it)) }
    if (!settings.visible) return
    if (heatmap.session !is SessionState.Active) {
        Text(
            stringResource(
                if (heatmap.session ==
                    SessionState.Expired
                ) {
                    R.string.map_heatmap_expired
                } else {
                    R.string.map_heatmap_login_needed
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = onOpenStravaLogin) { Text(stringResource(R.string.map_heatmap_login)) }
    }
    ChipRow(heatmap.sports, settings.sport, { heatmapSportLabel(it) }) { onEvent(MapEvent.HeatmapSportSelected(it)) }
    ChipRow(heatmap.colors, settings.color, { heatmapColorLabel(it) }) { onEvent(MapEvent.HeatmapColorSelected(it)) }
    Text(stringResource(R.string.map_heatmap_opacity), style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = settings.opacity,
        onValueChange = { onEvent(MapEvent.HeatmapOpacityChanged(it)) },
        valueRange = MIN_OPACITY..1f,
    )
}

@Composable
private fun ChipRow(
    options: List<String>,
    selected: String,
    label: (String) -> Int?,
    onSelect: (String) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)?.let { stringResource(it) } ?: option) },
            )
        }
    }
}

/**
 * Small non-blocking indicator on the map while the heatmap is on: data age with a refresh action,
 * or a login suggestion when the session is missing or expired (M3.9, M3.10, M3.11).
 */
@Composable
internal fun HeatmapStatusChip(
    heatmap: HeatmapUi,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = CHIP_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            when (heatmap.session) {
                is SessionState.Active -> {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(stringResource(R.string.map_heatmap_label), style = MaterialTheme.typography.labelMedium)
                        Text(
                            heatmap.oldestTileMillis?.let {
                                stringResource(
                                    R.string.map_heatmap_age,
                                    DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS),
                                )
                            } ?: stringResource(R.string.map_heatmap_age_unknown),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.map_heatmap_refresh))
                    }
                }
                SessionState.Expired, SessionState.LoggedOut -> {
                    Text(
                        stringResource(
                            if (heatmap.session ==
                                SessionState.Expired
                            ) {
                                R.string.map_heatmap_chip_expired
                            } else {
                                R.string.map_heatmap_chip_logged_out
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                    TextButton(onClick = onLogin) { Text(stringResource(R.string.map_heatmap_login_short), maxLines = 1, softWrap = false) }
                }
            }
        }
    }
}

private const val MIN_OPACITY = 0.2f
private const val CHIP_ALPHA = 0.92f
