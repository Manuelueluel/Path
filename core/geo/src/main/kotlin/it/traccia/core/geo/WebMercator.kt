// Math constants of the projection formulas are clearer inline than as named constants.
@file:Suppress("MagicNumber")

package it.traccia.core.geo

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Spherical Web Mercator (EPSG:3857) in "world pixel" coordinates:
 * at zoom z the world is `tileSize * 2^z` pixels wide, origin at the top-left corner.
 */
object WebMercator {
    /** Max latitude representable in Web Mercator. */
    const val MAX_LATITUDE = 85.05112878

    /** Equatorial circumference in meters (WGS84 semi-major axis). */
    const val EARTH_CIRCUMFERENCE_METERS = 40_075_016.686

    fun worldSize(
        zoom: Double,
        tileSize: Int,
    ): Double = tileSize * 2.0.pow(zoom)

    fun lonToX(
        lon: Double,
        worldSize: Double,
    ): Double = (lon + 180.0) / 360.0 * worldSize

    fun latToY(
        lat: Double,
        worldSize: Double,
    ): Double {
        val clamped = lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val rad = Math.toRadians(clamped)
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * worldSize
    }

    fun xToLon(
        x: Double,
        worldSize: Double,
    ): Double = x / worldSize * 360.0 - 180.0

    fun yToLat(
        y: Double,
        worldSize: Double,
    ): Double {
        val n = PI * (1.0 - 2.0 * y / worldSize)
        return Math.toDegrees(atan(sinh(n)))
    }

    /** Ground resolution in meters per pixel at [lat] for a world of [worldSize] pixels. */
    fun metersPerPixel(
        lat: Double,
        worldSize: Double,
    ): Double = EARTH_CIRCUMFERENCE_METERS * cos(Math.toRadians(lat)) / worldSize
}

/** Address of a slippy-map tile. */
data class TileId(
    val zoom: Int,
    val x: Int,
    val y: Int,
)
