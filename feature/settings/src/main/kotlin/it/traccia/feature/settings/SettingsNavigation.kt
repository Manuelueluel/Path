package it.traccia.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import it.traccia.feature.settings.about.AboutScreen
import it.traccia.feature.settings.onboarding.OnboardingExit
import it.traccia.feature.settings.onboarding.OnboardingScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

@Serializable
data object AboutRoute

@Serializable
data object OnboardingRoute

fun NavGraphBuilder.settingsScreen(
    onBack: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onOpenHeatmap: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(onBack = onBack, onOpenOfflineMaps = onOpenOfflineMaps, onOpenHeatmap = onOpenHeatmap, onOpenAbout = onOpenAbout)
    }
}

fun NavGraphBuilder.aboutScreen(
    versionName: String,
    onBack: () -> Unit,
) {
    composable<AboutRoute> { AboutScreen(versionName = versionName, onBack = onBack) }
}

fun NavGraphBuilder.onboardingScreen(onExit: (OnboardingExit) -> Unit) {
    composable<OnboardingRoute> { OnboardingScreen(onExit = onExit) }
}
