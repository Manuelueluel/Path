package it.traccia.feature.routing

import it.traccia.core.common.Sport
import it.traccia.core.geo.GeoPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RoutingProfilesTest {
    private val profiles = RoutingProfiles.parse(File("src/main/assets/${RoutingProfiles.ASSET}").readText())

    @Test
    fun `every sport has a bundled profile`() {
        Sport.entries.forEach { sport ->
            val brf = profiles.profile(sport).brf
            assertTrue(File("src/main/assets/brouter/$brf.brf").exists(), "$sport -> $brf.brf missing")
        }
        assertTrue(File("src/main/assets/brouter/lookups.dat").exists())
    }

    @Test
    fun `options become profile parameters`() {
        assertEquals(mapOf("allow_steps" to "false"), profiles.parameters(Sport.HIKING, setOf("avoid_steps")))
        assertEquals(mapOf("hiking_routes_preference" to "0"), profiles.parameters(Sport.RUNNING, emptySet()))
        assertEquals(mapOf("prefer_unpaved_paths" to "true"), profiles.parameters(Sport.GRAVEL, setOf("prefer_unpaved", "unknown")))
    }

    @Test
    fun `option parameters exist in the profile file`() {
        Sport.entries.forEach { sport ->
            val profile = profiles.profile(sport)
            val text = File("src/main/assets/brouter/${profile.brf}.brf").readText()
            (profile.params.keys + profile.options.values.flatMap { it.keys }).forEach { param ->
                assertTrue(text.contains("%$param%"), "$param is not a parameter of ${profile.brf}")
            }
        }
    }

    @Test
    fun `segment file names follow the BRouter 5 degree grid`() {
        assertEquals("E5_N45.rd5", BRouterEngine.segmentFileName(GeoPoint(45.86, 9.47)))
        assertEquals("W5_N40.rd5", BRouterEngine.segmentFileName(GeoPoint(42.0, -3.2)))
        assertEquals("E10_S5.rd5", BRouterEngine.segmentFileName(GeoPoint(-1.0, 12.0)))
    }
}
