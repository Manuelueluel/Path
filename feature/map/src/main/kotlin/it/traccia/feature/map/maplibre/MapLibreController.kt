package it.traccia.feature.map.maplibre

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import androidx.core.content.ContextCompat
import it.traccia.core.geo.terrain.ContourInterval
import it.traccia.core.storage.route.Route
import it.traccia.feature.heatmap.HeatmapSource
import it.traccia.feature.map.R
import it.traccia.feature.map.style.MapStyle
import it.traccia.feature.planner.PlannerUiState
import it.traccia.feature.planner.map.PlannerLayer
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.toBool
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.CustomGeometrySource
import org.maplibre.android.style.sources.CustomGeometrySourceOptions
import org.maplibre.android.style.sources.GeometryTileProvider

/**
 * Imperative wrapper around [MapLibreMap]: everything that touches the MapLibre style lives here,
 * so the Compose layer only forwards state.
 */
class MapLibreController(
    private val context: Context,
    val map: MapLibreMap,
    private val contourProvider: GeometryTileProvider,
) {
    private var loadedJson: String? = null
    private var contoursVisible = true
    private var hillshadeVisible = true
    private var locationEnabled = false
    private var pendingCenterOnUser = false
    private val heatmapLayer = HeatmapLayer()
    private val plannerLayer = PlannerLayer()

    /** Loads [json] unless it is already the current style; runtime layers are re-added after loading. */
    fun setStyle(
        style: MapStyle,
        json: String,
    ) {
        if (json == loadedJson) return
        loadedJson = json
        val builder =
            Style
                .Builder()
                .fromJson(json)
                .withDrawableImages(
                    *ICONS
                        .map { (name, res) ->
                            android.util.Pair(name, requireNotNull(ContextCompat.getDrawable(context, res)))
                        }.toTypedArray(),
                )
        map.setStyle(builder) { loaded ->
            if (json != loadedJson) return@setStyle
            addContourLayers(loaded, style)
            heatmapLayer.restore(loaded)
            plannerLayer.install(loaded)
            applyVisibility(loaded)
            activateLocation(loaded)
            if (pendingCenterOnUser) startTracking()
        }
    }

    fun setLayersVisible(
        contours: Boolean,
        hillshade: Boolean,
    ) {
        contoursVisible = contours
        hillshadeVisible = hillshade
        map.style?.takeIf { it.isFullyLoaded }?.let(::applyVisibility)
    }

    /** Shows the Strava heatmap raster (null hides it); see [HeatmapLayer]. */
    fun setHeatmap(
        source: HeatmapSource?,
        opacity: Float,
        reloadToken: Int,
    ) = heatmapLayer.update(map.style?.takeIf { it.isFullyLoaded }, source, opacity, reloadToken)

    /** Draws the route being planned (M5). */
    fun renderPlanner(state: PlannerUiState) {
        plannerLayer.render(map.style, state)
    }

    /** Saved routes shown together (M7.9). */
    fun renderShownRoutes(routes: List<Route>) {
        plannerLayer.renderShown(map.style, routes)
    }

    /** Control point under a screen position, with a touch-friendly radius (M5.7). */
    fun plannerPointAt(
        screen: PointF,
        radiusPx: Float,
    ): Int? = map.style?.takeIf { it.isFullyLoaded }?.let { plannerLayer.pointAt(map, screen, radiusPx) }

    /** Shows the user position. Callers must have checked the location permission. */
    fun setLocationEnabled(enabled: Boolean) {
        locationEnabled = enabled
        map.style?.takeIf { it.isFullyLoaded }?.let(::activateLocation)
    }

    /**
     * Moves the camera to the user and follows them until the next pan gesture.
     * Callers must have checked the location permission; the location layer is enabled if needed.
     */
    fun centerOnUser() {
        locationEnabled = true
        val style = map.style?.takeIf { it.isFullyLoaded }
        if (style == null) {
            // Style still loading: center as soon as it is ready.
            pendingCenterOnUser = true
            return
        }
        activateLocation(style)
        startTracking()
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        pendingCenterOnUser = false
        val component = map.locationComponent
        if (component.isLocationComponentActivated) {
            component.setCameraMode(CameraMode.TRACKING, CAMERA_TRANSITION_MS, USER_ZOOM, null, null, null)
        }
    }

    private fun addContourLayers(
        style: Style,
        mapStyle: MapStyle,
    ) {
        val options =
            CustomGeometrySourceOptions()
                .withMinZoom(ContourInterval.MIN_ZOOM)
                .withMaxZoom(CONTOUR_MAX_ZOOM)
                .withClip(true)
        style.addSource(CustomGeometrySource(SOURCE_CONTOURS, options, contourProvider))
        val major = toBool(get(ContourTileProvider.PROP_MAJOR))
        val minorLayer =
            LineLayer(LAYER_CONTOUR_MINOR, SOURCE_CONTOURS).withFilter(not(major)).withProperties(
                PropertyFactory.lineColor(mapStyle.contourColor),
                PropertyFactory.lineWidth(interpolate(linear(), zoom(), stop(12, 0.5f), stop(16, 0.9f))),
                PropertyFactory.lineOpacity(0.6f),
            )
        val majorLayer =
            LineLayer(LAYER_CONTOUR_MAJOR, SOURCE_CONTOURS).withFilter(major).withProperties(
                PropertyFactory.lineColor(mapStyle.contourMajorColor),
                PropertyFactory.lineWidth(interpolate(linear(), zoom(), stop(12, 0.9f), stop(16, 1.6f))),
                PropertyFactory.lineOpacity(0.8f),
            )
        val labelLayer =
            SymbolLayer(LAYER_CONTOUR_LABEL, SOURCE_CONTOURS).withFilter(major).withProperties(
                PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
                PropertyFactory.textField(Expression.toString(get(ContourTileProvider.PROP_ELEVATION))),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textSize(10f),
                PropertyFactory.textColor(mapStyle.contourLabelColor),
                PropertyFactory.textHaloColor(mapStyle.labelHaloColor),
                PropertyFactory.textHaloWidth(1.5f),
                PropertyFactory.textPadding(CONTOUR_LABEL_PADDING),
                PropertyFactory.symbolSpacing(CONTOUR_LABEL_SPACING),
            )
        labelLayer.minZoom = CONTOUR_LABEL_MIN_ZOOM
        // Lines just above the hillshade, labels below every other label of the base map.
        if (style.getLayer(LAYER_HILLSHADE) != null) {
            style.addLayerAbove(minorLayer, LAYER_HILLSHADE)
        } else {
            style.addLayer(minorLayer)
        }
        style.addLayerAbove(majorLayer, LAYER_CONTOUR_MINOR)
        val firstLabel = style.layers.firstOrNull { it is SymbolLayer && it.id != LAYER_CONTOUR_LABEL }
        if (firstLabel != null) style.addLayerBelow(labelLayer, firstLabel.id) else style.addLayer(labelLayer)
    }

    private fun applyVisibility(style: Style) {
        style.getLayer(LAYER_HILLSHADE)?.setVisible(hillshadeVisible)
        listOf(LAYER_CONTOUR_MINOR, LAYER_CONTOUR_MAJOR, LAYER_CONTOUR_LABEL).forEach { style.getLayer(it)?.setVisible(contoursVisible) }
    }

    @SuppressLint("MissingPermission")
    private fun activateLocation(style: Style) {
        val component = map.locationComponent
        if (locationEnabled && !component.isLocationComponentActivated) {
            component.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style).useDefaultLocationEngine(true).build(),
            )
        }
        if (component.isLocationComponentActivated) {
            component.isLocationComponentEnabled = locationEnabled
            component.renderMode = RenderMode.COMPASS
        }
    }

    private fun Layer.setVisible(visible: Boolean) {
        setProperties(PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE))
    }

    private companion object {
        const val SOURCE_CONTOURS = "contours"
        const val LAYER_HILLSHADE = "hillshade"
        const val LAYER_CONTOUR_MINOR = "contour-minor"
        const val LAYER_CONTOUR_MAJOR = "contour-major"
        const val LAYER_CONTOUR_LABEL = "contour-label"
        const val CONTOUR_MAX_ZOOM = 16
        const val CONTOUR_LABEL_MIN_ZOOM = 13f
        const val CONTOUR_LABEL_SPACING = 350f
        const val CONTOUR_LABEL_PADDING = 10f
        const val CAMERA_TRANSITION_MS = 750L
        const val USER_ZOOM = 15.0

        val ICONS =
            listOf(
                "peak" to R.drawable.map_icon_peak,
                "saddle" to R.drawable.map_icon_saddle,
                "hut" to R.drawable.map_icon_hut,
                "shelter" to R.drawable.map_icon_shelter,
                "water" to R.drawable.map_icon_water,
            )
    }
}
