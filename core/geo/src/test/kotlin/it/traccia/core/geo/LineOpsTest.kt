package it.traccia.core.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LineOpsTest {
    // ~111 m per 0.001 degree of latitude.
    private val straight = (0..10).map { GeoPoint(45.0 + it * 0.001, 9.0) }

    @Test
    fun `collinear points collapse to the end points`() {
        assertEquals(listOf(straight.first(), straight.last()), straight.simplify(1.0))
    }

    @Test
    fun `corners above the tolerance are kept`() {
        val corner = listOf(GeoPoint(45.0, 9.0), GeoPoint(45.01, 9.0), GeoPoint(45.01, 9.01))
        assertEquals(corner, corner.simplify(10.0))
    }

    @Test
    fun `small wiggles below the tolerance disappear`() {
        val wiggly = straight.mapIndexed { i, p -> if (i % 2 == 1) GeoPoint(p.latitude, p.longitude + 0.00002) else p }
        assertEquals(2, wiggly.simplify(5.0).size)
    }

    @Test
    fun `perpendicular distance of a point 100 m east`() {
        val d =
            perpendicularDistance(
                GeoPoint(45.0005, 9.0 + 100 / (111_195.0 * Math.cos(Math.toRadians(45.0)))),
                straight.first(),
                straight.last(),
            )
        assertEquals(100.0, d, 0.5)
    }

    @Test
    fun `resample at constant spacing`() {
        val samples = straight.resample(100.0)
        // ~1112 m long: 0, 100, ..., 1100 and the end.
        assertEquals(13, samples.size)
        assertEquals(500.0, samples[5].distanceMeters, 1e-9)
        assertEquals(straight.first(), samples.first().point)
        assertEquals(straight.last(), samples.last().point)
        samples.zipWithNext().forEach { (a, b) -> assertTrue(b.distanceMeters > a.distanceMeters) }
    }

    @Test
    fun `resample of a single point`() {
        assertEquals(listOf(DistancePoint(straight.first(), 0.0)), listOf(straight.first()).resample(10.0))
    }
}
