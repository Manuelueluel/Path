package it.traccia.core.geo.terrain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ElevationGridTest {
    // 0 10
    // 20 30
    private val grid = ElevationGrid(2, 2, floatArrayOf(0f, 10f, 20f, 30f))

    @Test
    fun `sample at pixel centers returns exact values`() {
        assertEquals(0.0, grid.sample(0.0, 0.0))
        assertEquals(30.0, grid.sample(1.0, 1.0))
    }

    @Test
    fun `bilinear interpolation in the middle`() {
        assertEquals(15.0, grid.sample(0.5, 0.5), 1e-9)
        assertEquals(5.0, grid.sample(0.5, 0.0), 1e-9)
    }

    @Test
    fun `outside coordinates are clamped`() {
        assertEquals(30.0, grid.sample(5.0, 9.0))
        assertEquals(0.0, grid.sample(-3.0, -1.0))
    }

    @Test
    fun `size mismatch is rejected`() {
        assertThrows<IllegalArgumentException> { ElevationGrid(2, 2, FloatArray(3)) }
    }
}
