package it.traccia.core.geo

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Douglas-Peucker simplification with a tolerance in meters (local equirectangular projection,
 * accurate at route scale). End points are always kept.
 */
fun List<GeoPoint>.simplify(toleranceMeters: Double): List<GeoPoint> {
    if (size < MIN_SIMPLIFIABLE || toleranceMeters <= 0) return this
    val keep =
        BooleanArray(size).also {
            it[0] = true
            it[lastIndex] = true
        }
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.add(0 to lastIndex)
    while (stack.isNotEmpty()) {
        val (first, last) = stack.removeLast()
        var maxDistance = 0.0
        var index = -1
        for (i in first + 1 until last) {
            val d = perpendicularDistance(this[i], this[first], this[last])
            if (d > maxDistance) {
                maxDistance = d
                index = i
            }
        }
        if (index >= 0 && maxDistance > toleranceMeters) {
            keep[index] = true
            stack.add(first to index)
            stack.add(index to last)
        }
    }
    return filterIndexed { i, _ -> keep[i] }
}

/** Distance in meters from [p] to the segment [a]-[b]. */
fun perpendicularDistance(
    p: GeoPoint,
    a: GeoPoint,
    b: GeoPoint,
): Double {
    val metersPerDegLat = METERS_PER_DEGREE
    val metersPerDegLon = METERS_PER_DEGREE * cos(Math.toRadians(a.latitude))
    val ax = 0.0
    val ay = 0.0
    val bx = (b.longitude - a.longitude) * metersPerDegLon
    val by = (b.latitude - a.latitude) * metersPerDegLat
    val px = (p.longitude - a.longitude) * metersPerDegLon
    val py = (p.latitude - a.latitude) * metersPerDegLat
    val lengthSquared = bx * bx + by * by
    val t = if (lengthSquared == 0.0) 0.0 else (((px - ax) * bx + (py - ay) * by) / lengthSquared).coerceIn(0.0, 1.0)
    val dx = px - t * bx
    val dy = py - t * by
    return sqrt(dx * dx + dy * dy)
}

/** A point along a line with its cumulative distance from the start. */
data class DistancePoint(
    val point: GeoPoint,
    val distanceMeters: Double,
)

/**
 * Points every [stepMeters] along the line (plus the last point), with cumulative distance.
 * Used to sample elevations at a constant spacing, independent of the vertex density.
 */
fun List<GeoPoint>.resample(stepMeters: Double): List<DistancePoint> {
    require(stepMeters > 0) { "Step must be positive" }
    if (isEmpty()) return emptyList()
    val out = ArrayList<DistancePoint>()
    out += DistancePoint(first(), 0.0)
    var travelled = 0.0
    var nextAt = stepMeters
    for (i in 1 until size) {
        val a = this[i - 1]
        val b = this[i]
        val length = a.distanceTo(b)
        while (length > 0 && nextAt <= travelled + length) {
            val t = (nextAt - travelled) / length
            out += DistancePoint(interpolate(a, b, t), nextAt)
            nextAt += stepMeters
        }
        travelled += length
    }
    if (travelled > out.last().distanceMeters) out += DistancePoint(last(), travelled)
    return out
}

fun interpolate(
    a: GeoPoint,
    b: GeoPoint,
    t: Double,
): GeoPoint = GeoPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)

private const val METERS_PER_DEGREE = 111_195.0

/** Lines with only their end points have nothing to simplify. */
private const val MIN_SIMPLIFIABLE = 3
