package it.traccia.core.terrain

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Map data sources, read from `assets/map_sources.json` so that URLs live in configuration.
 *
 * The map style and the DEM code never use the online URLs directly: they request
 * `https://<virtualHost>/<layer>/{z}/{x}/{y}` and [TileRouter] serves each tile from an installed
 * offline region or from the online source.
 */
@Serializable
data class MapSources(
    val virtualHost: String,
    val basemap: Basemap,
    val trails: Trails,
    val dem: Dem,
    val glyphsUrl: String,
    val catalogUrl: String,
) {
    @Serializable
    data class Basemap(
        val tileJsonUrl: String,
        val maxZoom: Int,
        val attribution: String,
    )

    /** Trail difficulty overlay: only available offline (built by the region pipeline). */
    @Serializable
    data class Trails(
        val maxZoom: Int,
        val attribution: String,
    )

    @Serializable
    data class Dem(
        val tileUrl: String,
        val encoding: String,
        val tileSize: Int,
        val maxZoom: Int,
        val attribution: String,
    ) {
        fun onlineUrlFor(
            z: Int,
            x: Int,
            y: Int,
        ): String = tileUrl.replace("{z}", z.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())
    }

    /** URL template of a routed layer, e.g. `https://tiles.traccia.invalid/dem/{z}/{x}/{y}.webp`. */
    fun virtualTiles(layer: TileLayer): String = "https://$virtualHost/${layer.path}/{z}/{x}/{y}.${layer.extension}"

    fun virtualTileUrl(
        layer: TileLayer,
        z: Int,
        x: Int,
        y: Int,
    ): String = "https://$virtualHost/${layer.path}/$z/$x/$y.${layer.extension}"

    val virtualGlyphs: String get() = "https://$virtualHost/$FONTS_PATH/{fontstack}/{range}.pbf"

    /** Heatmap tile template for one sport and color, served through [HeatmapTileHandler]. */
    fun virtualHeatmapTiles(
        sport: String,
        color: String,
    ): String = "https://$virtualHost/${RoutedRequest.HEATMAP_PATH}/$sport/$color/{z}/{x}/{y}.png"

    companion object {
        const val FONTS_PATH = "fonts"
        private const val ASSET = "map_sources.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): MapSources = json.decodeFromString(serializer(), text)

        fun fromAssets(context: Context): MapSources =
            context.assets
                .open(ASSET)
                .bufferedReader()
                .use { parse(it.readText()) }
    }
}

/** Tiled layers served through [TileRouter]; [path] matches the region pack file type. */
enum class TileLayer(
    val path: String,
    val extension: String,
    val mediaType: String,
) {
    BASEMAP("basemap", "pbf", "application/x-protobuf"),
    TRAILS("trails", "pbf", "application/x-protobuf"),
    DEM("dem", "webp", "image/webp"),
    ;

    companion object {
        fun fromPath(path: String): TileLayer? = entries.firstOrNull { it.path == path }
    }
}
