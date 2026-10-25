package it.traccia.core.terrain

import it.traccia.core.geo.TileId
import it.traccia.core.geo.WebMercator
import it.traccia.core.geo.terrain.ContourGenerator
import it.traccia.core.geo.terrain.ContourInterval
import it.traccia.core.geo.terrain.DemWindow
import it.traccia.core.geo.terrain.ElevationGrid
import it.traccia.core.geo.terrain.smoothChaikin
import kotlin.math.ceil
import kotlin.math.floor

/** Drops consecutive repeated points from interleaved `x, y` coordinates. */
internal fun removeDuplicatePoints(coordinates: DoubleArray): DoubleArray {
    if (coordinates.size <= 2) return coordinates
    val out = ArrayList<Double>(coordinates.size)
    out += coordinates[0]
    out += coordinates[1]
    for (i in 1 until coordinates.size / 2) {
        val x = coordinates[i * 2]
        val y = coordinates[i * 2 + 1]
        if (x != out[out.size - 2] || y != out[out.size - 1]) {
            out += x
            out += y
        }
    }
    return out.toDoubleArray()
}

/** A contour line in geographic coordinates: interleaved `lon, lat`. */
class GeoContour(
    val elevation: Double,
    val isMajor: Boolean,
    val lonLat: DoubleArray,
)

/**
 * Terrain queries on top of a DEM tile source: point elevation and contour lines for a map tile.
 * Pure Kotlin apart from the tile source, so it is unit-testable with synthetic tiles.
 */
class TerrainSampler(
    private val tileSize: Int,
    private val maxDemZoom: Int,
    private val fetchTile: (TileId) -> ElevationGrid?,
) {
    /** Elevation in meters at a point, using the most detailed DEM zoom; null when the tile is unavailable. */
    fun elevationAt(
        latitude: Double,
        longitude: Double,
    ): Double? {
        val world = WebMercator.worldSize(maxDemZoom.toDouble(), tileSize)
        // Grid values sit at pixel centers.
        val px = WebMercator.lonToX(longitude, world) - HALF_PIXEL
        val py = WebMercator.latToY(latitude, world) - HALF_PIXEL
        val tx = floor((px + HALF_PIXEL) / tileSize).toInt()
        val ty = floor((py + HALF_PIXEL) / tileSize).toInt()
        val tile = fetchTile(TileId(maxDemZoom, tx, ty)) ?: return null
        return tile.sample(px - tx * tileSize, py - ty * tileSize)
    }

    /**
     * Contours for the map tile covering the given bounds at [zoom].
     * @return null when the DEM is unavailable, empty when the zoom is too low for contours.
     */
    fun contours(
        west: Double,
        south: Double,
        east: Double,
        north: Double,
        zoom: Int,
    ): List<GeoContour>? {
        val interval = ContourInterval.forZoom(zoom) ?: return emptyList()
        val demZoom = minOf(zoom, maxDemZoom)
        val world = WebMercator.worldSize(demZoom.toDouble(), tileSize)
        // One pixel of margin on each side so lines continue into the neighbouring tiles.
        val x0 = floor(WebMercator.lonToX(west, world) - HALF_PIXEL).toInt() - 1
        val x1 = ceil(WebMercator.lonToX(east, world) - HALF_PIXEL).toInt() + 1
        val y0 = floor(WebMercator.latToY(north, world) - HALF_PIXEL).toInt() - 1
        val y1 = ceil(WebMercator.latToY(south, world) - HALF_PIXEL).toInt() + 1
        val grid = DemWindow(demZoom, tileSize, fetchTile).read(x0, y0, x1 - x0 + 1, y1 - y0 + 1)
        // Above the DEM resolution, smooth the pixel-sized steps.
        val smoothing = (zoom - demZoom).coerceIn(0, MAX_SMOOTHING)
        return grid?.let { ContourGenerator.generate(it, interval.minor.toDouble()) }?.mapNotNull { line ->
            val coords = smoothChaikin(removeDuplicatePoints(line.coordinates), line.isClosed, smoothing)
            // Contours touching a DEM pixel exactly can collapse to a single point: not a valid line.
            if (coords.size < MIN_LINE_COORDS) return@mapNotNull null
            val lonLat = DoubleArray(coords.size)
            for (i in 0 until coords.size / 2) {
                lonLat[i * 2] = WebMercator.xToLon(x0 + coords[i * 2] + HALF_PIXEL, world)
                lonLat[i * 2 + 1] = WebMercator.yToLat(y0 + coords[i * 2 + 1] + HALF_PIXEL, world)
            }
            GeoContour(line.elevation, interval.isMajor(line.elevation), lonLat)
        }
    }

    private companion object {
        const val MIN_LINE_COORDS = 4
        const val HALF_PIXEL = 0.5
        const val MAX_SMOOTHING = 2
    }
}
