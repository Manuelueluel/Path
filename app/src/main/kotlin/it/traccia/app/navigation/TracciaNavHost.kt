package it.traccia.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import it.traccia.feature.heatmap.HeatmapAccountRoute
import it.traccia.feature.heatmap.StravaLoginRoute
import it.traccia.feature.heatmap.heatmapScreens
import it.traccia.feature.library.LibraryRoute
import it.traccia.feature.library.libraryScreen
import it.traccia.feature.map.MapRoute
import it.traccia.feature.map.mapScreen
import it.traccia.feature.offlinemaps.OfflineMapsRoute
import it.traccia.feature.offlinemaps.offlineMapsScreen
import it.traccia.feature.settings.SettingsRoute
import it.traccia.feature.settings.settingsScreen

/** App navigation graph: the map is the home screen. */
@Composable
fun TracciaNavHost() {
    val navController = rememberNavController()
    val openOfflineMaps = { navController.navigate(OfflineMapsRoute) { launchSingleTop = true } }
    val openStravaLogin = { navController.navigate(StravaLoginRoute) { launchSingleTop = true } }
    NavHost(navController = navController, startDestination = MapRoute) {
        mapScreen(
            onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
            onOpenOfflineMaps = openOfflineMaps,
            onOpenStravaLogin = openStravaLogin,
            onOpenLibrary = { navController.navigate(LibraryRoute) { launchSingleTop = true } },
        )
        settingsScreen(
            onBack = { navController.popBackStack() },
            onOpenOfflineMaps = openOfflineMaps,
            onOpenHeatmap = { navController.navigate(HeatmapAccountRoute) { launchSingleTop = true } },
        )
        offlineMapsScreen(onBack = { navController.popBackStack() })
        heatmapScreens(onBack = { navController.popBackStack() }, onOpenLogin = openStravaLogin)
        libraryScreen(onBack = { navController.popBackStack() })
    }
}
