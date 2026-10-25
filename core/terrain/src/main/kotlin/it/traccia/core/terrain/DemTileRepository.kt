package it.traccia.core.terrain

import it.traccia.core.common.LruCache
import it.traccia.core.geo.TileId
import it.traccia.core.geo.terrain.ElevationGrid
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads and decodes DEM tiles, with an in-memory LRU of decoded grids
 * on top of the OkHttp disk cache. When the network is down it falls back to the disk cache.
 *
 * Blocking API: it is called from MapLibre's tile worker threads and from IO coroutines.
 */
@Singleton
class DemTileRepository
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val sources: MapSources,
        private val decoder: DemTileDecoder,
    ) {
        val tileSize: Int get() = sources.dem.tileSize
        val maxZoom: Int get() = sources.dem.maxZoom

        private val memory = LruCache<TileId, ElevationGrid>(MEMORY_TILES)
        private val locks = ConcurrentHashMap<TileId, Any>()

        /** @return the decoded tile, or null when it is not available online nor in cache. */
        fun tile(id: TileId): ElevationGrid? {
            memory[id]?.let { return it }
            // One download per tile even when several map tiles ask for it at once.
            val lock = locks.computeIfAbsent(id) { Any() }
            return synchronized(lock) {
                memory[id] ?: download(id)?.also { memory.put(id, it) }
            }.also { locks.remove(id) }
        }

        private fun download(id: TileId): ElevationGrid? {
            val url = sources.virtualTileUrl(TileLayer.DEM, id.zoom, id.x, id.y)
            val bytes =
                try {
                    fetch(Request.Builder().url(url).build())
                } catch (_: IOException) {
                    // Offline: use whatever the HTTP cache has, even if stale.
                    try {
                        fetch(
                            Request
                                .Builder()
                                .url(url)
                                .cacheControl(CacheControl.FORCE_CACHE)
                                .build(),
                        )
                    } catch (_: IOException) {
                        null
                    }
                }
            return bytes?.let(decoder::decode)
        }

        private fun fetch(request: Request): ByteArray? =
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body.bytes() else null
            }

        private companion object {
            // A 512 px tile is ~1 MB of floats.
            const val MEMORY_TILES = 24
        }
    }
