package it.traccia.core.geo

import it.traccia.core.common.UnitSystem
import it.traccia.core.common.UnitSystem.Companion.METERS_PER_FOOT
import it.traccia.core.common.UnitSystem.Companion.METERS_PER_KM
import it.traccia.core.common.UnitSystem.Companion.METERS_PER_MILE
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Scale bar labels: "500 m", "2 km", "1,5 km"; "500 ft", "2 mi" (decimal separator from [locale]). */
fun formatDistance(
    meters: Double,
    units: UnitSystem = UnitSystem.METRIC,
    locale: Locale = Locale.getDefault(),
): String =
    when (units) {
        UnitSystem.METRIC -> round(meters, METERS_PER_KM, "m", "km", locale)
        UnitSystem.IMPERIAL ->
            if (meters < METERS_PER_MILE) {
                "${(meters / METERS_PER_FOOT).roundToLong()} ft"
            } else {
                round(meters / METERS_PER_MILE, 1.0, "mi", "mi", locale, small = false)
            }
    }

private fun round(
    value: Double,
    perLarge: Double,
    smallUnit: String,
    largeUnit: String,
    locale: Locale,
    small: Boolean = true,
): String {
    val large = value / perLarge
    return when {
        small && value < perLarge -> "${value.roundToLong()} $smallUnit"
        abs(large - large.roundToLong()) < ROUND_EPSILON -> "${large.roundToLong()} $largeUnit"
        else -> String.format(locale, "%.1f %s", large, largeUnit)
    }
}

private const val ROUND_EPSILON = 1e-6

/** "45,86012° N  9,47011° E": 5 decimals ≈ 1 m. */
fun formatCoordinates(
    latitude: Double,
    longitude: Double,
    locale: Locale = Locale.getDefault(),
): String {
    val ns = if (latitude >= 0) "N" else "S"
    val ew = if (longitude >= 0) "E" else "W"
    return String.format(locale, "%.5f° %s  %.5f° %s", abs(latitude), ns, abs(longitude), ew)
}
