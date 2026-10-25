package it.traccia.core.terrain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class MapSourcesTest {
    @Test
    fun `bundled configuration parses`() {
        val sources = MapSources.parse(File("src/main/assets/map_sources.json").readText())
        assertEquals("terrarium", sources.dem.encoding)
        assertEquals(512, sources.dem.tileSize)
        assertEquals(12, sources.dem.maxZoom)
        assertEquals("https://tiles.mapterhorn.com/12/2155/1459.webp", sources.dem.onlineUrlFor(12, 2155, 1459))
        assertEquals("https://tiles.traccia.invalid/dem/{z}/{x}/{y}.webp", sources.virtualTiles(TileLayer.DEM))
        assertEquals("https://tiles.traccia.invalid/fonts/{fontstack}/{range}.pbf", sources.virtualGlyphs)
    }
}
