// Line widths and radii below are a style sheet: clearer inline than as named constants.
@file:Suppress("MagicNumber")

package it.traccia.feature.planner.map

import android.graphics.PointF
import android.graphics.RectF
import it.traccia.core.geo.GeoPoint
import it.traccia.core.storage.route.Route
import it.traccia.core.storage.route.SegmentKind
import it.traccia.feature.elevation.ElevationProfile
import it.traccia.feature.elevation.SlopeClass
import it.traccia.feature.planner.PlannerUiState
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.lineProgress
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Draws the route being planned, the control points and the profile marker, plus the saved
 * routes shown together (M7.9). Installed again after every style change.
 */
class PlannerLayer {
    private var lastState: PlannerUiState? = null
    private var lastShown: List<Route> = emptyList()

    fun install(style: Style) {
        listOf(SRC_SHOWN, SRC_ROUTE, SRC_POINTS, SRC_MARKER).forEach { style.addSource(GeoJsonSource(it)) }
        style.addSource(GeoJsonSource(SRC_SLOPE, GeoJsonOptions().withLineMetrics(true)))
        // Route above everything else, but below the user position (CLAUDE.md layer order).
        val locationLayer = style.layers.firstOrNull { it.id.contains("location") }?.id
        val add: (Layer) -> Unit = { layer ->
            if (locationLayer != null) style.addLayerBelow(layer, locationLayer) else style.addLayer(layer)
        }
        installLines(add)
        installPoints(add)
        lastState?.let { render(style, it) }
        renderShown(style, lastShown)
    }

    private fun installLines(add: (Layer) -> Unit) {
        add(roundLine(LAYER_SHOWN, SRC_SHOWN, SHOWN_COLOR, 3.5f).withProperties(PropertyFactory.lineOpacity(0.85f)))
        add(roundLine(LAYER_CASING, SRC_ROUTE, CASING_COLOR, 7f))
        add(roundLine(LAYER_LINE, SRC_ROUTE, ROUTE_COLOR, 4f).withFilter(not(get(PROP_DASHED))))
        // Straight fallbacks and segments being computed or dragged: dashed (M4.6).
        add(
            LineLayer(LAYER_DASHED, SRC_ROUTE).withFilter(get(PROP_DASHED)).withProperties(
                PropertyFactory.lineColor(ROUTE_COLOR),
                PropertyFactory.lineWidth(3.5f),
                PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)),
            ),
        )
        add(
            LineLayer(LAYER_SLOPE, SRC_SLOPE).withProperties(
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            ),
        )
    }

    private fun installPoints(add: (Layer) -> Unit) {
        add(
            CircleLayer(LAYER_POINTS, SRC_POINTS).withProperties(
                PropertyFactory.circleRadius(Expression.switchCase(get(PROP_SELECTED), literal(SELECTED_RADIUS), literal(POINT_RADIUS))),
                PropertyFactory.circleColor(
                    match(get(PROP_ROLE), literal(MID_COLOR), stop(ROLE_START, literal(START_COLOR)), stop(ROLE_END, literal(END_COLOR))),
                ),
                PropertyFactory.circleStrokeColor(
                    match(
                        get(PROP_ROLE),
                        literal(ROUTE_COLOR),
                        stop(ROLE_START, literal(CASING_COLOR)),
                        stop(ROLE_END, literal(CASING_COLOR)),
                    ),
                ),
                PropertyFactory.circleStrokeWidth(2.5f),
            ),
        )
        add(
            CircleLayer(LAYER_MARKER, SRC_MARKER).withProperties(
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleColor(MARKER_COLOR),
                PropertyFactory.circleStrokeColor(CASING_COLOR),
                PropertyFactory.circleStrokeWidth(2f),
            ),
        )
    }

    private fun roundLine(
        id: String,
        source: String,
        color: String,
        width: Float,
    ) = LineLayer(id, source).withProperties(
        PropertyFactory.lineColor(color),
        PropertyFactory.lineWidth(width),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
    )

    /** Draws [state]; without a loaded [style] it is only remembered for [install]. */
    fun render(
        style: Style?,
        state: PlannerUiState,
    ) {
        lastState = state
        if (style == null || !style.isFullyLoaded) return
        val route = state.route
        val drag = state.drag
        val points = route.points.toMutableList().also { list -> drag?.let { list[it.index] = it.position } }
        val segments =
            route.segments.mapIndexed { i, segment ->
                val affected = drag != null && (i == drag.index - 1 || i == drag.index)
                when {
                    affected || segment == null -> line(listOf(points[i], points[i + 1]), dashed = true)
                    else -> line(segment.geometry, dashed = segment.kind == SegmentKind.FALLBACK)
                }
            }
        style.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(FeatureCollection.fromFeatures(segments))
        style.getSourceAs<GeoJsonSource>(SRC_POINTS)?.setGeoJson(
            FeatureCollection.fromFeatures(pointFeatures(points, state.selectedPoint, route.isClosed)),
        )
        style.getSourceAs<GeoJsonSource>(SRC_MARKER)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(state.highlightPoint?.let { Feature.fromGeometry(point(it)) })),
        )
        renderSlope(style, state.profile.takeIf { state.profileExpanded && drag == null })
    }

    fun renderShown(
        style: Style?,
        routes: List<Route>,
    ) {
        lastShown = routes
        if (style == null || !style.isFullyLoaded) return
        style.getSourceAs<GeoJsonSource>(SRC_SHOWN)?.setGeoJson(
            FeatureCollection.fromFeatures(routes.filter { it.points.size >= 2 }.map { line(it.track, dashed = false) }),
        )
    }

    /**
     * Index of the control point under [screen], within a touch-friendly radius (M5.7). With
     * overlapping points (closed loop) the latest one wins, so deleting it reopens the loop.
     */
    fun pointAt(
        map: MapLibreMap,
        screen: PointF,
        radiusPx: Float,
    ): Int? =
        map
            .queryRenderedFeatures(RectF(screen.x - radiusPx, screen.y - radiusPx, screen.x + radiusPx, screen.y + radiusPx), LAYER_POINTS)
            .mapNotNull { it.getNumberProperty(PROP_INDEX)?.toInt() }
            .maxOrNull()

    /** Route coloured by gradient along its length (M6.5). */
    private fun renderSlope(
        style: Style,
        profile: ElevationProfile?,
    ) {
        val source = style.getSourceAs<GeoJsonSource>(SRC_SLOPE) ?: return
        val layer = style.getLayer(LAYER_SLOPE) ?: return
        if (profile == null || profile.points.size < 2 || profile.lengthMeters <= 0) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        source.setGeoJson(line(profile.points.map { it.point }, dashed = false))
        val grades = profile.grades()
        val stops = ArrayList<Expression.Stop>()
        var previous: SlopeClass? = null
        profile.points.forEachIndexed { i, p ->
            val cls = SlopeClass.of(grades[i])
            // A stop only where the class changes keeps the expression small on long routes.
            if (cls != previous || i == profile.points.lastIndex) {
                stops += stop((p.distanceMeters / profile.lengthMeters).toFloat(), color(cls.color.toInt()))
                previous = cls
            }
        }
        layer.setProperties(
            PropertyFactory.lineGradient(interpolate(linear(), lineProgress(), *stops.distinctBy { it.toString() }.toTypedArray())),
        )
    }

    /** On a closed loop the arrival is the start again: it keeps the start colour. */
    private fun pointFeatures(
        points: List<GeoPoint>,
        selected: Int?,
        closed: Boolean,
    ): List<Feature> =
        points.mapIndexed { i, p ->
            Feature.fromGeometry(point(p)).apply {
                addNumberProperty(PROP_INDEX, i)
                addStringProperty(
                    PROP_ROLE,
                    when {
                        i == 0 || (closed && i == points.lastIndex) -> ROLE_START
                        i == points.lastIndex -> ROLE_END
                        else -> ROLE_MID
                    },
                )
                addBooleanProperty(PROP_SELECTED, i == selected)
            }
        }

    private fun line(
        geometry: List<GeoPoint>,
        dashed: Boolean,
    ): Feature = Feature.fromGeometry(LineString.fromLngLats(geometry.map(::point))).apply { addBooleanProperty(PROP_DASHED, dashed) }

    private fun point(p: GeoPoint) = Point.fromLngLat(p.longitude, p.latitude)

    private companion object {
        const val SRC_ROUTE = "planner-route"
        const val SRC_POINTS = "planner-points"
        const val SRC_MARKER = "planner-marker"
        const val SRC_SLOPE = "planner-slope"
        const val SRC_SHOWN = "shown-routes"
        const val LAYER_SHOWN = "shown-routes"
        const val LAYER_CASING = "planner-casing"
        const val LAYER_LINE = "planner-line"
        const val LAYER_DASHED = "planner-dashed"
        const val LAYER_SLOPE = "planner-slope"
        const val LAYER_POINTS = "planner-points"
        const val LAYER_MARKER = "planner-marker"
        const val PROP_DASHED = "dashed"
        const val PROP_INDEX = "index"
        const val PROP_ROLE = "role"
        const val PROP_SELECTED = "selected"
        const val ROLE_START = "start"
        const val ROLE_END = "end"
        const val ROLE_MID = "mid"
        const val POINT_RADIUS = 7f
        const val SELECTED_RADIUS = 10f
        const val ROUTE_COLOR = "#F4511E"
        const val CASING_COLOR = "#FFFFFF"
        const val START_COLOR = "#2E7D32"
        const val END_COLOR = "#C62828"
        const val MID_COLOR = "#FFFFFF"
        const val MARKER_COLOR = "#1A1A1A"
        const val SHOWN_COLOR = "#7B1FA2"
    }
}
