package it.traccia.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

fun NavGraphBuilder.settingsScreen(
    onBack: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onOpenHeatmap: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(onBack = onBack, onOpenOfflineMaps = onOpenOfflineMaps, onOpenHeatmap = onOpenHeatmap)
    }
}
