// Math constants of the marching squares case table and bit masks are clearer inline than as named constants.
@file:Suppress("MagicNumber")

package it.traccia.core.geo.terrain

import kotlin.math.ceil

/**
 * One contour line in grid coordinates: interleaved `x0, y0, x1, y1, ...`.
 * [isClosed] is true when the line is a ring (first point equals the last one).
 */
class ContourLine(
    val elevation: Double,
    val coordinates: DoubleArray,
    val isClosed: Boolean,
) {
    val pointCount: Int get() = coordinates.size / 2
}

/**
 * Marching squares contouring of an [ElevationGrid].
 * Segments are stitched into continuous polylines, so labels and dashes follow the whole line.
 */
object ContourGenerator {
    fun generate(
        grid: ElevationGrid,
        interval: Double,
    ): List<ContourLine> {
        require(interval > 0) { "Interval must be positive: $interval" }
        if (grid.width < 2 || grid.height < 2) return emptyList()
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        for (v in grid.values) {
            if (v < min) min = v
            if (v > max) max = v
        }
        val lines = mutableListOf<ContourLine>()
        var level = ceil(min / interval) * interval
        while (level <= max) {
            lines += contoursAt(grid, level)
            level += interval
        }
        return lines
    }

    /** All contour lines of a single [level]. */
    fun contoursAt(
        grid: ElevationGrid,
        level: Double,
    ): List<ContourLine> {
        val segments = SegmentCollector()
        for (y in 0 until grid.height - 1) {
            for (x in 0 until grid.width - 1) {
                addCellSegments(grid, x, y, level, segments)
            }
        }
        return segments.stitch().map { edges -> toLine(grid, level, edges) }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun addCellSegments(
        grid: ElevationGrid,
        x: Int,
        y: Int,
        level: Double,
        out: SegmentCollector,
    ) {
        val tl = grid[x, y]
        val tr = grid[x + 1, y]
        val br = grid[x + 1, y + 1]
        val bl = grid[x, y + 1]
        val case =
            (if (tl >= level) 8 else 0) or
                (if (tr >= level) 4 else 0) or
                (if (br >= level) 2 else 0) or
                (if (bl >= level) 1 else 0)
        if (case == 0 || case == 15) return
        val top = horizontalEdge(grid, x, y)
        val bottom = horizontalEdge(grid, x, y + 1)
        val left = verticalEdge(grid, x, y)
        val right = verticalEdge(grid, x + 1, y)
        val centerHigh = (tl + tr + br + bl) / 4.0 >= level
        when (case) {
            1, 14 -> out.add(left, bottom)
            2, 13 -> out.add(bottom, right)
            3, 12 -> out.add(left, right)
            4, 11 -> out.add(top, right)
            6, 9 -> out.add(top, bottom)
            7, 8 -> out.add(left, top)
            5 ->
                // Saddle: high tr and bl.
                if (centerHigh) {
                    out.add(left, top)
                    out.add(bottom, right)
                } else {
                    out.add(top, right)
                    out.add(left, bottom)
                }
            10 ->
                // Saddle: high tl and br.
                if (centerHigh) {
                    out.add(top, right)
                    out.add(left, bottom)
                } else {
                    out.add(left, top)
                    out.add(bottom, right)
                }
        }
    }

    // Edge ids: horizontal edge (x,y)-(x+1,y) is even, vertical edge (x,y)-(x,y+1) is odd.
    private fun horizontalEdge(
        grid: ElevationGrid,
        x: Int,
        y: Int,
    ) = (y * grid.width + x) * 2

    private fun verticalEdge(
        grid: ElevationGrid,
        x: Int,
        y: Int,
    ) = (y * grid.width + x) * 2 + 1

    private fun toLine(
        grid: ElevationGrid,
        level: Double,
        edges: IntArray,
    ): ContourLine {
        val coords = DoubleArray(edges.size * 2)
        edges.forEachIndexed { i, edge ->
            val cell = edge / 2
            val x = cell % grid.width
            val y = cell / grid.width
            val vertical = edge % 2 == 1
            val v0 = grid[x, y]
            val v1 = if (vertical) grid[x, y + 1] else grid[x + 1, y]
            val t = if (v1 == v0) 0.5 else ((level - v0) / (v1 - v0)).coerceIn(0.0, 1.0)
            coords[i * 2] = if (vertical) x.toDouble() else x + t
            coords[i * 2 + 1] = if (vertical) y + t else y.toDouble()
        }
        return ContourLine(level, coords, isClosed = edges.size > 2 && edges.first() == edges.last())
    }

    /** Collects undirected segments between edge crossings and joins them into chains. */
    private class SegmentCollector {
        private val starts = ArrayList<Int>()
        private val ends = ArrayList<Int>()
        private val byEdge = HashMap<Int, MutableList<Int>>()

        fun add(
            a: Int,
            b: Int,
        ) {
            val index = starts.size
            starts += a
            ends += b
            byEdge.getOrPut(a) { ArrayList(2) } += index
            byEdge.getOrPut(b) { ArrayList(2) } += index
        }

        fun stitch(): List<IntArray> {
            val visited = BooleanArray(starts.size)
            val chains = mutableListOf<IntArray>()
            // Open lines first, starting from a free end (an edge touched by a single segment)...
            for ((edge, segs) in byEdge) {
                if (segs.size == 1 && !visited[segs[0]]) chains += walk(segs[0], edge, visited)
            }
            // ...then the remaining segments can only form closed rings.
            for (seg in starts.indices) {
                if (!visited[seg]) chains += walk(seg, starts[seg], visited)
            }
            return chains
        }

        private fun walk(
            firstSegment: Int,
            firstEdge: Int,
            visited: BooleanArray,
        ): IntArray {
            val path = ArrayList<Int>()
            path += firstEdge
            var segment = firstSegment
            var edge = firstEdge
            while (true) {
                visited[segment] = true
                val other = if (starts[segment] == edge) ends[segment] else starts[segment]
                path += other
                val next = byEdge[other]?.firstOrNull { it != segment && !visited[it] } ?: break
                segment = next
                edge = other
            }
            return path.toIntArray()
        }
    }
}

/** Contour spacing for a map zoom level, or null when contours would be too dense to read. */
data class ContourInterval(
    val minor: Int,
    val major: Int,
) {
    fun isMajor(elevation: Double): Boolean = elevation.toLong() % major == 0L

    companion object {
        const val MIN_ZOOM = 11

        fun forZoom(zoom: Int): ContourInterval? =
            when {
                zoom < MIN_ZOOM -> null
                zoom == 11 -> ContourInterval(minor = 100, major = 500)
                zoom == 12 -> ContourInterval(minor = 50, major = 250)
                zoom == 13 -> ContourInterval(minor = 25, major = 100)
                zoom == 14 -> ContourInterval(minor = 20, major = 100)
                else -> ContourInterval(minor = 10, major = 50)
            }
    }
}
