package it.traccia.feature.heatmap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeatmapConfigTest {
    @Test
    fun `bundled config lists the sports of features md`() {
        val ids = bundledConfig.sports.map { it.id }
        assertTrue(ids.containsAll(listOf("all", "ride", "run", "hike", "mtb", "gravel", "winter", "water")), ids.toString())
        assertTrue("hot" in bundledConfig.colors)
    }

    @Test
    fun `tile url is built from the config template`() {
        val url = bundledConfig.tileUrlFor(checkNotNull(bundledConfig.sport("mtb")), "hot", 13, 4311, 2918)
        assertEquals("https://content-a.strava.com/identified/globalheat/sport_MountainBikeRide/hot/13/4311/2918.png?v=19", url)
        assertEquals("https://content-a.strava.com", bundledConfig.tileOrigin)
    }

    @Test
    fun `tile ranges cover the visible area one zoom above for 256 px tiles`() {
        val ranges = tileRanges(MapArea(9.40, 45.80, 9.55, 45.90, zoom = 12.4), bundledConfig)
        assertEquals(listOf(13, 14), ranges.map { it.zoom })
        val z13 = ranges.first()
        assertTrue(z13.minX <= 4310 && z13.maxX >= 4313 && z13.minY <= 2917 && z13.maxY >= 2919, z13.toString())
    }

    @Test
    fun `tile ranges stay within the configured zooms`() {
        assertEquals(listOf(15), tileRanges(MapArea(9.4, 45.8, 9.41, 45.81, zoom = 17.0), bundledConfig).map { it.zoom })
    }
}
