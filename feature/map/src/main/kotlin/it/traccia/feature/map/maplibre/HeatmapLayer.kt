package it.traccia.feature.map.maplibre

import it.traccia.feature.heatmap.HeatmapSource
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/** The Strava heatmap raster layer, kept across style reloads (M3.4, M3.6, M3.9). */
internal class HeatmapLayer {
    private var source: HeatmapSource? = null
    private var opacity = 1f
    private var reloadToken = 0

    /**
     * Applies the wanted state. A new [newReloadToken] makes MapLibre request the visible tiles
     * again, after the area was marked for refresh. With no loaded [style] the state is only kept.
     */
    fun update(
        style: Style?,
        newSource: HeatmapSource?,
        newOpacity: Float,
        newReloadToken: Int,
    ) {
        val sourceChanged = newSource != source || newReloadToken != reloadToken
        source = newSource
        opacity = newOpacity
        reloadToken = newReloadToken
        if (style == null) return
        if (sourceChanged) {
            remove(style)
            newSource?.let { add(style, it) }
        } else {
            style.getLayer(LAYER)?.setProperties(PropertyFactory.rasterOpacity(newOpacity))
        }
    }

    /** Re-adds the layer after a style change. */
    fun restore(style: Style) {
        source?.let { add(style, it) }
    }

    private fun add(
        style: Style,
        heatmap: HeatmapSource,
    ) {
        val tileSet =
            TileSet(TILEJSON_VERSION, heatmap.tilesUrl).apply {
                minZoom = heatmap.minZoom.toFloat()
                maxZoom = heatmap.maxZoom.toFloat()
            }
        style.addSource(
            RasterSource(SOURCE, tileSet, heatmap.tileSize).apply {
                // Only the tiles on screen: no lower-zoom prefetching (CLAUDE.md heatmap rules).
                prefetchZoomDelta = 0
            },
        )
        val layer =
            RasterLayer(LAYER, SOURCE).withProperties(
                PropertyFactory.rasterOpacity(opacity),
                PropertyFactory.rasterFadeDuration(0f),
            )
        // Above terrain and contour lines, below every label (CLAUDE.md layer order).
        val firstLabel = style.layers.firstOrNull { it is SymbolLayer }
        if (firstLabel != null) style.addLayerBelow(layer, firstLabel.id) else style.addLayer(layer)
    }

    private fun remove(style: Style) {
        style.getLayer(LAYER)?.let { style.removeLayer(it) }
        style.getSource(SOURCE)?.let { style.removeSource(it) }
    }

    private companion object {
        const val SOURCE = "heatmap"
        const val LAYER = "heatmap"
        const val TILEJSON_VERSION = "2.2.0"
    }
}
