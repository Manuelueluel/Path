package it.traccia.feature.gpx

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GpxParserTest {
    private fun resource(name: String) = checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing $name" }

    @Test
    fun `track with extensions, waypoints and two segments`() {
        val doc = resource("track-with-extensions.gpx").use(GpxParser::parse)
        assertEquals("Resegone dal Passo del Fò", doc.name)
        assertEquals("Giro & vetta", doc.description)
        assertEquals(1, doc.tracks.size)
        assertEquals(5, doc.tracks.single().size)
        assertEquals(
            1100.5,
            doc.tracks
                .single()
                .first()
                .elevation,
        )
        assertEquals("Punta Cermenati", doc.waypoints.single().name)
        assertEquals(
            1875.0,
            doc.waypoints
                .single()
                .point.elevation,
        )
        assertEquals(doc.tracks.single(), doc.mainLine)
    }

    @Test
    fun `route only file uses the route`() {
        val doc = resource("route-only.gpx").use(GpxParser::parse)
        assertTrue(doc.tracks.isEmpty())
        assertEquals("Giro in bici", doc.name)
        assertEquals(3, checkNotNull(doc.mainLine).size)
        assertNull(doc.mainLine?.first()?.elevation)
    }

    @Test
    fun `gpx 1_0 is supported`() {
        val doc = resource("gpx10.gpx").use(GpxParser::parse)
        assertEquals(2, doc.tracks.single().size)
    }

    @Test
    fun `other xml files are rejected`() {
        assertThrows<GpxFormatException> { resource("not-gpx.xml").use(GpxParser::parse) }
        assertThrows<GpxFormatException> { "not xml at all".byteInputStream().use(GpxParser::parse) }
        assertThrows<GpxFormatException> {
            """<gpx><trk><trkseg><trkpt lat="x" lon="1"/></trkseg></trk></gpx>""".byteInputStream().use(GpxParser::parse)
        }
    }
}
