package it.traccia.feature.map.style

import it.traccia.core.terrain.MapSources
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class StylePlaceholdersTest {
    private val sources = MapSources.parse(File("../../core/terrain/src/main/assets/map_sources.json").readText())

    @Test
    fun `every bundled style resolves to valid JSON without placeholders`() {
        MapStyle.entries.forEach { style ->
            val resolved = resolveStylePlaceholders(File("src/main/assets/${style.asset}").readText(), sources)
            assertFalse(resolved.contains("{{"), "${style.name} still has placeholders")
            assertTrue(resolved.contains("\"tileSize\": 512"), "${style.name} tileSize must be numeric")
            assertTrue(resolved.contains("\"id\": \"hillshade\""), "${style.name} needs the hillshade anchor layer")
            assertTrue(resolved.contains("https://tiles.traccia.invalid/basemap/{z}/{x}/{y}.pbf"), "${style.name} must use routed tiles")
        }
    }

    @Test
    fun `attribution html is escaped for JSON`() {
        val resolved = resolveStylePlaceholders("{\"a\": \"{{DEM_ATTRIBUTION}}\"}", sources)
        val escaped = sources.dem.attribution.replace("\"", "\\\"")
        assertEquals("{\"a\": \"$escaped\"}", resolved)
    }
}
