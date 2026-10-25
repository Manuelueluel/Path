package it.traccia.core.terrain

import it.traccia.core.storage.offline.LocalTileSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.zip.GZIPInputStream

/** Parsed request for a routed tile or glyph range. */
sealed interface RoutedRequest {
    data class Tile(
        val layer: TileLayer,
        val z: Int,
        val x: Int,
        val y: Int,
    ) : RoutedRequest

    data class Glyphs(
        val fontStack: String,
        val range: String,
    ) : RoutedRequest

    data class Heatmap(
        val request: HeatmapRequest,
    ) : RoutedRequest

    companion object {
        /** Parses the path segments after the virtual host, or null when not routable. */
        fun parse(segments: List<String>): RoutedRequest? =
            when {
                segments.size == GLYPH_SEGMENTS && segments[0] == MapSources.FONTS_PATH ->
                    Glyphs(segments[1], segments[2].removeSuffix(".pbf"))
                segments.size == TILE_SEGMENTS -> parseTile(segments)
                segments.size == HEATMAP_SEGMENTS && segments[0] == HEATMAP_PATH -> parseHeatmap(segments)
                else -> null
            }

        private fun parseHeatmap(segments: List<String>): Heatmap? {
            val (z, x, y) = segments.drop(HEATMAP_COORDS_START).map { it.substringBefore('.').toIntOrNull() ?: return null }
            return Heatmap(HeatmapRequest(sport = segments[1], color = segments[2], z = z, x = x, y = y))
        }

        private fun parseTile(segments: List<String>): Tile? {
            val layer = TileLayer.fromPath(segments[0]) ?: return null
            val (z, x, y) = segments.drop(1).map { it.substringBefore('.').toIntOrNull() ?: return null }
            return Tile(layer, z, x, y)
        }

        private const val TILE_SEGMENTS = 4
        private const val GLYPH_SEGMENTS = 3
        private const val HEATMAP_SEGMENTS = 6
        private const val HEATMAP_COORDS_START = 3
        const val HEATMAP_PATH = "heatmap"
    }
}

/**
 * OkHttp interceptor that resolves the app's virtual tile URLs (M2.7 automatic fallback):
 * - tiles fully covered by an installed region are served locally, without network;
 * - other tiles are fetched online, falling back to the partial local tile when offline;
 * - label glyphs come from the app assets, or online for uncommon scripts;
 * - Strava heatmap tiles are delegated to the [HeatmapTileHandler] (cache and session cookies).
 *
 * Shared by MapLibre and by the DEM code, so contours and elevations work offline too.
 */
class TileRouter(
    private val sources: MapSources,
    private val local: LocalTileSource,
    private val heatmap: HeatmapTileHandler? = null,
    private val bundledGlyphs: (fontStack: String, range: String) -> ByteArray?,
) : Interceptor {
    @Volatile
    private var basemapTemplate: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != sources.virtualHost) return chain.proceed(request)
        return when (val routed = RoutedRequest.parse(request.url.pathSegments)) {
            is RoutedRequest.Tile -> routeTile(chain, routed)
            is RoutedRequest.Glyphs -> routeGlyphs(chain, routed)
            is RoutedRequest.Heatmap -> heatmap?.handle(chain, routed.request) ?: notFound(request)
            null -> notFound(request)
        }
    }

    private fun routeTile(
        chain: Interceptor.Chain,
        tile: RoutedRequest.Tile,
    ): Response {
        val request = chain.request()
        val localTile = local.find(tile.layer.path, tile.z, tile.x, tile.y)
        if (localTile != null && localTile.coversTile) return ok(request, decoded(localTile.data), tile.layer.mediaType)
        val onlineUrl = onlineUrl(chain, tile)
        if (onlineUrl != null) {
            try {
                val response = chain.proceed(request.newBuilder().url(onlineUrl).build())
                if (response.isSuccessful || localTile == null) return response
                response.close()
            } catch (e: IOException) {
                if (localTile == null) throw e
            }
        }
        return localTile?.let { ok(request, decoded(it.data), tile.layer.mediaType) } ?: notFound(request)
    }

    /**
     * MBTiles vector tiles are usually gzipped. Over HTTP, decompression is the transport's job
     * (Content-Encoding), which does not apply to locally built responses: unzip them here.
     */
    private fun decoded(data: ByteArray): ByteArray =
        if (data.size > 2 && data[0] == GZIP_MAGIC_1 && data[1] == GZIP_MAGIC_2) {
            GZIPInputStream(data.inputStream()).use { it.readBytes() }
        } else {
            data
        }

    private fun routeGlyphs(
        chain: Interceptor.Chain,
        glyphs: RoutedRequest.Glyphs,
    ): Response {
        val request = chain.request()
        bundledGlyphs(glyphs.fontStack, glyphs.range)?.let { return ok(request, it, PROTOBUF) }
        val url =
            sources.glyphsUrl
                .replace("{fontstack}", glyphs.fontStack)
                .replace("{range}", glyphs.range)
        return chain.proceed(request.newBuilder().url(url).build())
    }

    private fun onlineUrl(
        chain: Interceptor.Chain,
        tile: RoutedRequest.Tile,
    ): String? =
        when (tile.layer) {
            TileLayer.DEM -> sources.dem.onlineUrlFor(tile.z, tile.x, tile.y)
            TileLayer.BASEMAP ->
                basemapTemplate(chain)
                    ?.replace("{z}", tile.z.toString())
                    ?.replace("{x}", tile.x.toString())
                    ?.replace("{y}", tile.y.toString())
            // The trail difficulty overlay exists only in the offline packs.
            TileLayer.TRAILS -> null
        }

    /** OpenFreeMap versions its tile URLs: read the current template from its TileJSON (cached by OkHttp). */
    private fun basemapTemplate(chain: Interceptor.Chain): String? {
        basemapTemplate?.let { return it }
        return try {
            chain.proceed(Request.Builder().url(sources.basemap.tileJsonUrl).build()).use { response ->
                if (!response.isSuccessful) return null
                val tileJson = Json.parseToJsonElement(response.body.string()).jsonObject
                tileJson["tiles"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonPrimitive
                    ?.content
                    ?.also { basemapTemplate = it }
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            // Malformed TileJSON: treat as unavailable.
            null
        }
    }

    private fun ok(
        request: Request,
        data: ByteArray,
        mediaType: String,
    ): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(HTTP_OK)
            .message("OK")
            // Local data: never stored in MapLibre's ambient cache, the file is the cache.
            .header("Cache-Control", "no-store")
            .body(data.toResponseBody(mediaType.toMediaType()))
            .build()

    private fun notFound(request: Request): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(HTTP_NOT_FOUND)
            .message("Not Found")
            .body(ByteArray(0).toResponseBody(null))
            .build()

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404
        const val PROTOBUF = "application/x-protobuf"
        const val GZIP_MAGIC_1 = 0x1f.toByte()
        const val GZIP_MAGIC_2 = 0x8b.toByte()
    }
}
