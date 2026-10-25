package it.traccia.feature.heatmap.tiles

import android.util.Log
import it.traccia.core.terrain.HeatmapRequest
import it.traccia.core.terrain.HeatmapTileHandler
import it.traccia.feature.heatmap.cache.CachedTile
import it.traccia.feature.heatmap.cache.HeatmapTileCache
import it.traccia.feature.heatmap.config.HeatmapConfig
import it.traccia.feature.heatmap.session.HeatmapSession
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Heatmap tiles, following the rules in CLAUDE.md:
 * - requested only when MapLibre displays them (no prefetch, no area download);
 * - each downloaded tile is cached with its download date and reused until it is older than the threshold;
 * - Strava cookies go only to the tile host; when they are rejected the cache keeps working.
 */
class HeatmapTileService(
    private val config: HeatmapConfig,
    private val cache: HeatmapTileCache,
    private val session: HeatmapSession,
    private val clock: () -> Long = System::currentTimeMillis,
) : HeatmapTileHandler {
    private val refreshAfterMillis = TimeUnit.DAYS.toMillis(config.refreshAfterDays.toLong())

    override fun handle(
        chain: Interceptor.Chain,
        request: HeatmapRequest,
    ): Response {
        val sport = config.sport(request.sport)
        if (sport == null || request.color !in config.colors || request.z !in config.minZoom..config.maxZoom) {
            return empty(chain.request())
        }
        val cached = cache.get(request.sport, request.color, request.z, request.x, request.y)
        val cookies = session.tileCookies()
        return when (decideTileAction(cached?.downloadedAtMillis?.takeIf { it > 0 }, clock(), refreshAfterMillis, cookies != null)) {
            TileAction.SERVE_CACHE, TileAction.CACHE_ONLY -> serve(chain.request(), cached)
            TileAction.FETCH -> fetch(chain, request, config.tileUrlFor(sport, request.color, request.z, request.x, request.y), cached)
        }
    }

    private fun fetch(
        chain: Interceptor.Chain,
        request: HeatmapRequest,
        url: String,
        cached: CachedTile?,
    ): Response {
        val original = chain.request()
        return try {
            var response = download(chain, url)
            if (response.code in UNAUTHORIZED) {
                response.close()
                // Try once to renew the CloudFront cookies with the strava.com session.
                val renewed = session.onUnauthorized { sessionCookies -> renew(chain, sessionCookies) }
                if (!renewed) return serve(original, cached)
                response = download(chain, url)
                if (response.code in UNAUTHORIZED) {
                    response.close()
                    return serve(original, cached)
                }
            }
            response.use { r ->
                when {
                    r.isSuccessful -> store(original, request, r.body.bytes())
                    // No activity there: remember it, so the empty tile is not requested again for a while.
                    r.code == HTTP_NOT_FOUND -> store(original, request, ByteArray(0))
                    else -> serve(original, cached)
                }
            }
        } catch (e: IOException) {
            Log.i(TAG, "Heatmap offline, using cache", e)
            if (cached == null) throw e
            serve(original, cached)
        }
    }

    private fun download(
        chain: Interceptor.Chain,
        url: String,
    ): Response {
        val cookies = session.tileCookies().orEmpty()
        return chain.proceed(
            Request
                .Builder()
                .url(url)
                .header("Cookie", cookies)
                // Our cache is the only copy: keep the tiles out of the shared HTTP cache.
                .cacheControl(CacheControl.Builder().noStore().build())
                .build(),
        )
    }

    private fun renew(
        chain: Interceptor.Chain,
        sessionCookies: String,
    ): Headers? =
        try {
            chain
                .proceed(
                    Request
                        .Builder()
                        .url(config.heatmapPageUrl)
                        .header("Cookie", sessionCookies)
                        .cacheControl(CacheControl.Builder().noStore().build())
                        .build(),
                ).use { if (it.isSuccessful) it.headers else null }
        } catch (e: IOException) {
            Log.i(TAG, "Cannot renew the Strava session", e)
            null
        }

    private fun store(
        original: Request,
        request: HeatmapRequest,
        data: ByteArray,
    ): Response {
        val tile = CachedTile(data, clock())
        cache.put(request.sport, request.color, request.z, request.x, request.y, tile)
        return serve(original, tile)
    }

    private fun serve(
        original: Request,
        tile: CachedTile?,
    ): Response =
        if (tile == null || tile.data.isEmpty()) {
            empty(original)
        } else {
            Response
                .Builder()
                .request(original)
                .protocol(Protocol.HTTP_1_1)
                .code(HTTP_OK)
                .message("OK")
                // Never stored by MapLibre: the heatmap cache is the only cache (M3.7).
                .header("Cache-Control", "no-store")
                .body(tile.data.toResponseBody(PNG))
                .build()
        }

    private fun empty(original: Request): Response =
        Response
            .Builder()
            .request(original)
            .protocol(Protocol.HTTP_1_1)
            .code(HTTP_NOT_FOUND)
            .message("Not Found")
            .header("Cache-Control", "no-store")
            .body(ByteArray(0).toResponseBody(null))
            .build()

    private companion object {
        const val TAG = "HeatmapTileService"
        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404
        val UNAUTHORIZED = setOf(401, 403)
        val PNG = "image/png".toMediaType()
    }
}
