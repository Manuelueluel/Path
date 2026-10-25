package it.traccia.feature.routing

import btools.router.OsmNodeNamed
import btools.router.RoutingContext
import btools.router.RoutingEngine
import it.traccia.core.geo.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.runInterruptible
import java.io.File
import kotlin.math.floor
import kotlin.math.roundToInt

/** Why a segment could not be routed: the planner then draws a dashed straight line (M4.6). */
class RoutingException(
    message: String,
) : Exception(message)

/**
 * Thin wrapper around the BRouter engine: one route between two points, offline, cancellable (M4.1, M4.5).
 * Pure JVM, so it is tested against a real segment file.
 */
class BRouterEngine(
    private val profileDir: File,
) {
    /**
     * @throws RoutingException when BRouter finds no route (missing segment data, unreachable point...).
     */
    suspend fun route(
        from: GeoPoint,
        to: GeoPoint,
        profile: String,
        parameters: Map<String, String>,
        segmentDir: File,
    ): List<GeoPoint> {
        val context =
            RoutingContext().apply {
                localFunction = File(profileDir, "$profile.brf").absolutePath
                keyValues = HashMap(parameters)
                memoryclass = MEMORY_CLASS_MB
            }
        val engine = RoutingEngine(null, null, segmentDir, listOf(node(from, "from"), node(to, "to")), context)
        engine.quite = true
        // Cancelling the coroutine stops the search (e.g. the user moved the point again).
        val handle = currentCoroutineContext().job.invokeOnCompletion { engine.terminate() }
        try {
            runInterruptible(Dispatchers.Default) { engine.doRun(TIMEOUT_MS) }
        } finally {
            handle.dispose()
        }
        // On failure BRouter sets an error message but may still return an empty track.
        val track = engine.foundTrack
        val error = engine.errorMessage
        if (error != null || track == null || track.nodes.size < 2) throw RoutingException(error ?: "No route found")
        return track.nodes.map { GeoPoint(toDegrees(it.iLat) - LAT_OFFSET, toDegrees(it.iLon) - LON_OFFSET) }
    }

    private fun node(
        p: GeoPoint,
        label: String,
    ) = OsmNodeNamed().apply {
        name = label
        ilon = ((p.longitude + LON_OFFSET) * MICRO).roundToInt()
        ilat = ((p.latitude + LAT_OFFSET) * MICRO).roundToInt()
    }

    private fun toDegrees(micro: Int) = micro / MICRO

    companion object {
        // BRouter stores coordinates as micro-degrees shifted to be positive.
        private const val MICRO = 1e6
        private const val LON_OFFSET = 180.0
        private const val LAT_OFFSET = 90.0
        private const val TIMEOUT_MS = 60_000L
        private const val MEMORY_CLASS_MB = 128
        private const val TILE_DEGREES = 5

        /** Name of the BRouter segment file covering [p], e.g. `E5_N45.rd5`. */
        fun segmentFileName(p: GeoPoint): String {
            val lon = floor(p.longitude / TILE_DEGREES).toInt() * TILE_DEGREES
            val lat = floor(p.latitude / TILE_DEGREES).toInt() * TILE_DEGREES
            val ew = if (lon < 0) "W${-lon}" else "E$lon"
            val ns = if (lat < 0) "S${-lat}" else "N$lat"
            return "${ew}_$ns.rd5"
        }
    }
}
