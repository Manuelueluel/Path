package it.traccia.feature.heatmap.tiles

import it.traccia.core.storage.offline.LocalTile
import it.traccia.core.storage.offline.LocalTileSource
import it.traccia.core.terrain.MapSources
import it.traccia.core.terrain.TileRouter
import it.traccia.feature.heatmap.bundledConfig
import it.traccia.feature.heatmap.cache.CacheAge
import it.traccia.feature.heatmap.cache.CachedTile
import it.traccia.feature.heatmap.cache.HeatmapTileCache
import it.traccia.feature.heatmap.cache.TileRange
import it.traccia.feature.heatmap.session.HeatmapSession
import it.traccia.feature.heatmap.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class HeatmapTileServiceTest {
    private val server = MockWebServer()
    private val cache = FakeCache()
    private val session = FakeSession()
    private var now = TimeUnit.DAYS.toMillis(100)

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.close()

    /** Same wiring as the app: router -> heatmap service, Strava replaced by the mock server. */
    private fun client(): OkHttpClient {
        val config =
            bundledConfig.copy(
                tileUrl = server.url("/").toString() + "{sport}/{color}/{z}/{x}/{y}.png",
                heatmapPageUrl = server.url("/heatmap-page").toString(),
            )
        val sources =
            MapSources(
                virtualHost = "tiles.test.invalid",
                basemap = MapSources.Basemap("", 14, ""),
                trails = MapSources.Trails(14, ""),
                dem = MapSources.Dem("", "terrarium", 512, 12, ""),
                glyphsUrl = "",
                catalogUrl = "",
            )
        val noLocal =
            object : LocalTileSource {
                override fun find(
                    layer: String,
                    z: Int,
                    x: Int,
                    y: Int,
                ): LocalTile? = null
            }
        val service = HeatmapTileService(config, cache, session) { now }
        return OkHttpClient.Builder().addInterceptor(TileRouter(sources, noLocal, service) { _, _ -> null }).build()
    }

    private fun OkHttpClient.tile(path: String = "heatmap/hike/hot/13/4311/2918.png") =
        newCall(Request.Builder().url("https://tiles.test.invalid/$path").build()).execute().use { it.code to it.body.string() }

    private fun png(text: String) = MockResponse.Builder().body(text).build()

    @Test
    fun `downloads with cookies only from the tile host and caches with the date`() {
        session.cookies = "CloudFront-Signature=S"
        server.enqueue(png("heat"))
        assertEquals(200 to "heat", client().tile())
        val request = server.takeRequest()
        assertEquals("/sport_Hike/hot/13/4311/2918.png", request.target)
        assertEquals("CloudFront-Signature=S", request.headers["Cookie"])
        assertEquals(
            now,
            cache.tiles.values
                .single()
                .downloadedAtMillis,
        )
    }

    @Test
    fun `recent cached tile is served without network`() {
        session.cookies = "c=1"
        cache.tiles["hike/hot/13/4311/2918"] = CachedTile("cached".toByteArray(), now - TimeUnit.DAYS.toMillis(5))
        assertEquals(200 to "cached", client().tile())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `old cached tile is downloaded again`() {
        session.cookies = "c=1"
        cache.tiles["hike/hot/13/4311/2918"] = CachedTile("old".toByteArray(), now - TimeUnit.DAYS.toMillis(40))
        server.enqueue(png("new"))
        assertEquals(200 to "new", client().tile())
    }

    @Test
    fun `area marked stale is downloaded again`() {
        session.cookies = "c=1"
        cache.tiles["hike/hot/13/4311/2918"] = CachedTile("old".toByteArray(), 0)
        server.enqueue(png("new"))
        assertEquals(200 to "new", client().tile())
    }

    @Test
    fun `without session the cache is still used, even if old`() {
        cache.tiles["hike/hot/13/4311/2918"] = CachedTile("old".toByteArray(), now - TimeUnit.DAYS.toMillis(400))
        assertEquals(200 to "old", client().tile())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `without session and cache nothing is requested`() {
        assertEquals(404, client().tile().first)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `offline falls back to the cache`() {
        session.cookies = "c=1"
        cache.tiles["hike/hot/13/4311/2918"] = CachedTile("old".toByteArray(), now - TimeUnit.DAYS.toMillis(40))
        val client = client()
        server.close()
        assertEquals(200 to "old", client.tile())
    }

    @Test
    fun `rejected cookies are renewed and the tile retried`() {
        session.cookies = "c=old"
        session.renewTo = "c=new"
        server.enqueue(MockResponse.Builder().code(403).build())
        server.enqueue(MockResponse.Builder().addHeader("Set-Cookie", "c=new").build())
        server.enqueue(png("heat"))
        assertEquals(200 to "heat", client().tile())
        server.takeRequest()
        assertEquals("/heatmap-page", server.takeRequest().target)
        assertEquals("c=new", server.takeRequest().headers["Cookie"])
    }

    @Test
    fun `rejected cookies without renewal serve the cache`() {
        session.cookies = "c=old"
        cache.tiles["hike/hot/13/4311/2918"] = CachedTile("old".toByteArray(), now - TimeUnit.DAYS.toMillis(40))
        server.enqueue(MockResponse.Builder().code(403).build())
        server.enqueue(MockResponse.Builder().code(302).build())
        assertEquals(200 to "old", client().tile())
        assertEquals(SessionState.Expired, session.state.value)
    }

    @Test
    fun `empty Strava tiles are remembered as empty`() {
        session.cookies = "c=1"
        server.enqueue(MockResponse.Builder().code(404).build())
        assertEquals(404, client().tile().first)
        assertEquals(
            0,
            cache.tiles.values
                .single()
                .data.size,
        )
        assertEquals(404, client().tile().first)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `unknown sport, color or zoom is never requested`() {
        session.cookies = "c=1"
        val client = client()
        assertEquals(404, client.tile("heatmap/curling/hot/13/1/1.png").first)
        assertEquals(404, client.tile("heatmap/hike/rainbow/13/1/1.png").first)
        assertEquals(404, client.tile("heatmap/hike/hot/18/1/1.png").first)
        assertEquals(0, server.requestCount)
        assertNull(cache.tiles["hike/hot/18/1/1"])
    }

    private class FakeCache : HeatmapTileCache {
        val tiles = mutableMapOf<String, CachedTile>()

        override fun get(
            sport: String,
            color: String,
            z: Int,
            x: Int,
            y: Int,
        ) = tiles["$sport/$color/$z/$x/$y"]

        override fun put(
            sport: String,
            color: String,
            z: Int,
            x: Int,
            y: Int,
            tile: CachedTile,
        ) {
            tiles["$sport/$color/$z/$x/$y"] = tile
        }

        override fun age(
            sport: String,
            color: String,
            ranges: List<TileRange>,
        ): CacheAge? = null

        override fun markStale(
            sport: String,
            color: String,
            ranges: List<TileRange>,
        ) = Unit

        override fun sizes(): Map<String, Long> = emptyMap()

        override fun clear(sport: String) = Unit
    }

    private class FakeSession : HeatmapSession {
        var cookies: String? = null
        var renewTo: String? = null
        override val state = MutableStateFlow<SessionState>(SessionState.Active(null))

        override fun tileCookies() = cookies.takeIf { state.value != SessionState.Expired }

        override fun onUnauthorized(renew: (sessionCookies: String) -> Headers?): Boolean {
            val headers = renew("session=1")
            val fresh = renewTo
            return if (headers != null && fresh != null) {
                cookies = fresh
                true
            } else {
                state.value = SessionState.Expired
                false
            }
        }
    }
}
