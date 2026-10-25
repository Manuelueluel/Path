package it.traccia.feature.gpx

import it.traccia.core.common.Sport
import it.traccia.core.geo.GeoPoint
import it.traccia.core.geo.distanceTo
import it.traccia.core.geo.lengthMeters
import it.traccia.core.storage.route.SegmentKind
import it.traccia.feature.gpx.GpxConversion.simplifiedFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GpxConversionTest {
    /** A 5.5 km zig-zag of 112 m steps. */
    private val zigzag = (0..50).map { i -> GeoPoint(45.0 + i * 0.001, 9.0 + if (i % 10 < 5) i % 5 * 0.001 else (5 - i % 5) * 0.001) }

    @Test
    fun `imported track keeps its exact shape`() {
        val route = GpxConversion.toRoute(zigzag, "Import", Sport.HIKING)
        assertEquals(zigzag, route.track)
        assertTrue(route.segments.all { it.kind == SegmentKind.IMPORTED })
        assertEquals(route.points.size - 1, route.segments.size)
    }

    @Test
    fun `control points are few but never too far apart`() {
        val route = GpxConversion.toRoute(zigzag, "Import", Sport.HIKING)
        assertTrue(route.points.size < zigzag.size / 2, "${route.points.size} control points")
        route.segments.forEach { assertTrue(it.geometry.lengthMeters() <= 1_700, "segment ${it.geometry.lengthMeters()}") }
        assertEquals(zigzag.first(), route.points.first())
        assertEquals(zigzag.last(), route.points.last())
    }

    @Test
    fun `long straight track still gets intermediate points`() {
        val straight = (0..100).map { GeoPoint(45.0 + it * 0.001, 9.0) }
        val route = GpxConversion.toRoute(straight, "Dritto", Sport.RUNNING)
        assertTrue(route.points.size >= 8, "${route.points.size}")
        route.points.zipWithNext().forEach { (a, b) -> assertTrue(a.distanceTo(b) <= 1_600) }
    }

    @Test
    fun `repeated points and elevations are dropped`() {
        val line = listOf(GeoPoint(45.0, 9.0, 100.0), GeoPoint(45.0, 9.0, 100.0), GeoPoint(45.01, 9.0, 200.0))
        val route = GpxConversion.toRoute(line, "x", Sport.HIKING)
        assertEquals(listOf(GeoPoint(45.0, 9.0), GeoPoint(45.01, 9.0)), route.track)
    }

    @Test
    fun `file names are safe`() {
        assertEquals("Anello_del_Resegone_2026.gpx", GpxConversion.fileName("Anello del Resegone / 2026?"))
        assertEquals("percorso.gpx", GpxConversion.fileName("???"))
    }

    @Test
    fun `simplification levels reduce the points`() {
        // 1 km line with 2 m zig-zags every meter.
        val dense = (0..1000).map { GeoPoint(45.0 + it * 0.000009, 9.0 + if (it % 2 == 0) 0.0 else 0.000025) }
        val counts = Simplification.entries.map { dense.simplifiedFor(it).size }
        assertEquals(dense.size, counts.first())
        assertTrue(counts.zipWithNext().all { (a, b) -> b <= a }, counts.toString())
        assertEquals(2, counts.last())
    }
}
