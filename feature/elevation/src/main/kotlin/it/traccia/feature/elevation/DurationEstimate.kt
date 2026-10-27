package it.traccia.feature.elevation

import it.traccia.core.common.SpeedReference
import it.traccia.core.common.Sport
import kotlin.math.max
import kotlin.math.min

/**
 * Moving time estimate per sport (M5.8), from the user's reference speeds (M8.2).
 * - On foot (hiking, ski touring): DIN 33466 / CAI rule, horizontal and vertical times combined.
 * - Running and cycling: flat speed plus a time penalty per meter of climbing.
 */
object DurationEstimate {
    private const val SECONDS_PER_HOUR = 3600.0
    private const val METERS_PER_KM = 1000.0

    fun seconds(
        sport: Sport,
        distanceMeters: Double,
        ascentMeters: Double,
        descentMeters: Double,
        reference: SpeedReference = SpeedReference.defaultFor(sport),
    ): Double {
        val horizontal = distanceMeters / METERS_PER_KM / reference.flatKmh
        return when (sport) {
            Sport.HIKING, Sport.SKI_TOURING -> {
                val vertical = ascentMeters / reference.ascentMh + descentMeters / reference.descentMh
                (max(horizontal, vertical) + min(horizontal, vertical) / 2) * SECONDS_PER_HOUR
            }
            else -> (horizontal + ascentMeters / reference.ascentMh) * SECONDS_PER_HOUR
        }
    }

    /** Whether descending counts for [sport]: only then the descent rate is shown and edited. */
    fun usesDescent(sport: Sport): Boolean = sport == Sport.HIKING || sport == Sport.SKI_TOURING
}
