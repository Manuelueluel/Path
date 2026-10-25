package it.traccia.core.terrain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import it.traccia.core.geo.terrain.ElevationGrid
import it.traccia.core.geo.terrain.Terrarium
import javax.inject.Inject

/** Decodes an encoded DEM image (PNG/WebP, terrarium) into elevations. */
fun interface DemTileDecoder {
    fun decode(bytes: ByteArray): ElevationGrid?
}

internal class AndroidTerrariumDecoder
    @Inject
    constructor() : DemTileDecoder {
        override fun decode(bytes: ByteArray): ElevationGrid? {
            val options =
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    // Premultiplied alpha would corrupt the RGB-encoded elevations.
                    inPremultiplied = false
                }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            return try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                Terrarium.decodeGrid(pixels, bitmap.width, bitmap.height)
            } finally {
                bitmap.recycle()
            }
        }
    }
