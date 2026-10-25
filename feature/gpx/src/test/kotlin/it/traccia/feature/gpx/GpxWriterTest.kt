package it.traccia.feature.gpx

import it.traccia.core.geo.GeoPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class GpxWriterTest {
    private val track = listOf(GeoPoint(45.8758, 9.4812, 1100.0), GeoPoint(45.8700, 9.4770, 1350.25), GeoPoint(45.8588, 9.4704, null))
    private val controls = listOf(track.first(), track.last())

    private fun write(content: GpxContent) =
        GpxWriter.write("Anello <Resegone> & \"vetta\"", "Prova", track, controls, listOf(GpxWaypoint(track.last(), "Cima")), content)

    @Test
    fun `track export round trips through the parser`() {
        val gpx = write(GpxContent.TRACK)
        assertTrue(gpx.contains("""version="1.1""""))
        val doc = gpx.byteInputStream().use(GpxParser::parse)
        assertEquals("Anello <Resegone> & \"vetta\"", doc.name)
        assertEquals(3, doc.tracks.single().size)
        assertEquals(1350.3, checkNotNull(doc.tracks.single()[1].elevation), 0.05)
        assertEquals(
            45.8758,
            doc.tracks
                .single()
                .first()
                .latitude,
            1e-9,
        )
        assertTrue(doc.routes.isEmpty())
        assertEquals("Cima", doc.waypoints.single().name)
    }

    @Test
    fun `route and both`() {
        val route = write(GpxContent.ROUTE).byteInputStream().use(GpxParser::parse)
        assertTrue(route.tracks.isEmpty())
        assertEquals(2, route.routes.single().size)
        val both = write(GpxContent.BOTH).byteInputStream().use(GpxParser::parse)
        assertEquals(1, both.tracks.size)
        assertEquals(1, both.routes.size)
    }

    @Test
    fun `numbers use a dot whatever the locale`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ITALY)
        try {
            assertFalse(write(GpxContent.TRACK).contains("45,8758"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
