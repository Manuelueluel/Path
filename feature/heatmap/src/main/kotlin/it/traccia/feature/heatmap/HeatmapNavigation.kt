package it.traccia.feature.heatmap

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import it.traccia.feature.heatmap.ui.HeatmapAccountScreen
import it.traccia.feature.heatmap.ui.StravaLoginScreen
import kotlinx.serialization.Serializable

@Serializable
data object HeatmapAccountRoute

@Serializable
data object StravaLoginRoute

fun NavGraphBuilder.heatmapScreens(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    composable<HeatmapAccountRoute> { HeatmapAccountScreen(onBack = onBack, onLogin = onOpenLogin) }
    composable<StravaLoginRoute> { StravaLoginScreen(onDone = onBack) }
}
