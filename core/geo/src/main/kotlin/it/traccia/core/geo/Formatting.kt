package it.traccia.core.geo

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private const val METERS_PER_KM = 1000.0

/** "500 m", "2 km", "1,5 km" (decimal separator from [locale]). */
fun formatDistance(
    meters: Double,
    locale: Locale = Locale.getDefault(),
): String =
    when {
        meters < METERS_PER_KM -> "${meters.roundToLong()} m"
        meters % METERS_PER_KM == 0.0 -> "${(meters / METERS_PER_KM).roundToLong()} km"
        else -> String.format(locale, "%.1f km", meters / METERS_PER_KM)
    }

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
