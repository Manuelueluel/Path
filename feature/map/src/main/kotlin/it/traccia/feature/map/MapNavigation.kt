package it.traccia.feature.map

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object MapRoute

fun NavGraphBuilder.mapScreen(onOpenSettings: () -> Unit) {
    composable<MapRoute> {
        MapScreen(onOpenSettings = onOpenSettings)
    }
}
