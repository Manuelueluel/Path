package it.traccia.core.terrain

import okhttp3.Interceptor
import okhttp3.Response

/** A heatmap tile request on the virtual host: `/heatmap/{sport}/{color}/{z}/{x}/{y}.png`. */
data class HeatmapRequest(
    val sport: String,
    val color: String,
    val z: Int,
    val x: Int,
    val y: Int,
)

/**
 * Serves heatmap tiles for [TileRouter]. Implemented by the heatmap feature, which owns the
 * session cookies and the tile cache; [Interceptor.Chain.proceed] is used for network calls.
 */
fun interface HeatmapTileHandler {
    fun handle(
        chain: Interceptor.Chain,
        request: HeatmapRequest,
    ): Response
}
