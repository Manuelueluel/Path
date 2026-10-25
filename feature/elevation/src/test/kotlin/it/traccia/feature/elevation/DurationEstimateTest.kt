package it.traccia.feature.elevation

import it.traccia.core.common.Sport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DurationEstimateTest {
    private fun hours(
        sport: Sport,
        km: Double,
        up: Double,
        down: Double = 0.0,
    ) = DurationEstimate.seconds(sport, km * 1000, up, down) / 3600

    @Test
    fun `hiking on the flat is 4 km per hour`() {
        assertEquals(2.0, hours(Sport.HIKING, 8.0, 0.0), 1e-9)
    }

    @Test
    fun `hiking follows the DIN 33466 combination`() {
        // 8 km = 2 h horizontal; 900 m up = 3 h vertical -> 3 + 2/2 = 4 h.
        assertEquals(4.0, hours(Sport.HIKING, 8.0, 900.0), 1e-9)
    }

    @Test
    fun `climbing slows every sport`() {
        Sport.entries.forEach { sport -> assertTrue(hours(sport, 20.0, 1000.0) > hours(sport, 20.0, 0.0), sport.name) }
    }

    @Test
    fun `road cycling is faster than hiking`() {
        assertTrue(hours(Sport.ROAD_CYCLING, 50.0, 500.0) < hours(Sport.HIKING, 50.0, 500.0))
    }
}
