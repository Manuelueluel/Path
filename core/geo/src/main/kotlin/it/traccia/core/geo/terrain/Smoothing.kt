// Math constants of the Chaikin weights are clearer inline than as named constants.
@file:Suppress("MagicNumber")

package it.traccia.core.geo.terrain

/**
 * Chaikin corner cutting on interleaved `x, y` coordinates.
 * Open lines keep their end points; closed rings (first == last) stay closed.
 */
fun smoothChaikin(
    coordinates: DoubleArray,
    isClosed: Boolean,
    iterations: Int,
): DoubleArray {
    var current = coordinates
    repeat(iterations) {
        val n = current.size / 2
        if (n < 3) return current
        val out = ArrayList<Double>(n * 4)
        if (!isClosed) {
            out += current[0]
            out += current[1]
        }
        for (i in 0 until n - 1) {
            val x0 = current[i * 2]
            val y0 = current[i * 2 + 1]
            val x1 = current[i * 2 + 2]
            val y1 = current[i * 2 + 3]
            out += 0.75 * x0 + 0.25 * x1
            out += 0.75 * y0 + 0.25 * y1
            out += 0.25 * x0 + 0.75 * x1
            out += 0.25 * y0 + 0.75 * y1
        }
        if (isClosed) {
            out += out[0]
            out += out[1]
        } else {
            out += current[current.size - 2]
            out += current[current.size - 1]
        }
        current = out.toDoubleArray()
    }
    return current
}
