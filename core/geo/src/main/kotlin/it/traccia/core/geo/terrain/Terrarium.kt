// Math constants of the encoding formula are clearer inline than as named constants.
@file:Suppress("MagicNumber")

package it.traccia.core.geo.terrain

/**
 * Terrarium DEM encoding (Mapzen / Mapterhorn): `elevation = R * 256 + G + B / 256 - 32768`.
 */
object Terrarium {
    private const val OFFSET = 32768f
    private const val BYTE = 0xFF

    fun decode(argb: Int): Float {
        val r = (argb shr 16) and BYTE
        val g = (argb shr 8) and BYTE
        val b = argb and BYTE
        return r * 256f + g + b / 256f - OFFSET
    }

    fun decodeGrid(
        argbPixels: IntArray,
        width: Int,
        height: Int,
    ): ElevationGrid = ElevationGrid(width, height, FloatArray(argbPixels.size) { decode(argbPixels[it]) })
}
