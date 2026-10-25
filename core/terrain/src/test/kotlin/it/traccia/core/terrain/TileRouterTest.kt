package it.traccia.core.terrain

import it.traccia.core.storage.offline.LocalTile
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream

class TileRouterTest {
    private val server = MockWebServer()

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.close()

    private fun client(
        local: Map<String, LocalTile> = emptyMap(),
        glyphs: (String, String) -> ByteArray? = { _, _ -> null },
    ) = OkHttpClient
        .Builder()
        .addInterceptor(TileRouter(testSources(server), FakeLocalTiles(local), bundledGlyphs = glyphs))
        .build()

    private fun OkHttpClient.get(path: String) =
        newCall(Request.Builder().url("https://$TEST_HOST/$path").build()).execute().use { it.code to it.body.string() }

    private fun body(text: String) = MockResponse.Builder().body(text).build()

    @Test
    fun `fully covered tile is served locally without network`() {
        val client = client(mapOf("dem/12/2155/1459" to LocalTile("local".toByteArray(), coversTile = true)))
        assertEquals(200 to "local", client.get("dem/12/2155/1459.webp"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `gzipped local vector tiles are served uncompressed`() {
        val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write("pbf".toByteArray()) } }.toByteArray()
        val client = client(mapOf("basemap/14/8622/5837" to LocalTile(zipped, coversTile = true)))
        assertEquals(200 to "pbf", client.get("basemap/14/8622/5837.pbf"))
    }

    @Test
    fun `partially covered tile prefers the online version`() {
        server.enqueue(body("online"))
        val client = client(mapOf("dem/5/16/11" to LocalTile("partial".toByteArray(), coversTile = false)))
        assertEquals(200 to "online", client.get("dem/5/16/11.webp"))
        assertEquals("/dem/5/16/11.webp", server.takeRequest().target)
    }

    @Test
    fun `partially covered tile falls back to local when offline`() {
        val client = client(mapOf("dem/5/16/11" to LocalTile("partial".toByteArray(), coversTile = false)))
        server.close()
        assertEquals(200 to "partial", client.get("dem/5/16/11.webp"))
    }

    @Test
    fun `partially covered tile falls back to local on server error`() {
        server.enqueue(MockResponse.Builder().code(503).build())
        val client = client(mapOf("dem/5/16/11" to LocalTile("partial".toByteArray(), coversTile = false)))
        assertEquals(200 to "partial", client.get("dem/5/16/11.webp"))
    }

    @Test
    fun `no local tile and no network propagates the failure`() {
        val client = client()
        server.close()
        assertThrows<IOException> { client.get("dem/12/1/1.webp") }
    }

    @Test
    fun `trails exist only offline`() {
        assertEquals(404, client().get("trails/14/8622/5837.pbf").first)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `basemap url template comes from the TileJSON`() {
        val template = server.url("/planet/v1/").toString() + "{z}/{x}/{y}.pbf"
        server.enqueue(body("""{"tilejson":"3.0.0","tiles":["$template"]}"""))
        server.enqueue(body("tile-a"))
        server.enqueue(body("tile-b"))
        val client = client()
        assertEquals(200 to "tile-a", client.get("basemap/14/8622/5837.pbf"))
        assertEquals(200 to "tile-b", client.get("basemap/14/8623/5837.pbf"))
        assertEquals(
            listOf("/tilejson", "/planet/v1/14/8622/5837.pbf", "/planet/v1/14/8623/5837.pbf"),
            List(3) { server.takeRequest().target },
        )
    }

    @Test
    fun `bundled glyphs are served from assets, others online`() {
        server.enqueue(body("remote-glyphs"))
        val client = client(glyphs = { stack, range -> if (range == "0-255") "$stack:$range".toByteArray() else null })
        assertEquals(200 to "Noto Sans Regular:0-255", client.get("fonts/Noto%20Sans%20Regular/0-255.pbf"))
        assertEquals(200 to "remote-glyphs", client.get("fonts/Noto%20Sans%20Regular/19968-20223.pbf"))
    }

    @Test
    fun `other hosts are not touched`() {
        server.enqueue(body("plain"))
        val client = client()
        val response = client.newCall(Request.Builder().url(server.url("/x")).build()).execute().use { it.body.string() }
        assertEquals("plain", response)
    }

    @Test
    fun `heatmap requests go to the heatmap handler`() {
        var received: HeatmapRequest? = null
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(
                    TileRouter(
                        testSources(server),
                        FakeLocalTiles(),
                        HeatmapTileHandler { chain, request ->
                            received = request
                            chain.proceed(
                                chain
                                    .request()
                                    .newBuilder()
                                    .url(server.url("/strava"))
                                    .build(),
                            )
                        },
                    ) { _, _ -> null },
                ).build()
        server.enqueue(body("heat"))
        assertEquals(200 to "heat", client.get("heatmap/hike/hot/13/4311/2918.png"))
        assertEquals(HeatmapRequest("hike", "hot", 13, 4311, 2918), received)
    }

    @Test
    fun `heatmap without handler is not found`() {
        assertEquals(404, client().get("heatmap/hike/hot/13/4311/2918.png").first)
    }

    @Test
    fun `request parsing`() {
        assertEquals(RoutedRequest.Tile(TileLayer.BASEMAP, 14, 3, 4), RoutedRequest.parse(listOf("basemap", "14", "3", "4.pbf")))
        assertEquals(
            RoutedRequest.Glyphs("Noto Sans Bold", "256-511"),
            RoutedRequest.parse(listOf("fonts", "Noto Sans Bold", "256-511.pbf")),
        )
        assertNull(RoutedRequest.parse(listOf("unknown", "1", "2", "3.pbf")))
        assertNull(RoutedRequest.parse(listOf("dem", "a", "2", "3.webp")))
    }
}
