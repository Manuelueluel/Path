package it.traccia.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = ForestGreen,
        onPrimary = Color.White,
        primaryContainer = ForestGreenContainer,
        onPrimaryContainer = Color(0xFF00210C),
        secondary = Slate,
        onSecondary = Color.White,
        tertiary = TrailOrange,
        onTertiary = Color.White,
        tertiaryContainer = TrailOrangeContainer,
        onTertiaryContainer = Color(0xFF351000),
        secondaryContainer = Color(0xFFD1E8D4),
        onSecondaryContainer = Color(0xFF0C1F13),
        background = PaperLight,
        surface = PaperLight,
        surfaceVariant = Color(0xFFDDE5DA),
        onSurfaceVariant = Color(0xFF414941),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF1F5ED),
        surfaceContainer = Color(0xFFEBEFE7),
        surfaceContainerHigh = Color(0xFFE6E9E2),
        surfaceContainerHighest = Color(0xFFE0E4DC),
        outline = Color(0xFF717970),
        outlineVariant = Color(0xFFC1C9BE),
    )

private val DarkColors =
    darkColorScheme(
        primary = ForestGreenLight,
        onPrimary = Color(0xFF00391A),
        primaryContainer = ForestGreenContainerDark,
        onPrimaryContainer = ForestGreenContainer,
        secondary = SlateLight,
        onSecondary = Color(0xFF213527),
        tertiary = TrailOrangeLight,
        onTertiary = Color(0xFF552000),
        tertiaryContainer = TrailOrangeContainerDark,
        onTertiaryContainer = TrailOrangeContainer,
        secondaryContainer = Color(0xFF374B3D),
        onSecondaryContainer = Color(0xFFD1E8D4),
        background = NightDark,
        surface = NightDark,
        surfaceVariant = Color(0xFF414941),
        onSurfaceVariant = Color(0xFFC1C9BE),
        surfaceContainerLowest = Color(0xFF0B0F0B),
        surfaceContainerLow = Color(0xFF181D18),
        surfaceContainer = Color(0xFF1C211C),
        surfaceContainerHigh = Color(0xFF262B26),
        surfaceContainerHighest = Color(0xFF313631),
        outline = Color(0xFF8B9389),
        outlineVariant = Color(0xFF414941),
    )

/** App theme. Follows the system light/dark setting. */
@Composable
fun TracciaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
