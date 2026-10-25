package it.traccia.core.geo.terrain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TerrariumTest {
    private fun argb(
        r: Int,
        g: Int,
        b: Int,
    ) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `sea level is 128 0 0`() {
        assertEquals(0f, Terrarium.decode(argb(128, 0, 0)))
    }

    @Test
    fun `decodes positive elevation with fraction`() {
        // 1875.5 m = 32768 + 1875.5 = 34643.5 -> R=135 (34560), G=83, B=128
        assertEquals(1875.5f, Terrarium.decode(argb(135, 83, 128)))
    }

    @Test
    fun `decodes below sea level`() {
        assertEquals(-10f, Terrarium.decode(argb(127, 246, 0)))
    }

    @Test
    fun `alpha channel is ignored`() {
        assertEquals(Terrarium.decode(argb(130, 10, 0)), Terrarium.decode(argb(130, 10, 0) and 0x00FFFFFF))
    }
}
