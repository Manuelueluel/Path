package it.traccia.core.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class MapScaleTest {
    @Test
    fun `picks the largest round distance that fits`() {
        // 3.7 m/dp * 100 dp = 370 m -> 200 m
        val bar = MapScale.scaleBar(metersPerDp = 3.7, maxWidthDp = 100f)
        assertEquals(200.0, bar.meters)
        assertEquals(200 / 3.7, bar.widthDp.toDouble(), 1e-3)
    }

    @Test
    fun `exact round distance is kept`() {
        assertEquals(500.0, MapScale.scaleBar(metersPerDp = 5.0, maxWidthDp = 100f).meters)
    }

    @Test
    fun `bar never exceeds the max width`() {
        listOf(0.01, 0.3, 1.0, 7.7, 42.0, 999.0, 12_345.0).forEach { mpd ->
            val bar = MapScale.scaleBar(mpd, 120f)
            assertTrue(bar.widthDp <= 120f && bar.widthDp > 120f / 5.1f, "mpd=$mpd -> $bar")
        }
    }

    @Test
    fun `MapLibre zoom 0 at equator is about 78 km per dp`() {
        assertEquals(78_271.5, MapScale.metersPerDp(0.0, 0.0), 1.0)
    }

    @Test
    fun `distance formatting`() {
        assertEquals("200 m", formatDistance(200.0, Locale.ITALY))
        assertEquals("2 km", formatDistance(2000.0, Locale.ITALY))
        assertEquals("1,5 km", formatDistance(1500.0, Locale.ITALY))
    }

    @Test
    fun `coordinate formatting with hemispheres`() {
        assertEquals("45,86000° N  9,47000° E", formatCoordinates(45.86, 9.47, Locale.ITALY))
        assertEquals("33.90000° S  18.40000° W", formatCoordinates(-33.9, -18.4, Locale.ROOT))
    }
}
