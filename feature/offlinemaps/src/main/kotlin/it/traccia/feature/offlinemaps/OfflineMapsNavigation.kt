package it.traccia.feature.offlinemaps

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object OfflineMapsRoute

fun NavGraphBuilder.offlineMapsScreen(onBack: () -> Unit) {
    composable<OfflineMapsRoute> {
        OfflineMapsScreen(onBack = onBack)
    }
}
