package it.traccia.core.geo.terrain

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SmoothingTest {
    @Test
    fun `open line keeps its end points`() {
        val line = doubleArrayOf(0.0, 0.0, 1.0, 1.0, 2.0, 0.0)
        val smooth = smoothChaikin(line, isClosed = false, iterations = 2)
        assertEquals(0.0, smooth[0])
        assertEquals(0.0, smooth[1])
        assertEquals(2.0, smooth[smooth.size - 2])
        assertEquals(0.0, smooth[smooth.size - 1])
    }

    @Test
    fun `closed ring stays closed`() {
        val square = doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0, 0.0, 0.0)
        val smooth = smoothChaikin(square, isClosed = true, iterations = 1)
        assertEquals(smooth[0], smooth[smooth.size - 2])
        assertEquals(smooth[1], smooth[smooth.size - 1])
        assertEquals(9, smooth.size / 2)
    }

    @Test
    fun `zero iterations or too short lines are unchanged`() {
        val line = doubleArrayOf(0.0, 0.0, 1.0, 1.0)
        assertArrayEquals(line, smoothChaikin(line, isClosed = false, iterations = 3))
        assertArrayEquals(line, smoothChaikin(line, isClosed = false, iterations = 0))
    }
}
