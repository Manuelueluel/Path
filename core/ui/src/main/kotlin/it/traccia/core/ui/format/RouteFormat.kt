package it.traccia.core.ui.format

import androidx.compose.runtime.staticCompositionLocalOf
import it.traccia.core.common.UnitSystem
import it.traccia.core.common.UnitSystem.Companion.METERS_PER_FOOT
import it.traccia.core.common.UnitSystem.Companion.METERS_PER_KM
import it.traccia.core.common.UnitSystem.Companion.METERS_PER_MILE
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val SHORT_DISTANCE = 10.0
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

/** Units chosen in the settings, provided at the root of the UI (M8.1). */
val LocalUnitSystem = staticCompositionLocalOf { UnitSystem.METRIC }

/** "850 m", "4,25 km", "18,4 km"; "900 ft", "2,64 mi", "11,4 mi". */
fun formatRouteDistance(
    meters: Double,
    units: UnitSystem = UnitSystem.METRIC,
    locale: Locale = Locale.getDefault(),
): String {
    val u =
        when (units) {
            UnitSystem.METRIC -> Units(METERS_PER_KM, 1.0, "km", "m", smallBelow = 1.0)
            // Below 0.2 mi (≈ 1000 ft) as feet.
            UnitSystem.IMPERIAL -> Units(METERS_PER_MILE, METERS_PER_FOOT, "mi", "ft", smallBelow = SHORT_MILES)
        }
    val value = meters / u.perLarge
    return when {
        value < u.smallBelow -> "${(meters / u.perSmall).roundToLong()} ${u.small}"
        value < SHORT_DISTANCE -> String.format(locale, "%.2f %s", value, u.large)
        else -> String.format(locale, "%.1f %s", value, u.large)
    }
}

/** Elevation or climbing: "1234 m", "4049 ft". */
fun formatElevation(
    meters: Double,
    units: UnitSystem = UnitSystem.METRIC,
): String =
    when (units) {
        UnitSystem.METRIC -> "${meters.roundToInt()} m"
        UnitSystem.IMPERIAL -> "${(meters / METERS_PER_FOOT).roundToInt()} ft"
    }

/** "45 min", "3 h 05 min". */
fun formatDuration(seconds: Double): String {
    val minutes = (seconds / SECONDS_PER_MINUTE).roundToInt()
    val h = minutes / MINUTES_PER_HOUR
    val m = minutes % MINUTES_PER_HOUR
    return if (h == 0) "$m min" else "$h h %02d min".format(m)
}

private const val SHORT_MILES = 0.2

private data class Units(
    val perLarge: Double,
    val perSmall: Double,
    val large: String,
    val small: String,
    /** Large-unit value below which the small unit is used. */
    val smallBelow: Double,
)
