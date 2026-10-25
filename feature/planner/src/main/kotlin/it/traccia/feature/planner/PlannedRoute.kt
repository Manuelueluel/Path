package it.traccia.feature.planner

import it.traccia.core.geo.GeoPoint
import it.traccia.core.geo.lengthMeters
import it.traccia.core.storage.route.RouteSegment
import it.traccia.core.storage.route.SegmentKind

/**
 * The route being drawn: a linear sequence of control points (CLAUDE.md, "Interazione di
 * pianificazione"). `segments[i]` joins `points[i]` and `points[i + 1]`; null means "to compute".
 * Immutable: every gesture produces a new value, so the rules are unit-tested here.
 */
data class PlannedRoute(
    val points: List<GeoPoint> = emptyList(),
    val segments: List<RouteSegment?> = emptyList(),
) {
    init {
        require(segments.size == maxOf(points.size - 1, 0)) { "${points.size} points need ${points.size - 1} segments" }
    }

    val isEmpty: Boolean get() = points.isEmpty()

    /** Closed when the last point is back on the start (M5.6b). */
    val isClosed: Boolean get() = points.size >= MIN_LOOP_POINTS && points.first() == points.last()

    val canCloseLoop: Boolean get() = points.size >= 2 && !isClosed

    /** Segments still to compute. */
    val pending: List<Int> get() = segments.indices.filter { segments[it] == null }

    /** Tap on the map: a new point at the end, joined to the previous one (M5.1, M5.2). */
    fun add(point: GeoPoint): PlannedRoute =
        if (points.isEmpty()) copy(points = listOf(point)) else PlannedRoute(points + point, segments + null)

    /** Removes a point; its two neighbours are joined again (M5.3). */
    fun deleteAt(index: Int): PlannedRoute {
        require(index in points.indices) { "No point $index" }
        if (points.size == 1) return PlannedRoute()
        val newPoints = points.toMutableList().apply { removeAt(index) }
        val newSegments =
            when (index) {
                0 -> segments.drop(1)
                points.lastIndex -> segments.dropLast(1)
                else -> segments.take(index - 1) + listOf<RouteSegment?>(null) + segments.drop(index + 1)
            }
        return PlannedRoute(newPoints, newSegments)
    }

    /** Drag and drop: only the adjacent segments are computed again (M5.4). */
    fun moveTo(
        index: Int,
        point: GeoPoint,
    ): PlannedRoute {
        require(index in points.indices) { "No point $index" }
        val newPoints = points.toMutableList().apply { this[index] = point }
        val newSegments = segments.mapIndexed { i, s -> if (i == index - 1 || i == index) null else s }
        return PlannedRoute(newPoints, newSegments)
    }

    /** Undo: removes the last inserted point and its segment (M5.5). */
    fun undo(): PlannedRoute = if (points.isEmpty()) this else deleteAt(points.lastIndex)

    /** Appends the start as last point (M5.6b). Undo removes it like any other point. */
    fun closeLoop(): PlannedRoute = if (canCloseLoop) add(points.first()) else this

    /** Routing settings changed: the whole route is computed again (M5.9). */
    fun invalidateAll(): PlannedRoute = copy(segments = segments.map { null })

    /** Stores a computed segment, if its end points did not change meanwhile (M4.5). */
    fun withSegment(
        index: Int,
        from: GeoPoint,
        to: GeoPoint,
        segment: RouteSegment,
    ): PlannedRoute =
        if (isPendingBetween(index, from, to)) {
            copy(segments = segments.toMutableList().apply { this[index] = segment })
        } else {
            this
        }

    private fun isPendingBetween(
        index: Int,
        from: GeoPoint,
        to: GeoPoint,
    ): Boolean = index < segments.size && segments[index] == null && points[index] == from && points[index + 1] == to

    /**
     * Stores a computed segment wherever its end points still are: points may have been deleted
     * before the result arrived. Late results for changed segments are dropped (M4.5).
     */
    fun withSegment(
        from: GeoPoint,
        to: GeoPoint,
        segment: RouteSegment,
    ): PlannedRoute {
        val index = segments.indices.firstOrNull { isPendingBetween(it, from, to) } ?: return this
        return withSegment(index, from, to, segment)
    }

    /** Distance of each control point along the track, in meters. */
    fun controlPointDistances(): List<Double> {
        var total = 0.0
        val out = ArrayList<Double>(points.size)
        points.forEachIndexed { i, _ ->
            if (i > 0) total += (segments[i - 1]?.geometry ?: listOf(points[i - 1], points[i])).lengthMeters()
            out += total
        }
        return out
    }

    /** Full geometry; segments not yet computed are straight lines. */
    val track: List<GeoPoint>
        get() =
            if (points.size < 2) {
                points
            } else {
                segments.foldIndexed(ArrayList<GeoPoint>()) { i, acc, s ->
                    val geometry = s?.geometry ?: listOf(points[i], points[i + 1])
                    acc.apply { addAll(if (i == 0) geometry else geometry.drop(1)) }
                }
            }

    /** Segments for saving: the pending ones become straight fallbacks. */
    fun completedSegments(): List<RouteSegment> =
        segments.mapIndexed { i, s -> s ?: RouteSegment(listOf(points[i], points[i + 1]), SegmentKind.FALLBACK) }

    private companion object {
        const val MIN_LOOP_POINTS = 3
    }
}
