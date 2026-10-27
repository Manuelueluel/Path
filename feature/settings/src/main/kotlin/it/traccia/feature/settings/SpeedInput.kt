package it.traccia.feature.settings

import it.traccia.core.common.SpeedReference
import it.traccia.core.common.UnitSystem
import it.traccia.core.common.UnitSystem.Companion.METERS_PER_FOOT
import it.traccia.core.common.UnitSystem.Companion.METERS_PER_KM
import it.traccia.core.common.UnitSystem.Companion.METERS_PER_MILE
import java.util.Locale
import kotlin.math.roundToInt

/** Reference speeds as typed by the user, in the chosen units (M8.2). */
data class SpeedInput(
    val flat: String,
    val ascent: String,
    /** Null for the sports where descending does not count. */
    val descent: String?,
) {
    companion object {
        private val FLAT_RANGE_KMH = 1.0..80.0
        private val VERTICAL_RANGE_MH = 50.0..5000.0

        fun from(
            reference: SpeedReference,
            units: UnitSystem,
            usesDescent: Boolean,
            locale: Locale = Locale.getDefault(),
        ): SpeedInput =
            SpeedInput(
                flat = String.format(locale, "%.1f", reference.flatKmh * METERS_PER_KM / distanceUnit(units)),
                ascent = (reference.ascentMh / verticalUnit(units)).roundToInt().toString(),
                descent = if (usesDescent) (reference.descentMh / verticalUnit(units)).roundToInt().toString() else null,
            )

        /** km/h or mph. */
        private fun distanceUnit(units: UnitSystem) = if (units == UnitSystem.METRIC) METERS_PER_KM else METERS_PER_MILE

        /** m/h or ft/h. */
        private fun verticalUnit(units: UnitSystem) = if (units == UnitSystem.METRIC) 1.0 else METERS_PER_FOOT

        private fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
    }

    /** The speeds in metric units, or null when a value is missing or out of a plausible range. */
    fun toReference(
        units: UnitSystem,
        default: SpeedReference,
    ): SpeedReference? {
        val flatKmh = parse(flat)?.times(distanceUnit(units) / METERS_PER_KM) ?: return null
        val ascentMh = parse(ascent)?.times(verticalUnit(units)) ?: return null
        val descentMh =
            if (descent == null) {
                default.descentMh
            } else {
                parse(descent)?.times(verticalUnit(units)) ?: return null
            }
        val valid =
            flatKmh in FLAT_RANGE_KMH &&
                ascentMh in VERTICAL_RANGE_MH &&
                (descent == null || descentMh in VERTICAL_RANGE_MH)
        return if (valid) SpeedReference(flatKmh, ascentMh, descentMh) else null
    }
}
