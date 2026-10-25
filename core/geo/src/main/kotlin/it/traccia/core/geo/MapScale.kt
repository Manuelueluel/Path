package it.traccia.core.geo

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** A scale bar: [meters] of ground represented by a bar [widthDp] wide. */
data class ScaleBarSpec(
    val meters: Double,
    val widthDp: Float,
)

object MapScale {
    /** MapLibre renders 512 logical pixels (dp) per tile. */
    const val MAPLIBRE_TILE_SIZE = 512

    private val niceSteps = doubleArrayOf(1.0, 2.0, 5.0)

    fun metersPerDp(
        latitude: Double,
        zoom: Double,
    ): Double = WebMercator.metersPerPixel(latitude, WebMercator.worldSize(zoom, MAPLIBRE_TILE_SIZE))

    /** Largest "round" distance (1, 2 or 5 × 10ⁿ meters) whose bar fits in [maxWidthDp]. */
    fun scaleBar(
        metersPerDp: Double,
        maxWidthDp: Float,
    ): ScaleBarSpec {
        require(metersPerDp > 0 && maxWidthDp > 0) { "Invalid scale input" }
        val maxMeters = metersPerDp * maxWidthDp
        val magnitude = 10.0.pow(floor(log10(maxMeters)))
        val step = niceSteps.last { it * magnitude <= maxMeters }
        val meters = step * magnitude
        return ScaleBarSpec(meters = meters, widthDp = (meters / metersPerDp).toFloat())
    }
}
