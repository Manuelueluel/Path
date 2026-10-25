package it.traccia.core.geo.terrain

import it.traccia.core.geo.TileId
import kotlin.math.floor

/**
 * Builds an [ElevationGrid] covering an arbitrary rectangle of DEM world pixels at [zoom],
 * stitching together all the DEM tiles it touches. This lets contours cross tile borders seamlessly.
 *
 * @param fetchTile returns the decoded tile, or null when unavailable (the window is then incomplete).
 */
class DemWindow(
    val zoom: Int,
    val tileSize: Int,
    private val fetchTile: (TileId) -> ElevationGrid?,
) {
    /**
     * @return the grid whose cell (0, 0) is DEM world pixel ([originX], [originY]), or null when a tile is missing.
     */
    fun read(
        originX: Int,
        originY: Int,
        width: Int,
        height: Int,
    ): ElevationGrid? {
        val tilesPerSide = 1 shl zoom
        val out = FloatArray(width * height)
        val firstTx = floor(originX.toDouble() / tileSize).toInt()
        val lastTx = floor((originX + width - 1).toDouble() / tileSize).toInt()
        val firstTy = floor(originY.toDouble() / tileSize).toInt()
        val lastTy = floor((originY + height - 1).toDouble() / tileSize).toInt()
        for (ty in firstTy..lastTy) {
            for (tx in firstTx..lastTx) {
                // Wrap horizontally across the antimeridian; clamp vertically at the poles.
                val wrappedX = Math.floorMod(tx, tilesPerSide)
                val clampedY = ty.coerceIn(0, tilesPerSide - 1)
                val tile = fetchTile(TileId(zoom, wrappedX, clampedY)) ?: return null
                copyTile(tile, tx, ty, originX, originY, width, height, out)
            }
        }
        return ElevationGrid(width, height, out)
    }

    @Suppress("LongParameterList")
    private fun copyTile(
        tile: ElevationGrid,
        tx: Int,
        ty: Int,
        originX: Int,
        originY: Int,
        width: Int,
        height: Int,
        out: FloatArray,
    ) {
        val tileLeft = tx * tileSize
        val tileTop = ty * tileSize
        val fromX = maxOf(originX, tileLeft)
        val toX = minOf(originX + width, tileLeft + tileSize)
        val fromY = maxOf(originY, tileTop)
        val toY = minOf(originY + height, tileTop + tileSize)
        for (gy in fromY until toY) {
            val srcY = (gy - tileTop).coerceIn(0, tile.height - 1)
            for (gx in fromX until toX) {
                out[(gy - originY) * width + (gx - originX)] = tile[gx - tileLeft, srcY]
            }
        }
    }
}
