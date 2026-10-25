package it.traccia.core.ui

import it.traccia.core.ui.format.formatDuration
import it.traccia.core.ui.format.formatRouteDistance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class RouteFormatTest {
    @Test
    fun distances() {
        assertEquals("850 m", formatRouteDistance(850.0, Locale.ITALY))
        assertEquals("4,25 km", formatRouteDistance(4250.0, Locale.ITALY))
        assertEquals("18,4 km", formatRouteDistance(18_420.0, Locale.ITALY))
    }

    @Test
    fun durations() {
        assertEquals("45 min", formatDuration(45 * 60.0))
        assertEquals("3 h 05 min", formatDuration(185 * 60.0))
    }
}
