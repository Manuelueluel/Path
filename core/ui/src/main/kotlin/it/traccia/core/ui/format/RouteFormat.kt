package it.traccia.core.ui.format

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val METERS_PER_KM = 1000.0
private const val SHORT_KM = 10.0
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

/** "850 m", "4,25 km", "18,4 km". */
fun formatRouteDistance(
    meters: Double,
    locale: Locale = Locale.getDefault(),
): String =
    when {
        meters < METERS_PER_KM -> "${meters.roundToLong()} m"
        meters < SHORT_KM * METERS_PER_KM -> String.format(locale, "%.2f km", meters / METERS_PER_KM)
        else -> String.format(locale, "%.1f km", meters / METERS_PER_KM)
    }

/** "45 min", "3 h 05 min". */
fun formatDuration(seconds: Double): String {
    val minutes = (seconds / SECONDS_PER_MINUTE).roundToInt()
    val h = minutes / MINUTES_PER_HOUR
    val m = minutes % MINUTES_PER_HOUR
    return if (h == 0) "$m min" else "$h h %02d min".format(m)
}
