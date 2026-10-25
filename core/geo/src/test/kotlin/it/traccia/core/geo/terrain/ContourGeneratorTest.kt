package it.traccia.core.geo.terrain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.hypot

class ContourGeneratorTest {
    private fun grid(
        size: Int,
        f: (Int, Int) -> Double,
    ) = ElevationGrid(size, size, FloatArray(size * size) { f(it % size, it / size).toFloat() })

    /** A cone losing 10 m per pixel from the center, clamped to 0..95 m so it stays inside the grid. */
    private val cone = grid(21) { x, y -> (100 - 10 * hypot(x - 10.0, y - 10.0)).coerceIn(0.0, 95.0) }

    @Test
    fun `flat terrain has no contours`() {
        assertTrue(ContourGenerator.generate(grid(10) { _, _ -> 500.0 }, 10.0).isEmpty())
    }

    @Test
    fun `inclined plane gives one straight open line per level`() {
        // Elevation grows 10 m per column: 0..90.
        val plane = grid(10) { x, _ -> x * 10.0 }
        val lines = ContourGenerator.generate(plane, 25.0)
        assertEquals(listOf(25.0, 50.0, 75.0), lines.map { it.elevation })
        lines.forEach { line ->
            assertFalse(line.isClosed)
            assertEquals(10, line.pointCount, "one point per row")
            val expectedX = line.elevation / 10.0
            for (i in 0 until line.pointCount) assertEquals(expectedX, line.coordinates[i * 2], 1e-9)
        }
    }

    @Test
    fun `cone gives one closed ring per level at the right radius`() {
        val lines = ContourGenerator.generate(cone, 20.0)
        assertEquals(listOf(20.0, 40.0, 60.0, 80.0), lines.map { it.elevation })
        lines.forEach { line ->
            assertTrue(line.isClosed, "ring at ${line.elevation}")
            val expectedRadius = (100 - line.elevation) / 10
            for (i in 0 until line.pointCount) {
                val r = hypot(line.coordinates[i * 2] - 10, line.coordinates[i * 2 + 1] - 10)
                assertEquals(expectedRadius, r, 0.25, "point $i of ${line.elevation}")
            }
        }
    }

    @Test
    fun `contour cut by the grid border stays a single open line`() {
        // Peak at the corner: each level is a quarter circle touching two borders.
        val corner = grid(12) { x, y -> 100 - 10 * hypot(x.toDouble(), y.toDouble()) }
        val lines = ContourGenerator.contoursAt(corner, 50.0)
        assertEquals(1, lines.size)
        assertFalse(lines.single().isClosed)
    }

    @Test
    fun `two separate peaks give two rings`() {
        val twoPeaks = grid(30) { x, y -> maxOf(100 - 10 * hypot(x - 7.0, y - 7.0), 100 - 10 * hypot(x - 22.0, y - 22.0)) }
        assertEquals(2, ContourGenerator.contoursAt(twoPeaks, 60.0).size)
    }

    @Test
    fun `saddle cell produces two segments without crossing`() {
        // High tl/br, low tr/bl, center below level: two separate pieces.
        val saddle = ElevationGrid(2, 2, floatArrayOf(10f, 0f, 0f, 10f))
        val lines = ContourGenerator.contoursAt(saddle, 6.0)
        assertEquals(2, lines.size)
        lines.forEach { assertEquals(2, it.pointCount) }
    }

    @Test
    fun `interval depends on zoom`() {
        assertNull(ContourInterval.forZoom(10))
        assertEquals(ContourInterval(50, 250), ContourInterval.forZoom(12))
        assertEquals(ContourInterval(10, 50), ContourInterval.forZoom(17))
        assertTrue(ContourInterval(10, 50).isMajor(1850.0))
        assertFalse(ContourInterval(10, 50).isMajor(1860.0))
    }
}
