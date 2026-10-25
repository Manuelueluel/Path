package it.traccia.feature.elevation

import kotlin.math.abs

/** Gradient classes used to colour chart and map (M6.5). Same colours for up and down. */
enum class SlopeClass(
    val maxPercent: Double,
    /** ARGB colour, shared by the Compose chart and the MapLibre line. */
    val color: Long,
) {
    FLAT(5.0, 0xFF4CAF50),
    MODERATE(10.0, 0xFFCDDC39),
    STEEP(15.0, 0xFFFFA000),
    VERY_STEEP(25.0, 0xFFE64A19),
    EXTREME(Double.MAX_VALUE, 0xFF8E1B4E),
    ;

    companion object {
        fun of(gradePercent: Double): SlopeClass = entries.first { abs(gradePercent) < it.maxPercent }
    }
}
