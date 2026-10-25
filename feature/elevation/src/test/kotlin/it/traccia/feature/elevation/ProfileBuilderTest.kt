package it.traccia.feature.elevation

import it.traccia.core.geo.GeoPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sin

class ProfileBuilderTest {
    /** 2 km northwards: 0.018 degrees of latitude. */
    private val track = listOf(GeoPoint(45.0, 9.0), GeoPoint(45.018, 9.0))

    /** Elevation as a function of the distance from the start along the track. */
    private fun dem(f: (Double) -> Double): (GeoPoint) -> Double = { p -> f((p.latitude - 45.0) * 111_195.0) }

    @Test
    fun `steady climb gives its ascent and gradient`() {
        // 10% for 2 km: +200 m.
        val profile = checkNotNull(ProfileBuilder.build(track, dem { 1000 + it * 0.10 }))
        assertEquals(200.0, profile.ascentMeters, 5.0)
        assertEquals(0.0, profile.descentMeters, 1e-9)
        assertEquals(10.0, profile.maxGradePercent, 0.5)
        assertEquals(1000.0, profile.minElevation, 3.0)
        assertEquals(1200.0, profile.maxElevation, 3.0)
    }

    @Test
    fun `dem noise does not create fake climbing`() {
        // Flat ground with +-1 m of high-frequency noise.
        val profile = checkNotNull(ProfileBuilder.build(track, dem { 500 + sin(it / 7) }))
        assertTrue(profile.ascentMeters < 3, "ascent ${profile.ascentMeters}")
        assertTrue(profile.descentMeters < 3, "descent ${profile.descentMeters}")
    }

    @Test
    fun `up and down are both counted`() {
        // Up 100 m in the first km, down 100 m in the second.
        val profile = checkNotNull(ProfileBuilder.build(track, dem { if (it < 1000) 500 + it * 0.1 else 600 - (it - 1000) * 0.1 }))
        assertEquals(100.0, profile.ascentMeters, 10.0)
        assertEquals(100.0, profile.descentMeters, 10.0)
    }

    @Test
    fun `steepest sections are found and do not overlap`() {
        val profile =
            checkNotNull(
                ProfileBuilder.build(
                    track,
                    dem { d ->
                        if (d in
                            800.0..1200.0
                        ) {
                            500 + (d - 800) * 0.3
                        } else if (d > 1200) {
                            620.0
                        } else {
                            500.0
                        }
                    },
                ),
            )
        val steepest = profile.steepest.maxBy { abs(it.gradePercent) }
        assertTrue(steepest.startMeters in 700.0..1000.0, steepest.toString())
        assertTrue(steepest.gradePercent > 20, steepest.toString())
        profile.steepest.zipWithNext().forEach { (a, b) -> assertTrue(a.endMeters <= b.startMeters) }
    }

    @Test
    fun `missing dem values are interpolated`() {
        assertEquals(listOf(10.0, 15.0, 20.0, 20.0), ProfileBuilder.fillGaps(listOf(10.0, null, 20.0, null)))
        assertNull(ProfileBuilder.fillGaps(listOf(null, null)))
        assertNull(ProfileBuilder.build(track) { null })
    }

    @Test
    fun `hysteresis ignores small oscillations`() {
        assertEquals(10.0 to 0.0, ProfileBuilder.climb(listOf(0.0, 2.0, 1.0, 2.0, 10.0), 3.0))
        // Final remainder below the threshold counts: net +2 m.
        assertEquals(2.0 to 0.0, ProfileBuilder.climb(listOf(0.0, 1.0, 2.0), 3.0))
    }

    @Test
    fun `slope classes`() {
        assertEquals(SlopeClass.FLAT, SlopeClass.of(-3.0))
        assertEquals(SlopeClass.STEEP, SlopeClass.of(12.0))
        assertEquals(SlopeClass.EXTREME, SlopeClass.of(-40.0))
    }
}
