package it.traccia.core.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DistanceTest {
    private val milanoDuomo = GeoPoint(45.4642, 9.1900)
    private val romaColosseo = GeoPoint(41.8902, 12.4922)

    @Test
    fun `distance between same point is zero`() {
        assertEquals(0.0, milanoDuomo.distanceTo(milanoDuomo), 1e-9)
    }

    @Test
    fun `distance Milano Roma is about 477 km`() {
        assertEquals(477_000.0, milanoDuomo.distanceTo(romaColosseo), 2_000.0)
    }

    @Test
    fun `one degree of latitude is about 111 km`() {
        assertEquals(111_195.0, GeoPoint(0.0, 0.0).distanceTo(GeoPoint(1.0, 0.0)), 10.0)
    }

    @Test
    fun `polyline length is sum of segments`() {
        val line = listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0), GeoPoint(2.0, 0.0))
        assertEquals(2 * 111_195.0, line.lengthMeters(), 20.0)
    }

    @Test
    fun `empty polyline has zero length`() {
        assertEquals(0.0, emptyList<GeoPoint>().lengthMeters())
    }

    @Test
    fun `invalid latitude is rejected`() {
        assertThrows<IllegalArgumentException> { GeoPoint(91.0, 0.0) }
    }
}
