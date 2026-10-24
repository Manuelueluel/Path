package it.traccia.core.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_008.8

/** Great-circle distance in meters between two points (haversine formula). */
fun GeoPoint.distanceTo(other: GeoPoint): Double {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(other.longitude - longitude)
    val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
}

/** Total length in meters of a polyline. */
fun List<GeoPoint>.lengthMeters(): Double = zipWithNext { a, b -> a.distanceTo(b) }.sum()
