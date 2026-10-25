package it.traccia.feature.elevation

import it.traccia.core.geo.GeoPoint
import it.traccia.core.geo.resample
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ProfilePoint(
    val distanceMeters: Double,
    /** Smoothed elevation (M6.2). */
    val elevationMeters: Double,
    val point: GeoPoint,
)

/** A stretch of the route with its average gradient (M6.6). */
data class SteepSection(
    val startMeters: Double,
    val endMeters: Double,
    /** Signed percentage: negative when descending. */
    val gradePercent: Double,
)

data class ElevationProfile(
    val points: List<ProfilePoint>,
    val ascentMeters: Double,
    val descentMeters: Double,
    val minElevation: Double,
    val maxElevation: Double,
    /** Steepest gradient over [ProfileBuilder.GRADE_WINDOW_M], in absolute percent. */
    val maxGradePercent: Double,
    val steepest: List<SteepSection>,
) {
    val lengthMeters: Double get() = points.lastOrNull()?.distanceMeters ?: 0.0

    /** Profile point nearest to [distance] along the route. */
    fun at(distance: Double): ProfilePoint? = points.minByOrNull { abs(it.distanceMeters - distance) }

    /** Signed gradient (%) around each point, for colouring (M6.5). */
    fun grades(): List<Double> =
        points.indices.map { i ->
            val a = points[max(0, i - 1)]
            val b = points[min(points.lastIndex, i + 1)]
            val run = b.distanceMeters - a.distanceMeters
            if (run > 0) (b.elevationMeters - a.elevationMeters) / run * PERCENT else 0.0
        }
}

/** Builds profiles from a route geometry and a DEM lookup (M6.1, M6.2, M6.6). Pure: unit-tested. */
object ProfileBuilder {
    const val STEP_M = 25.0

    /** Moving average over ~100 m removes the DEM noise without flattening real climbs. */
    private const val SMOOTHING_SAMPLES = 2

    /** Ups and downs smaller than this are noise, not climbing. */
    private const val HYSTERESIS_M = 3.0
    const val GRADE_WINDOW_M = 100.0
    private const val SECTION_M = 300.0
    private const val SECTIONS = 3

    /** @return null when the DEM has no data for the whole route. */
    fun build(
        track: List<GeoPoint>,
        elevationAt: (GeoPoint) -> Double?,
    ): ElevationProfile? {
        if (track.size < 2) return null
        val samples = track.resample(STEP_M)
        val raw = fillGaps(samples.map { elevationAt(it.point) }) ?: return null
        val smoothed = movingAverage(raw, SMOOTHING_SAMPLES)
        val points = samples.mapIndexed { i, s -> ProfilePoint(s.distanceMeters, smoothed[i], s.point) }
        val (ascent, descent) = climb(smoothed, HYSTERESIS_M)
        return ElevationProfile(
            points = points,
            ascentMeters = ascent,
            descentMeters = descent,
            minElevation = smoothed.min(),
            maxElevation = smoothed.max(),
            maxGradePercent = windows(points, GRADE_WINDOW_M).maxOfOrNull { abs(it.gradePercent) } ?: 0.0,
            steepest = steepestSections(points),
        )
    }

    /** Missing DEM values are interpolated from their neighbours. */
    internal fun fillGaps(values: List<Double?>): List<Double>? {
        val known = values.indices.filter { values[it] != null }
        if (known.isEmpty()) return null
        return values.indices.map { i ->
            values[i] ?: run {
                val before = known.lastOrNull { it < i }
                val after = known.firstOrNull { it > i }
                when {
                    before == null -> checkNotNull(values[checkNotNull(after)])
                    after == null -> checkNotNull(values[before])
                    else -> {
                        val a = checkNotNull(values[before])
                        val b = checkNotNull(values[after])
                        a + (b - a) * (i - before) / (after - before)
                    }
                }
            }
        }
    }

    internal fun movingAverage(
        values: List<Double>,
        halfWindow: Int,
    ): List<Double> =
        values.indices.map { i ->
            // Symmetric window that shrinks at the ends: start and arrival elevations stay exact.
            val half = minOf(halfWindow, i, values.lastIndex - i)
            (i - half..i + half).sumOf { values[it] } / (2 * half + 1)
        }

    /** Total ascent and descent, counting a change only once it exceeds [threshold]. */
    internal fun climb(
        values: List<Double>,
        threshold: Double,
    ): Pair<Double, Double> {
        var ascent = 0.0
        var descent = 0.0
        var reference = values.firstOrNull() ?: return 0.0 to 0.0
        values.drop(1).forEach { v ->
            val delta = v - reference
            if (delta >= threshold) {
                ascent += delta
                reference = v
            } else if (delta <= -threshold) {
                descent -= delta
                reference = v
            }
        }
        // The last stretch below the threshold is still real (the arrival elevation is exact).
        val rest = values.last() - reference
        if (rest > 0) ascent += rest else descent -= rest
        return ascent to descent
    }

    /** Average gradient of consecutive windows of about [length] meters. */
    private fun windows(
        points: List<ProfilePoint>,
        length: Double,
    ): List<SteepSection> {
        val out = ArrayList<SteepSection>()
        var start = 0
        for (end in points.indices) {
            while (points[end].distanceMeters - points[start].distanceMeters > length) start++
            val run = points[end].distanceMeters - points[start].distanceMeters
            if (run >= length * MIN_WINDOW_FRACTION) {
                out +=
                    SteepSection(
                        points[start].distanceMeters,
                        points[end].distanceMeters,
                        (points[end].elevationMeters - points[start].elevationMeters) / run * PERCENT,
                    )
            }
        }
        return out
    }

    /** The [SECTIONS] steepest non-overlapping stretches of about [SECTION_M] meters. */
    private fun steepestSections(points: List<ProfilePoint>): List<SteepSection> {
        val chosen = ArrayList<SteepSection>()
        windows(points, SECTION_M)
            .sortedByDescending { abs(it.gradePercent) }
            .forEach { candidate ->
                if (chosen.size < SECTIONS &&
                    chosen.none { it.startMeters < candidate.endMeters && candidate.startMeters < it.endMeters }
                ) {
                    chosen += candidate
                }
            }
        return chosen.sortedBy { it.startMeters }
    }

    private const val MIN_WINDOW_FRACTION = 0.9
}

private const val PERCENT = 100.0
