package it.traccia.feature.map.maplibre

import android.annotation.SuppressLint
import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.traccia.core.geo.GeoPoint
import it.traccia.feature.heatmap.MapArea
import it.traccia.feature.map.MapUiState
import it.traccia.feature.map.R
import it.traccia.feature.map.prefs.CameraSnapshot
import it.traccia.feature.planner.PlannerUiState
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.sources.GeometryTileProvider

/** Map gestures for the planner: taps, long press on a point, drag and drop (M5). */
internal class PlannerGestures(
    val onMapTap: (GeoPoint) -> Unit,
    val onPointTap: (Int) -> Unit,
    val onDragStart: (Int) -> Unit,
    val onDragMove: (GeoPoint) -> Unit,
    val onDragEnd: (GeoPoint) -> Unit,
    /** Screen position (px) of the selected point, to anchor its "Cancella" option. */
    val onSelectedPointMoved: (Offset?) -> Unit,
)

/**
 * Hosts a MapLibre [MapView] in Compose. It only forwards state to the [MapLibreController]
 * and gestures and camera changes back to the caller; no decisions are taken here.
 */
@Composable
internal fun MapLibreMapView(
    state: MapUiState,
    planner: PlannerUiState,
    initialCamera: CameraSnapshot,
    contourProvider: GeometryTileProvider,
    gestures: PlannerGestures,
    onCameraMoved: (latitude: Double, longitude: Double, zoom: Double) -> Unit,
    onCameraIdle: (CameraSnapshot, MapArea?) -> Unit,
    onControllerReady: (MapLibreController) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    val currentOnMoved by rememberUpdatedState(onCameraMoved)
    val currentOnIdle by rememberUpdatedState(onCameraIdle)
    val currentOnReady by rememberUpdatedState(onControllerReady)
    val currentGestures by rememberUpdatedState(gestures)
    val selectedPoint by rememberUpdatedState(planner.selectedPoint?.let { planner.route.points.getOrNull(it) })
    var controller by remember { mutableStateOf<MapLibreController?>(null) }
    val hitRadiusPx = with(density) { HIT_RADIUS.dp.toPx() }
    // Index of the point being dragged; touch events go to the drag instead of the map meanwhile.
    val dragging = remember { intArrayOf(NO_DRAG) }

    val mapView =
        remember {
            val options =
                MapLibreMapOptions
                    .createFromAttributes(context)
                    .camera(
                        CameraPosition
                            .Builder()
                            .target(LatLng(initialCamera.latitude, initialCamera.longitude))
                            .zoom(initialCamera.zoom)
                            .bearing(initialCamera.bearing)
                            .build(),
                    ).logoEnabled(false)
            MapView(context, options).apply { onCreate(Bundle()) }
        }

    MapViewLifecycle(mapView)

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            with(density) {
                val margin = 12.dp.roundToPx()
                map.uiSettings.compassGravity = Gravity.TOP or Gravity.START
                map.uiSettings.setCompassMargins(margin, statusBarPx + margin, margin, margin)
                // Top-left under the compass: the bottom of the screen belongs to the planner panel.
                map.uiSettings.attributionGravity = Gravity.TOP or Gravity.START
                map.uiSettings.setAttributionMargins(margin, statusBarPx + ATTRIBUTION_TOP.dp.roundToPx(), margin, margin)
                map.uiSettings.isTiltGesturesEnabled = false
            }

            val reportMove =
                setUpListeners(
                    mapView,
                    map,
                    hitRadiusPx,
                    dragging,
                    { controller },
                    { selectedPoint },
                    { currentGestures },
                ) { lat, lon, zoom ->
                    currentOnMoved(lat, lon, zoom)
                }
            map.addOnCameraIdleListener {
                val position = map.cameraPosition
                val target = position.target ?: return@addOnCameraIdleListener
                val bounds = map.projection.visibleRegion.latLngBounds
                val area = MapArea(bounds.longitudeWest, bounds.latitudeSouth, bounds.longitudeEast, bounds.latitudeNorth, position.zoom)
                currentOnIdle(CameraSnapshot(target.latitude, target.longitude, position.zoom, position.bearing), area)
            }
            reportMove()
            MapLibreController(context, map, contourProvider).also {
                controller = it
                currentOnReady(it)
            }
        }
    }

    val ready = controller
    LaunchedEffect(ready, state.styleJson) {
        val json = state.styleJson ?: return@LaunchedEffect
        ready?.setStyle(state.style, json)
    }
    LaunchedEffect(ready, state.contoursVisible, state.hillshadeVisible) {
        ready?.setLayersVisible(contours = state.contoursVisible, hillshade = state.hillshadeVisible)
    }
    LaunchedEffect(ready, state.heatmap.source, state.heatmap.settings.opacity, state.heatmap.reloadToken) {
        ready?.setHeatmap(state.heatmap.source, state.heatmap.settings.opacity, state.heatmap.reloadToken)
    }
    LaunchedEffect(ready, planner) {
        ready?.renderPlanner(planner)
        val map = ready?.map ?: return@LaunchedEffect
        gestures.onSelectedPointMoved(
            selectedPoint?.let { p -> map.projection.toScreenLocation(LatLng(p.latitude, p.longitude)).let { Offset(it.x, it.y) } },
        )
    }
    LaunchedEffect(ready, state.shownRoutes) {
        ready?.renderShownRoutes(state.shownRoutes)
    }
    LaunchedEffect(ready, state.isLocationEnabled) {
        ready?.setLocationEnabled(state.isLocationEnabled)
    }

    val description = stringResource(R.string.map_description)
    AndroidView(factory = { mapView }, modifier = modifier.semantics { contentDescription = description })
}

/**
 * Camera move, tap, long press and drag listeners. Returns the function that reports the
 * current camera, also called once at start.
 */
@SuppressLint("ClickableViewAccessibility")
@Suppress("LongParameterList")
private fun setUpListeners(
    mapView: MapView,
    map: MapLibreMap,
    hitRadiusPx: Float,
    dragging: IntArray,
    controller: () -> MapLibreController?,
    selectedPoint: () -> GeoPoint?,
    gestures: () -> PlannerGestures,
    onMoved: (Double, Double, Double) -> Unit,
): () -> Unit {
    fun geo(latLng: LatLng) = GeoPoint(latLng.latitude, latLng.longitude)

    fun reportMove() {
        val position = map.cameraPosition
        val target = position.target ?: return
        onMoved(target.latitude, target.longitude, position.zoom)
        gestures().onSelectedPointMoved(
            selectedPoint()?.let { p ->
                map.projection.toScreenLocation(LatLng(p.latitude, p.longitude)).let { Offset(it.x, it.y) }
            },
        )
    }
    map.addOnCameraMoveListener { reportMove() }
    map.addOnMapClickListener { latLng ->
        val hit = controller()?.plannerPointAt(map.projection.toScreenLocation(latLng), hitRadiusPx)
        if (hit != null) gestures().onPointTap(hit) else gestures().onMapTap(geo(latLng))
        true
    }
    map.addOnMapLongClickListener { latLng ->
        val hit =
            controller()?.plannerPointAt(map.projection.toScreenLocation(latLng), hitRadiusPx) ?: return@addOnMapLongClickListener false
        dragging[0] = hit
        gestures().onDragStart(hit)
        true
    }
    // While a point is dragged the touch stream goes to the drag, not to the map.
    mapView.setOnTouchListener { _, event ->
        if (dragging[0] == NO_DRAG) return@setOnTouchListener false
        val position = geo(map.projection.fromScreenLocation(PointF(event.x, event.y)))
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> gestures().onDragMove(position)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging[0] = NO_DRAG
                gestures().onDragEnd(position)
            }
        }
        true
    }
    return ::reportMove
}

private const val HIT_RADIUS = 24
private const val ATTRIBUTION_TOP = 64
private const val NO_DRAG = -1

/** Forwards the host lifecycle to the [MapView], as MapLibre requires. */
@Composable
private fun MapViewLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // Leaving the screen: bring the view down through the whole lifecycle.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
}
