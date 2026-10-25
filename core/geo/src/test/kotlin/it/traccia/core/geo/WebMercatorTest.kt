package it.traccia.core.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebMercatorTest {
    private val world = WebMercator.worldSize(zoom = 14.0, tileSize = 256)

    @Test
    fun `origin maps to world center`() {
        assertEquals(world / 2, WebMercator.lonToX(0.0, world), 1e-6)
        assertEquals(world / 2, WebMercator.latToY(0.0, world), 1e-6)
    }

    @Test
    fun `round trip lon lat through pixels`() {
        val lat = 45.86
        val lon = 9.47
        assertEquals(lon, WebMercator.xToLon(WebMercator.lonToX(lon, world), world), 1e-9)
        assertEquals(lat, WebMercator.yToLat(WebMercator.latToY(lat, world), world), 1e-9)
    }

    @Test
    fun `Resegone falls in the expected z14 tile`() {
        assertEquals(8622, (WebMercator.lonToX(9.47, world) / 256).toInt())
        assertEquals(5837, (WebMercator.latToY(45.86, world) / 256).toInt())
    }

    @Test
    fun `ground resolution halves at 60 degrees`() {
        val equator = WebMercator.metersPerPixel(0.0, world)
        assertEquals(equator / 2, WebMercator.metersPerPixel(60.0, world), 1e-9)
    }
}
