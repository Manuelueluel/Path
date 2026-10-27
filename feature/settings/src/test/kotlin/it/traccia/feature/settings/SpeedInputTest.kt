package it.traccia.feature.settings

import it.traccia.core.common.SpeedReference
import it.traccia.core.common.Sport
import it.traccia.core.common.UnitSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Locale

class SpeedInputTest {
    private val hiking = SpeedReference.defaultFor(Sport.HIKING)

    @Test
    fun `metric values are shown as typed`() {
        assertEquals(SpeedInput("4,0", "300", "500"), SpeedInput.from(hiking, UnitSystem.METRIC, true, Locale.ITALY))
        assertEquals(SpeedInput("4.0", "300", null), SpeedInput.from(hiking, UnitSystem.METRIC, false, Locale.ROOT))
    }

    @Test
    fun `imperial input is converted back to metric`() {
        val input = SpeedInput.from(hiking, UnitSystem.IMPERIAL, true, Locale.ROOT)
        assertEquals(SpeedInput("2.5", "984", "1640"), input)
        val reference = input.toReference(UnitSystem.IMPERIAL, hiking) ?: error("invalid")
        assertEquals(4.02, reference.flatKmh, 0.01)
        assertEquals(300.0, reference.ascentMh, 0.5)
    }

    @Test
    fun `comma decimals are accepted, implausible values are not`() {
        assertEquals(4.5, SpeedInput("4,5", "300", "500").toReference(UnitSystem.METRIC, hiking)?.flatKmh)
        assertNull(SpeedInput("0", "300", "500").toReference(UnitSystem.METRIC, hiking))
        assertNull(SpeedInput("4", "", "500").toReference(UnitSystem.METRIC, hiking))
        assertNull(SpeedInput("4", "300", "99999").toReference(UnitSystem.METRIC, hiking))
    }

    @Test
    fun `without a descent field the default descent is kept`() {
        val mtb = SpeedReference.defaultFor(Sport.MTB)
        assertEquals(mtb.descentMh, SpeedInput("14", "600", null).toReference(UnitSystem.METRIC, mtb)?.descentMh)
    }
}
