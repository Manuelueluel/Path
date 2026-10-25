package it.traccia.core.geo.terrain

import kotlin.math.floor

/**
 * Regular grid of elevations in meters, row-major, `values[y * width + x]`.
 * Cell (x, y) represents the elevation at the center of DEM pixel (x, y).
 */
class ElevationGrid(
    val width: Int,
    val height: Int,
    val values: FloatArray,
) {
    init {
        require(width > 0 && height > 0) { "Empty grid ${width}x$height" }
        require(values.size == width * height) { "Expected ${width * height} values, got ${values.size}" }
    }

    operator fun get(
        x: Int,
        y: Int,
    ): Float = values[y * width + x]

    /**
     * Bilinear interpolation at fractional grid coordinates, where integer values are pixel centers.
     * Coordinates outside the grid are clamped to the border.
     */
    fun sample(
        fx: Double,
        fy: Double,
    ): Double {
        val x = fx.coerceIn(0.0, (width - 1).toDouble())
        val y = fy.coerceIn(0.0, (height - 1).toDouble())
        val x0 = floor(x).toInt().coerceAtMost(width - 2).coerceAtLeast(0)
        val y0 = floor(y).toInt().coerceAtMost(height - 2).coerceAtLeast(0)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val tx = x - x0
        val ty = y - y0
        val top = this[x0, y0] * (1 - tx) + this[x1, y0] * tx
        val bottom = this[x0, y1] * (1 - tx) + this[x1, y1] * tx
        return top * (1 - ty) + bottom * ty
    }
}
