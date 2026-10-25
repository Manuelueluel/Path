package it.traccia.core.storage.route

import it.traccia.core.common.Sport
import it.traccia.core.geo.GeoPoint

/** How a segment between two control points was obtained. */
enum class SegmentKind {
    /** Computed by the router along paths and roads. */
    ROUTED,

    /** Straight line chosen by the user (straight-line profile). */
    STRAIGHT,

    /** Straight line because routing failed: drawn dashed (M4.6). */
    FALLBACK,

    /** Geometry kept from an imported GPX track (M7.5). */
    IMPORTED,
}

data class RouteSegment(
    val geometry: List<GeoPoint>,
    val kind: SegmentKind,
)

/** Routing choices of a route, set outside the drawing mode (M5.9). */
data class RoutingSettings(
    /** "Linea retta" profile: no routing at all. */
    val straightLine: Boolean = false,
    /** Enabled profile options (M4.4), e.g. `avoid_steps`. */
    val options: Set<String> = emptySet(),
)

/** A saved route with its control points and computed segments. */
data class Route(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val sport: Sport,
    val routing: RoutingSettings = RoutingSettings(),
    /** Control points in drawing order; the last one is the arrival. */
    val points: List<GeoPoint>,
    /** `segments[i]` joins `points[i]` and `points[i + 1]`. */
    val segments: List<RouteSegment>,
    val distanceMeters: Double = 0.0,
    val ascentMeters: Double = 0.0,
    val descentMeters: Double = 0.0,
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
) {
    /** Full geometry, segments joined without repeating the shared points. */
    val track: List<GeoPoint>
        get() =
            segments.foldIndexed(ArrayList<GeoPoint>()) { i, acc, s ->
                acc.apply { addAll(if (i == 0) s.geometry else s.geometry.drop(1)) }
            }
}

/** What the library list needs, without loading full geometries. */
data class RouteSummary(
    val id: Long,
    val name: String,
    val sport: Sport,
    val distanceMeters: Double,
    val ascentMeters: Double,
    val descentMeters: Double,
    val updatedAtMillis: Long,
    /** Simplified geometry for the thumbnail. */
    val preview: List<GeoPoint>,
)
