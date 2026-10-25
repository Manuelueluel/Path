package it.traccia.feature.gpx

import it.traccia.core.common.Sport
import it.traccia.core.geo.GeoPoint
import it.traccia.core.geo.distanceTo
import it.traccia.core.geo.simplify
import it.traccia.core.storage.route.Route
import it.traccia.core.storage.route.RouteSegment
import it.traccia.core.storage.route.SegmentKind

/** Simplification levels for devices with point limits (M7.2): tolerance in meters, 0 = none. */
enum class Simplification(
    val toleranceMeters: Double,
) {
    NONE(0.0),
    LIGHT(5.0),
    MEDIUM(15.0),
    STRONG(30.0),
}

object GpxConversion {
    /** Control points closer than this along a straight stretch are not needed. */
    private const val CONTROL_TOLERANCE_M = 40.0

    /** But keep one every so often, so moving a point never re-routes a huge stretch. */
    private const val MAX_CONTROL_SPACING_M = 1_500.0

    /**
     * Turns an imported track into an editable route (M7.5): the original geometry is kept as
     * [SegmentKind.IMPORTED] segments between automatically chosen control points.
     */
    fun toRoute(
        line: List<GeoPoint>,
        name: String,
        sport: Sport,
    ): Route {
        require(line.size >= 2) { "A route needs at least two points" }
        // Elevations come from the DEM; repeated points (paused GPS) are dropped.
        val clean = ArrayList<GeoPoint>()
        line.forEach { p -> GeoPoint(p.latitude, p.longitude).let { if (clean.lastOrNull() != it) clean += it } }
        val indices = controlIndices(clean)
        val points = indices.map { clean[it] }
        val segments = indices.zipWithNext { from, to -> RouteSegment(clean.subList(from, to + 1).toList(), SegmentKind.IMPORTED) }
        return Route(name = name, sport = sport, points = points, segments = segments)
    }

    internal fun controlIndices(line: List<GeoPoint>): List<Int> {
        val kept = line.simplify(CONTROL_TOLERANCE_M).toSet()
        val out = ArrayList<Int>()
        var sinceLast = 0.0
        line.forEachIndexed { i, p ->
            if (i > 0) sinceLast += line[i - 1].distanceTo(p)
            val isEnd = i == 0 || i == line.lastIndex
            if (isEnd || p in kept || sinceLast >= MAX_CONTROL_SPACING_M) {
                if (out.lastOrNull() != i) out += i
                sinceLast = 0.0
            }
        }
        return out
    }

    /** Track reduced for devices with point limits (M7.2). */
    fun List<GeoPoint>.simplifiedFor(level: Simplification): List<GeoPoint> =
        if (level == Simplification.NONE) this else simplify(level.toleranceMeters)

    /** File name from the route name: safe on every file system. */
    fun fileName(name: String): String {
        val base =
            name
                .trim()
                .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
                .replace(Regex("\\s+"), "_")
                .take(MAX_NAME)
                .ifEmpty { "percorso" }
        return "$base.gpx"
    }

    private const val MAX_NAME = 60
}
