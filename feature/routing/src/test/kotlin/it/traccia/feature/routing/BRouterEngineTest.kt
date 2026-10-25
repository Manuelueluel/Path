package it.traccia.feature.routing

import it.traccia.core.geo.GeoPoint
import it.traccia.core.geo.distanceTo
import it.traccia.core.geo.lengthMeters
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

/**
 * Real routing on the BRouter segment of the test region. Runs only when the pipeline cache has
 * `segments/E5_N45.rd5` (250 MB, not in the repository).
 */
class BRouterEngineTest {
    private val segments = File(System.getProperty("user.home"), ".cache/traccia-pipeline/segments")
    private val engine = BRouterEngine(File("src/main/assets/brouter"))

    // Morterone (village) to the Resegone summit ridge, on hiking trails.
    private val morterone = GeoPoint(45.8758, 9.4812)
    private val resegone = GeoPoint(45.8588, 9.4704)

    @Test
    fun `hiking route follows the trails`() =
        runTest {
            assumeTrue(File(segments, "E5_N45.rd5").exists(), "segment file not available")
            val track = engine.route(morterone, resegone, "hiking-mountain", emptyMap(), segments)
            val beeline = morterone.distanceTo(resegone)
            assertTrue(track.size > 10, "too few points: ${track.size}")
            assertTrue(track.first().distanceTo(morterone) < 200 && track.last().distanceTo(resegone) < 200)
            // A real trail is longer than the straight line, but not absurdly.
            assertTrue(track.lengthMeters() in beeline * 1.05..beeline * 4, "length ${track.lengthMeters()} vs beeline $beeline")
        }

    @Test
    fun `missing segment data is a routing error`() =
        runTest {
            assumeTrue(File(segments, "E5_N45.rd5").exists(), "segment file not available")
            assertThrows<RoutingException> {
                // Middle of the Atlantic: no segment file for it.
                engine.route(GeoPoint(40.0, -30.0), GeoPoint(40.01, -30.01), "hiking-mountain", emptyMap(), segments)
            }
        }
}
