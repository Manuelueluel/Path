package it.traccia.core.geo

import kotlin.math.roundToLong

/**
 * Encoded polyline (Google algorithm) with 1e6 precision: compact storage for route geometry.
 * Elevations are not encoded: they always come from the DEM.
 */
object Polyline {
    private const val PRECISION = 1e6
    private const val CHUNK_BITS = 5
    private const val CHUNK_MASK = 0x1f
    private const val CONTINUATION = 0x20
    private const val ASCII_OFFSET = 63

    fun encode(points: List<GeoPoint>): String {
        val out = StringBuilder()
        var lastLat = 0L
        var lastLon = 0L
        points.forEach { p ->
            val lat = (p.latitude * PRECISION).roundToLong()
            val lon = (p.longitude * PRECISION).roundToLong()
            encodeValue(lat - lastLat, out)
            encodeValue(lon - lastLon, out)
            lastLat = lat
            lastLon = lon
        }
        return out.toString()
    }

    fun decode(encoded: String): List<GeoPoint> {
        val points = ArrayList<GeoPoint>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            val (dLat, afterLat) = decodeValue(encoded, index)
            val (dLon, afterLon) = decodeValue(encoded, afterLat)
            index = afterLon
            lat += dLat
            lon += dLon
            points += GeoPoint(lat / PRECISION, lon / PRECISION)
        }
        return points
    }

    private fun encodeValue(
        value: Long,
        out: StringBuilder,
    ) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= CONTINUATION) {
            out.append(((CONTINUATION or (v and CHUNK_MASK.toLong()).toInt()) + ASCII_OFFSET).toChar())
            v = v shr CHUNK_BITS
        }
        out.append((v + ASCII_OFFSET).toInt().toChar())
    }

    private fun decodeValue(
        encoded: String,
        start: Int,
    ): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var index = start
        while (true) {
            require(index < encoded.length) { "Truncated polyline" }
            val b = encoded[index++].code - ASCII_OFFSET
            result = result or ((b and CHUNK_MASK).toLong() shl shift)
            shift += CHUNK_BITS
            if (b < CONTINUATION) break
        }
        val value = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return value to index
    }
}
