package it.traccia.core.common

/** Units shown in the UI. Everything is stored in meters; stored by [name]: never rename entries. */
enum class UnitSystem {
    METRIC,
    IMPERIAL,
    ;

    companion object {
        const val METERS_PER_FOOT = 0.3048
        const val METERS_PER_MILE = 1609.344
        const val METERS_PER_KM = 1000.0
    }
}
