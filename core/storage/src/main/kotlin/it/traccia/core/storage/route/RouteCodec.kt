package it.traccia.core.storage.route

import it.traccia.core.geo.GeoPoint
import it.traccia.core.geo.Polyline
import it.traccia.core.geo.lengthMeters
import it.traccia.core.geo.simplify

/** Conversion between [Route] and [RouteEntity]. Pure: unit-tested without Room. */
object RouteCodec {
    private const val SEGMENT_SEPARATOR = "\n"

    // Polyline characters are in the ASCII range 63..126: ':' and '\n' never appear inside them.
    private const val KIND_SEPARATOR = ":"
    private const val PREVIEW_POINTS = 60.0
    private const val MIN_PREVIEW_TOLERANCE_M = 10.0

    fun toEntity(route: Route): RouteEntity =
        RouteEntity(
            id = route.id,
            name = route.name,
            sport = route.sport,
            createdAtMillis = route.createdAtMillis,
            updatedAtMillis = route.updatedAtMillis,
            description = route.description,
            straightLine = route.routing.straightLine,
            routingOptions =
                route.routing.options
                    .sorted()
                    .joinToString(","),
            points = Polyline.encode(route.points),
            segments = route.segments.joinToString(SEGMENT_SEPARATOR) { it.kind.name + KIND_SEPARATOR + Polyline.encode(it.geometry) },
            preview = Polyline.encode(preview(route.track)),
            distanceMeters = route.distanceMeters,
            ascentMeters = route.ascentMeters,
            descentMeters = route.descentMeters,
        )

    fun toRoute(entity: RouteEntity): Route =
        Route(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            sport = entity.sport,
            routing =
                RoutingSettings(
                    entity.straightLine,
                    entity.routingOptions
                        .split(',')
                        .filter { it.isNotBlank() }
                        .toSet(),
                ),
            points = Polyline.decode(entity.points),
            segments =
                entity.segments
                    .split(SEGMENT_SEPARATOR)
                    .filter { it.isNotBlank() }
                    .map { line ->
                        RouteSegment(
                            geometry = Polyline.decode(line.substringAfter(KIND_SEPARATOR)),
                            kind =
                                SegmentKind.entries.firstOrNull {
                                    it.name ==
                                        line.substringBefore(
                                            KIND_SEPARATOR,
                                        )
                                } ?: SegmentKind.ROUTED,
                        )
                    },
            distanceMeters = entity.distanceMeters,
            ascentMeters = entity.ascentMeters,
            descentMeters = entity.descentMeters,
            createdAtMillis = entity.createdAtMillis,
            updatedAtMillis = entity.updatedAtMillis,
        )

    fun toSummary(entity: RouteEntity): RouteSummary =
        RouteSummary(
            id = entity.id,
            name = entity.name,
            sport = entity.sport,
            distanceMeters = entity.distanceMeters,
            ascentMeters = entity.ascentMeters,
            descentMeters = entity.descentMeters,
            updatedAtMillis = entity.updatedAtMillis,
            preview = Polyline.decode(entity.preview),
        )

    /** About [PREVIEW_POINTS] points whatever the route length: enough for a thumbnail. */
    fun preview(track: List<GeoPoint>): List<GeoPoint> =
        track.simplify(maxOf(MIN_PREVIEW_TOLERANCE_M, track.lengthMeters() / PREVIEW_POINTS / 2))
}
