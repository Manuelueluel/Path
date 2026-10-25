package it.traccia.core.terrain

import it.traccia.core.geo.TileId
import it.traccia.core.geo.terrain.ElevationGrid
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DemTileRepositoryTest {
    private val server = MockWebServer()

    @TempDir
    lateinit var cacheDir: File

    /** Fake decoder: the body is the elevation of a 1x1 grid. */
    private val decoder = DemTileDecoder { bytes -> ElevationGrid(1, 1, floatArrayOf(bytes.decodeToString().toFloat())) }

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.close()

    /** Same wiring as the app: the DEM goes through the tile router, here with no offline regions. */
    private fun repository(): DemTileRepository {
        val sources = testSources(server)
        val client =
            OkHttpClient
                .Builder()
                .cache(Cache(cacheDir, 1_000_000))
                .addInterceptor(TileRouter(sources, FakeLocalTiles()) { _, _ -> null })
                .build()
        return DemTileRepository(client, sources, decoder)
    }

    private fun ok(body: String) =
        MockResponse
            .Builder()
            .body(body)
            .addHeader("Cache-Control", "max-age=0")
            .build()

    @Test
    fun `downloads the right url and decodes`() {
        server.enqueue(ok("1234"))
        val tile = repository().tile(TileId(12, 2155, 1459))
        assertEquals(1234f, checkNotNull(tile)[0, 0])
        assertEquals("/dem/12/2155/1459.webp", server.takeRequest().target)
    }

    @Test
    fun `second request is served from memory`() {
        server.enqueue(ok("10"))
        val repo = repository()
        repo.tile(TileId(1, 0, 0))
        repo.tile(TileId(1, 0, 0))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `missing tile returns null`() {
        server.enqueue(MockResponse.Builder().code(404).build())
        assertNull(repository().tile(TileId(15, 1, 1)))
    }

    @Test
    fun `offline falls back to the stale disk cache`() {
        server.enqueue(ok("777"))
        repository().tile(TileId(5, 3, 3))
        server.close()
        // New repository: empty memory cache, same disk cache, server unreachable.
        assertNotNull(repository().tile(TileId(5, 3, 3)))
    }
}
