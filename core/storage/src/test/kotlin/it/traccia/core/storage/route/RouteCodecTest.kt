package it.traccia.core.storage.route

import it.traccia.core.common.Sport
import it.traccia.core.geo.GeoPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RouteCodecTest {
    private val a = GeoPoint(45.85, 9.40)
    private val b = GeoPoint(45.86, 9.41)
    private val c = GeoPoint(45.87, 9.40)

    private val route =
        Route(
            id = 7,
            name = "Anello del Resegone",
            description = "Prova",
            sport = Sport.HIKING,
            routing = RoutingSettings(straightLine = false, options = setOf("avoid_steps", "avoid_climbs")),
            points = listOf(a, b, c),
            segments =
                listOf(
                    RouteSegment(listOf(a, GeoPoint(45.855, 9.405), b), SegmentKind.ROUTED),
                    RouteSegment(listOf(b, c), SegmentKind.FALLBACK),
                ),
            distanceMeters = 2500.0,
            ascentMeters = 300.0,
            descentMeters = 120.0,
            createdAtMillis = 1,
            updatedAtMillis = 2,
        )

    private fun List<GeoPoint>.rounded() = map { GeoPoint(Math.round(it.latitude * 1e6) / 1e6, Math.round(it.longitude * 1e6) / 1e6) }

    @Test
    fun `route survives a round trip through the entity`() {
        val back = RouteCodec.toRoute(RouteCodec.toEntity(route))
        assertEquals(
            route.copy(points = route.points.rounded(), segments = route.segments.map { it.copy(geometry = it.geometry.rounded()) }),
            back,
        )
    }

    @Test
    fun `track joins segments without repeating shared points`() {
        assertEquals(listOf(a, GeoPoint(45.855, 9.405), b, c), route.track)
    }

    @Test
    fun `summary has the statistics and a preview`() {
        val summary = RouteCodec.toSummary(RouteCodec.toEntity(route))
        assertEquals("Anello del Resegone", summary.name)
        assertEquals(2500.0, summary.distanceMeters)
        assertTrue(summary.preview.size in 2..4)
    }

    @Test
    fun `empty route encodes to empty fields`() {
        val empty = RouteCodec.toEntity(route.copy(points = emptyList(), segments = emptyList()))
        assertEquals("", empty.points)
        assertEquals("", empty.segments)
        assertEquals(emptyList<RouteSegment>(), RouteCodec.toRoute(empty).segments)
    }
}
