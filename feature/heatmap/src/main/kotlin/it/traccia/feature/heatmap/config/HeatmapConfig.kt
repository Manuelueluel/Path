package it.traccia.feature.heatmap.config

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Strava heatmap endpoints and identifiers, from `assets/heatmap.json` (never hard-coded). */
@Serializable
data class HeatmapConfig(
    val tileUrl: String,
    val tileSize: Int,
    val minZoom: Int,
    val maxZoom: Int,
    val loginUrl: String,
    val heatmapPageUrl: String,
    val sessionCookieUrl: String,
    val requiredCookies: List<String>,
    val policyCookie: String,
    val refreshAfterDays: Int,
    val sports: List<Sport>,
    val colors: List<String>,
) {
    @Serializable
    data class Sport(
        /** App identifier, stable: used in preferences and cache file names. */
        val id: String,
        /** Strava layer identifier in the tile URL. */
        val strava: String,
    )

    /** Origin of the tile host, used to read its cookies from the WebView. */
    val tileOrigin: String get() = tileUrl.substringBefore("://") + "://" + tileUrl.substringAfter("://").substringBefore('/')

    fun sport(id: String): Sport? = sports.firstOrNull { it.id == id }

    fun tileUrlFor(
        sport: Sport,
        color: String,
        z: Int,
        x: Int,
        y: Int,
    ): String =
        tileUrl
            .replace("{sport}", sport.strava)
            .replace("{color}", color)
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())

    companion object {
        private const val ASSET = "heatmap.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): HeatmapConfig = json.decodeFromString(serializer(), text)

        fun fromAssets(context: Context): HeatmapConfig =
            context.assets
                .open(ASSET)
                .bufferedReader()
                .use { parse(it.readText()) }
    }
}
