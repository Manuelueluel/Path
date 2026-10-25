package it.traccia.feature.elevation

import it.traccia.core.common.Sport
import kotlin.math.max
import kotlin.math.min

/**
 * Moving time estimate per sport (M5.8). Reference speeds become configurable in M8.2.
 * - On foot (hiking, ski touring): DIN 33466 / CAI rule, horizontal and vertical times combined.
 * - Running and cycling: flat speed plus a time penalty per meter of climbing.
 */
object DurationEstimate {
    private const val SECONDS_PER_HOUR = 3600.0
    private const val METERS_PER_KM = 1000.0

    private data class Reference(
        val flatKmh: Double,
        val ascentMh: Double,
        val descentMh: Double,
    )

    private val references =
        mapOf(
            Sport.HIKING to Reference(flatKmh = 4.0, ascentMh = 300.0, descentMh = 500.0),
            Sport.SKI_TOURING to Reference(flatKmh = 4.0, ascentMh = 350.0, descentMh = 1500.0),
            Sport.TRAIL_RUNNING to Reference(flatKmh = 8.5, ascentMh = 700.0, descentMh = 0.0),
            Sport.RUNNING to Reference(flatKmh = 10.5, ascentMh = 800.0, descentMh = 0.0),
            Sport.ROAD_CYCLING to Reference(flatKmh = 25.0, ascentMh = 1000.0, descentMh = 0.0),
            Sport.GRAVEL to Reference(flatKmh = 18.0, ascentMh = 700.0, descentMh = 0.0),
            Sport.MTB to Reference(flatKmh = 12.0, ascentMh = 500.0, descentMh = 0.0),
        )

    fun seconds(
        sport: Sport,
        distanceMeters: Double,
        ascentMeters: Double,
        descentMeters: Double,
    ): Double {
        val ref = references.getValue(sport)
        val horizontal = distanceMeters / METERS_PER_KM / ref.flatKmh
        return when (sport) {
            Sport.HIKING, Sport.SKI_TOURING -> {
                val vertical = ascentMeters / ref.ascentMh + descentMeters / ref.descentMh
                (max(horizontal, vertical) + min(horizontal, vertical) / 2) * SECONDS_PER_HOUR
            }
            else -> (horizontal + ascentMeters / ref.ascentMh) * SECONDS_PER_HOUR
        }
    }
}
