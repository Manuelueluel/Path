package it.traccia.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import it.traccia.feature.map.MapRoute
import it.traccia.feature.map.mapScreen
import it.traccia.feature.settings.SettingsRoute
import it.traccia.feature.settings.settingsScreen

/** App navigation graph: the map is the home screen. */
@Composable
fun TracciaNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = MapRoute) {
        mapScreen(onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } })
        settingsScreen(onBack = { navController.popBackStack() })
    }
}
