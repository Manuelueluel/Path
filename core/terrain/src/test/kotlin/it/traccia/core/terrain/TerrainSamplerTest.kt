package it.traccia.core.terrain

import it.traccia.core.geo.TileId
import it.traccia.core.geo.WebMercator
import it.traccia.core.geo.terrain.ElevationGrid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

class TerrainSamplerTest {
    private val tileSize = 64
    private val maxZoom = 10

    /** Synthetic DEM: a ramp eastwards, 100 m per world pixel at the max zoom. */
    private val ramp: (TileId) -> ElevationGrid = { id ->
        val scale = 2.0.pow(maxZoom - id.zoom)
        ElevationGrid(
            tileSize,
            tileSize,
            FloatArray(tileSize * tileSize) { i ->
                (
                    (id.x * tileSize + i % tileSize) * scale *
                        METERS_PER_PIXEL
                ).toFloat()
            },
        )
    }

    @Test
    fun `point elevation interpolates the DEM`() {
        val sampler = TerrainSampler(tileSize, maxZoom, ramp)
        val world = WebMercator.worldSize(maxZoom.toDouble(), tileSize)
        val lon = 9.47
        val expected = (WebMercator.lonToX(lon, world) - 0.5) * METERS_PER_PIXEL
        assertEquals(expected, checkNotNull(sampler.elevationAt(45.86, lon)), 0.1)
    }

    @Test
    fun `unavailable tile gives null elevation`() {
        assertNull(TerrainSampler(tileSize, maxZoom) { null }.elevationAt(45.0, 9.0))
    }

    @Test
    fun `no contours below the minimum zoom`() {
        assertEquals(emptyList<GeoContour>(), TerrainSampler(tileSize, maxZoom, ramp).contours(9.0, 45.0, 9.1, 45.1, zoom = 8))
    }

    @Test
    fun `contours of an eastward ramp run north-south inside the tile`() {
        val sampler = TerrainSampler(tileSize, maxZoom, ramp)
        val west = 9.40
        val east = 9.45
        val contours = checkNotNull(sampler.contours(west, 45.80, east, 45.85, zoom = 12))
        assertTrue(contours.isNotEmpty())
        contours.forEach { c ->
            val lons = (0 until c.lonLat.size / 2).map { c.lonLat[it * 2] }
            // Constant elevation along a line of constant longitude.
            assertEquals(lons.min(), lons.max(), 1e-6)
            assertTrue(lons.first() in (west - 0.01)..(east + 0.01))
            assertEquals(c.elevation % 50.0 == 0.0 && c.elevation % 250.0 == 0.0, c.isMajor)
        }
    }

    @Test
    fun `missing DEM gives null contours`() {
        assertNull(TerrainSampler(tileSize, maxZoom) { null }.contours(9.0, 45.0, 9.1, 45.1, zoom = 13))
    }

    @Test
    fun `consecutive duplicate points are removed`() {
        val coords = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 2.0, 0.0)
        assertEquals(listOf(0.0, 0.0, 1.0, 1.0, 2.0, 0.0), removeDuplicatePoints(coords).toList())
    }

    @Test
    fun `degenerate lines are dropped`() {
        // DEM exactly at the contour level on a single pixel, lower elsewhere: the level touches one point only.
        val spike: (TileId) -> ElevationGrid = {
            ElevationGrid(
                tileSize,
                tileSize,
                FloatArray(tileSize * tileSize) { 0f }.also { a ->
                    a[0] =
                        50f
                },
            )
        }
        val contours = checkNotNull(TerrainSampler(tileSize, maxZoom, spike).contours(9.0, 45.0, 9.5, 45.5, zoom = 12))
        contours.forEach { assertTrue(it.lonLat.size >= 4) }
    }

    private companion object {
        const val METERS_PER_PIXEL = 100.0
    }
}
