package it.traccia.feature.planner.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.traccia.core.ui.format.LocalUnitSystem
import it.traccia.core.ui.format.formatDuration
import it.traccia.core.ui.format.formatElevation
import it.traccia.core.ui.format.formatRouteDistance
import it.traccia.feature.elevation.ElevationProfile
import it.traccia.feature.elevation.ui.ElevationChart
import it.traccia.feature.planner.PlannerEvent
import it.traccia.feature.planner.PlannerUiState
import it.traccia.feature.planner.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The only on-screen controls while drawing: live statistics (M5.8) and three buttons
 * (Undo, Chiudi anello, Cancella tutto). The elevation profile opens from here (M6.3).
 */
@Composable
fun PlannerPanel(
    state: PlannerUiState,
    onEvent: (PlannerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = PANEL_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            if (state.isRouting) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 4.dp))
            val stats = state.stats
            if (stats == null) {
                Text(
                    stringResource(if (state.route.isEmpty) R.string.planner_hint_start else R.string.planner_hint_next),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            } else {
                val units = LocalUnitSystem.current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Stat(formatRouteDistance(stats.distanceMeters, units), R.string.planner_stat_distance, Modifier.weight(1f))
                    Stat(stats.ascentMeters?.let { formatElevation(it, units) } ?: "…", R.string.planner_stat_ascent, Modifier.weight(1f))
                    Stat(stats.descentMeters?.let { formatElevation(it, units) } ?: "…", R.string.planner_stat_descent, Modifier.weight(1f))
                    Stat(stats.durationSeconds?.let { formatDuration(it) } ?: "…", R.string.planner_stat_time, Modifier.weight(TIME_WEIGHT))
                    IconButton(onClick = { onEvent(PlannerEvent.ProfileToggled) }, enabled = state.profile != null) {
                        Icon(
                            if (state.profileExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.planner_profile_toggle),
                        )
                    }
                }
            }
            val profile = state.profile
            AnimatedVisibility(state.profileExpanded && profile != null) {
                if (profile != null) ProfileDetails(profile, state.highlightMeters, onEvent)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PlannerButton(R.drawable.ic_undo, R.string.planner_undo, state.canUndo) { onEvent(PlannerEvent.Undo) }
                PlannerButton(
                    R.drawable.ic_close_loop,
                    R.string.planner_close_loop,
                    state.route.canCloseLoop,
                ) { onEvent(PlannerEvent.CloseLoop) }
                PlannerButton(R.drawable.ic_clear_all, R.string.planner_clear_all, state.canClear) { onEvent(PlannerEvent.ClearAll) }
            }
        }
    }
}

@Composable
private fun ProfileDetails(
    profile: ElevationProfile,
    highlight: Double?,
    onEvent: (PlannerEvent) -> Unit,
) {
    val units = LocalUnitSystem.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ElevationChart(
            profile = profile,
            highlightMeters = highlight,
            onScrub = { onEvent(PlannerEvent.ProfileScrubbed(it)) },
            contentDescription =
                stringResource(
                    R.string.planner_profile_description,
                    formatElevation(profile.minElevation, units),
                    formatElevation(profile.maxElevation, units),
                ),
        )
        Text(
            stringResource(
                R.string.planner_profile_summary,
                formatElevation(profile.minElevation, units),
                formatElevation(profile.maxElevation, units),
                profile.maxGradePercent.roundToInt(),
            ),
            style = MaterialTheme.typography.labelMedium,
        )
        profile.steepest.forEach { section ->
            Text(
                stringResource(
                    if (section.gradePercent >= 0) R.string.planner_steep_up else R.string.planner_steep_down,
                    formatRouteDistance(section.startMeters, units),
                    formatRouteDistance(section.endMeters, units),
                    abs(section.gradePercent).roundToInt(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(
    value: String,
    @StringRes label: Int,
    modifier: Modifier = Modifier,
) {
    // Read as one item by TalkBack ("12,4 km, Distanza"); with large fonts the value shrinks instead of being cut.
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = MIN_STAT_SP.sp, maxFontSize = MaterialTheme.typography.titleSmall.fontSize),
        )
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlannerButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(label), modifier = Modifier.padding(start = 6.dp))
    }
}

private const val PANEL_ALPHA = 0.95f
private const val MIN_STAT_SP = 9

/** The duration label ("3 h 05 min") is wider than the others. */
private const val TIME_WEIGHT = 1.2f
