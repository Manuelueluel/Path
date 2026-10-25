package it.traccia.feature.map.style

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import it.traccia.core.terrain.MapSources
import it.traccia.core.terrain.TileLayer
import it.traccia.feature.map.StyleLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

/** Loads a style JSON from assets and fills in the data source URLs from [MapSources]. */
class StyleRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sources: MapSources,
    ) : StyleLoader {
        override suspend fun load(style: MapStyle): String =
            withContext(Dispatchers.IO) {
                val template =
                    context.assets
                        .open(style.asset)
                        .bufferedReader()
                        .use { it.readText() }
                resolveStylePlaceholders(template, sources)
            }
    }

/**
 * Replaces the `{{...}}` placeholders of a style template. Numeric placeholders are written
 * as quoted strings in the template (to keep it valid JSON) and become plain numbers here.
 */
fun resolveStylePlaceholders(
    template: String,
    sources: MapSources,
): String =
    template
        .replace("\"{{DEM_TILESIZE}}\"", sources.dem.tileSize.toString())
        .replace("\"{{DEM_MAXZOOM}}\"", sources.dem.maxZoom.toString())
        .replace("\"{{BASEMAP_MAXZOOM}}\"", sources.basemap.maxZoom.toString())
        .replace("\"{{TRAILS_MAXZOOM}}\"", sources.trails.maxZoom.toString())
        .replace("{{BASEMAP_TILES}}", sources.virtualTiles(TileLayer.BASEMAP))
        .replace("{{TRAILS_TILES}}", sources.virtualTiles(TileLayer.TRAILS))
        .replace("{{DEM_TILES}}", sources.virtualTiles(TileLayer.DEM))
        .replace("{{GLYPHS}}", sources.virtualGlyphs)
        .replace("{{BASEMAP_ATTRIBUTION}}", jsonEscape(sources.basemap.attribution))
        .replace("{{DEM_ATTRIBUTION}}", jsonEscape(sources.dem.attribution))

/** Escapes [text] for use inside a JSON string literal (without the surrounding quotes). */
private fun jsonEscape(text: String): String = JsonPrimitive(text).toString().removeSurrounding("\"")
