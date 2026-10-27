package it.traccia.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import it.traccia.app.BuildConfig
import it.traccia.feature.heatmap.HeatmapAccountRoute
import it.traccia.feature.heatmap.StravaLoginRoute
import it.traccia.feature.heatmap.heatmapScreens
import it.traccia.feature.library.LibraryRoute
import it.traccia.feature.library.libraryScreen
import it.traccia.feature.map.MapRoute
import it.traccia.feature.map.mapScreen
import it.traccia.feature.offlinemaps.OfflineMapsRoute
import it.traccia.feature.offlinemaps.offlineMapsScreen
import it.traccia.feature.settings.AboutRoute
import it.traccia.feature.settings.OnboardingRoute
import it.traccia.feature.settings.SettingsRoute
import it.traccia.feature.settings.aboutScreen
import it.traccia.feature.settings.onboarding.OnboardingExit
import it.traccia.feature.settings.onboardingScreen
import it.traccia.feature.settings.settingsScreen

/** App navigation graph: the map is the home screen, after a one-time onboarding (M8.3). */
@Composable
fun TracciaNavHost(startWithOnboarding: Boolean) {
    val navController = rememberNavController()
    val openOfflineMaps = { navController.navigate(OfflineMapsRoute) { launchSingleTop = true } }
    val openStravaLogin = { navController.navigate(StravaLoginRoute) { launchSingleTop = true } }
    NavHost(navController = navController, startDestination = if (startWithOnboarding) OnboardingRoute else MapRoute) {
        onboardingScreen { exit ->
            navController.navigate(MapRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
            when (exit) {
                OnboardingExit.MAP -> Unit
                OnboardingExit.OFFLINE_MAPS -> openOfflineMaps()
                OnboardingExit.STRAVA_LOGIN -> openStravaLogin()
            }
        }
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
            onOpenAbout = { navController.navigate(AboutRoute) { launchSingleTop = true } },
        )
        aboutScreen(versionName = BuildConfig.VERSION_NAME, onBack = { navController.popBackStack() })
        offlineMapsScreen(onBack = { navController.popBackStack() })
        heatmapScreens(onBack = { navController.popBackStack() }, onOpenLogin = openStravaLogin)
        libraryScreen(onBack = { navController.popBackStack() })
    }
}
