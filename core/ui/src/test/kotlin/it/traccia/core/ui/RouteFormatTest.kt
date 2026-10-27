package it.traccia.core.ui

import it.traccia.core.common.UnitSystem
import it.traccia.core.ui.format.formatDuration
import it.traccia.core.ui.format.formatElevation
import it.traccia.core.ui.format.formatRouteDistance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class RouteFormatTest {
    @Test
    fun distances() {
        assertEquals("850 m", formatRouteDistance(850.0, locale = Locale.ITALY))
        assertEquals("4,25 km", formatRouteDistance(4250.0, locale = Locale.ITALY))
        assertEquals("18,4 km", formatRouteDistance(18_420.0, locale = Locale.ITALY))
    }

    @Test
    fun `imperial distances`() {
        assertEquals("984 ft", formatRouteDistance(300.0, UnitSystem.IMPERIAL, Locale.ROOT))
        assertEquals("2.64 mi", formatRouteDistance(4250.0, UnitSystem.IMPERIAL, Locale.ROOT))
        assertEquals("11.4 mi", formatRouteDistance(18_420.0, UnitSystem.IMPERIAL, Locale.ROOT))
    }

    @Test
    fun elevations() {
        assertEquals("1234 m", formatElevation(1234.4))
        assertEquals("4049 ft", formatElevation(1234.1, UnitSystem.IMPERIAL))
    }

    @Test
    fun durations() {
        assertEquals("45 min", formatDuration(45 * 60.0))
        assertEquals("3 h 05 min", formatDuration(185 * 60.0))
    }
}
