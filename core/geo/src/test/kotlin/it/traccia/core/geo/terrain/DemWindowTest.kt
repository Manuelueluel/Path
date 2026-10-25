package it.traccia.core.geo.terrain

import it.traccia.core.geo.TileId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DemWindowTest {
    private val tileSize = 4

    /** Each tile's values encode their own world pixel: x * 1000 + y. */
    private fun tile(id: TileId) =
        ElevationGrid(
            tileSize,
            tileSize,
            FloatArray(tileSize * tileSize) { i ->
                val x = id.x * tileSize + i % tileSize
                val y = id.y * tileSize + i / tileSize
                (x * 1000 + y).toFloat()
            },
        )

    @Test
    fun `window inside a single tile`() {
        val fetched = mutableListOf<TileId>()
        val grid =
            checkNotNull(
                DemWindow(zoom = 2, tileSize = tileSize) {
                    fetched += it
                    tile(it)
                }.read(5, 5, 2, 2),
            )
        assertEquals(listOf(TileId(2, 1, 1)), fetched)
        assertEquals(5005f, grid[0, 0])
        assertEquals(6006f, grid[1, 1])
    }

    @Test
    fun `window stitched across four tiles`() {
        val fetched = mutableSetOf<TileId>()
        val grid =
            checkNotNull(
                DemWindow(zoom = 2, tileSize = tileSize) {
                    fetched += it
                    tile(it)
                }.read(3, 3, 3, 3),
            )
        assertEquals(setOf(TileId(2, 0, 0), TileId(2, 1, 0), TileId(2, 0, 1), TileId(2, 1, 1)), fetched)
        for (y in 0 until 3) {
            for (x in 0 until 3) assertEquals(((3 + x) * 1000 + (3 + y)).toFloat(), grid[x, y])
        }
    }

    @Test
    fun `missing tile yields null`() {
        assertNull(DemWindow(zoom = 2, tileSize = tileSize) { null }.read(0, 0, 2, 2))
    }

    @Test
    fun `wraps across the antimeridian`() {
        val fetched = mutableSetOf<TileId>()
        DemWindow(zoom = 1, tileSize = tileSize) {
            fetched += it
            tile(it)
        }.read(-1, 0, 2, 1)
        assertEquals(setOf(TileId(1, 1, 0), TileId(1, 0, 0)), fetched)
    }
}
