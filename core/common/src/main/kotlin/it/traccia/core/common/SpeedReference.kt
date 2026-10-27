package it.traccia.core.common

/**
 * Reference speeds of a sport, used to estimate moving times (M5.8, M8.2).
 * On foot both climbing and descending rates count; on wheels and running only climbing slows down.
 */
data class SpeedReference(
    val flatKmh: Double,
    val ascentMh: Double,
    val descentMh: Double,
) {
    companion object {
        /** Typical values: CAI / DIN 33466 for hiking, average amateur paces for the others. */
        fun defaultFor(sport: Sport): SpeedReference =
            when (sport) {
                Sport.HIKING -> SpeedReference(flatKmh = 4.0, ascentMh = 300.0, descentMh = 500.0)
                Sport.SKI_TOURING -> SpeedReference(flatKmh = 4.0, ascentMh = 350.0, descentMh = 1500.0)
                Sport.TRAIL_RUNNING -> SpeedReference(flatKmh = 8.5, ascentMh = 700.0, descentMh = 0.0)
                Sport.RUNNING -> SpeedReference(flatKmh = 10.5, ascentMh = 800.0, descentMh = 0.0)
                Sport.ROAD_CYCLING -> SpeedReference(flatKmh = 25.0, ascentMh = 1000.0, descentMh = 0.0)
                Sport.GRAVEL -> SpeedReference(flatKmh = 18.0, ascentMh = 700.0, descentMh = 0.0)
                Sport.MTB -> SpeedReference(flatKmh = 12.0, ascentMh = 500.0, descentMh = 0.0)
            }
    }
}
