package it.traccia.core.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PolylineTest {
    @Test
    fun `round trip keeps 1e-6 degree precision`() {
        val points = listOf(GeoPoint(45.857012, 9.480011), GeoPoint(45.858, 9.47), GeoPoint(-33.9, 151.2), GeoPoint(0.0, -179.999999))
        val decoded = Polyline.decode(Polyline.encode(points))
        assertEquals(points.size, decoded.size)
        points.zip(decoded).forEach { (a, b) ->
            assertEquals(a.latitude, b.latitude, 1e-6)
            assertEquals(a.longitude, b.longitude, 1e-6)
        }
    }

    @Test
    fun `empty line`() {
        assertEquals("", Polyline.encode(emptyList()))
        assertEquals(emptyList<GeoPoint>(), Polyline.decode(""))
    }

    @Test
    fun `truncated input is rejected`() {
        val encoded = Polyline.encode(listOf(GeoPoint(45.0, 9.0)))
        assertThrows<IllegalArgumentException> { Polyline.decode(encoded.dropLast(1)) }
    }
}
