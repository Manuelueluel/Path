package it.traccia.core.terrain

import it.traccia.core.storage.offline.LocalTile
import it.traccia.core.storage.offline.LocalTileSource
import mockwebserver3.MockWebServer

internal const val TEST_HOST = "tiles.test.invalid"

/** Sources whose online endpoints all point at [server]. */
internal fun testSources(server: MockWebServer) =
    MapSources(
        virtualHost = TEST_HOST,
        basemap = MapSources.Basemap(tileJsonUrl = server.url("/tilejson").toString(), maxZoom = 14, attribution = ""),
        trails = MapSources.Trails(maxZoom = 14, attribution = ""),
        dem =
            MapSources.Dem(
                server.url("/").toString() + "dem/{z}/{x}/{y}.webp",
                "terrarium",
                tileSize = 512,
                maxZoom = 12,
                attribution = "",
            ),
        glyphsUrl = server.url("/").toString() + "fonts/{fontstack}/{range}.pbf",
        catalogUrl = server.url("/catalog.json").toString(),
    )

internal class FakeLocalTiles(
    private val tiles: Map<String, LocalTile> = emptyMap(),
) : LocalTileSource {
    override fun find(
        layer: String,
        z: Int,
        x: Int,
        y: Int,
    ): LocalTile? = tiles["$layer/$z/$x/$y"]
}
