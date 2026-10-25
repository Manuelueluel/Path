package it.traccia.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.traccia.core.storage.route.Route
import it.traccia.core.ui.permission.AppPermission
import it.traccia.core.ui.permission.openAppSettings
import it.traccia.core.ui.permission.rememberPermissionLauncher
import it.traccia.feature.gpx.ui.GpxExportDialog
import it.traccia.feature.map.maplibre.MapLibreController
import it.traccia.feature.map.maplibre.MapLibreMapView
import it.traccia.feature.map.maplibre.PlannerGestures
import it.traccia.feature.map.ui.CenterReadout
import it.traccia.feature.map.ui.Crosshair
import it.traccia.feature.map.ui.HeatmapStatusChip
import it.traccia.feature.map.ui.LayerSheet
import it.traccia.feature.map.ui.ScaleBar
import it.traccia.feature.planner.PlannerEffect
import it.traccia.feature.planner.PlannerEvent
import it.traccia.feature.planner.PlannerUiState
import it.traccia.feature.planner.PlannerViewModel
import it.traccia.feature.planner.ui.DeletePointButton
import it.traccia.feature.planner.ui.PlannerPanel
import it.traccia.feature.planner.ui.RouteSheet
import it.traccia.feature.planner.ui.SaveRouteDialog
import org.maplibre.android.style.sources.GeometryTileProvider
import kotlin.math.roundToInt
import it.traccia.feature.planner.R as PlannerR

/** Home: the map with the route planner on it. */
@Composable
internal fun MapScreen(
    onOpenSettings: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onOpenStravaLogin: () -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
    planner: PlannerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val plannerState by planner.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var controller by remember { mutableStateOf<MapLibreController?>(null) }
    var exporting by remember { mutableStateOf<Route?>(null) }
    val requestPermission = rememberPermissionLauncher { result -> viewModel.onEvent(MapEvent.LocationPermissionResult(result)) }
    val deniedMessage = stringResource(R.string.map_location_denied)
    val settingsAction = stringResource(R.string.map_location_open_settings)
    val clearedMessage = stringResource(PlannerR.string.planner_cleared)
    val restoreAction = stringResource(PlannerR.string.planner_restore)

    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(MapEvent.Resumed)
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MapEffect.CenterOnUser -> controller?.centerOnUser()
                MapEffect.RequestLocationPermission -> requestPermission(AppPermission.LOCATION)
                MapEffect.LocationPermissionDenied -> {
                    val result = snackbarHostState.showSnackbar(deniedMessage, settingsAction, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) context.openAppSettings()
                }
            }
        }
    }
    LaunchedEffect(planner) {
        planner.effects.collect { effect ->
            when (effect) {
                // No confirmation for "Cancella tutto": a few seconds to restore instead (M5.6).
                PlannerEffect.Cleared -> {
                    val result = snackbarHostState.showSnackbar(clearedMessage, restoreAction, duration = SnackbarDuration.Short)
                    planner.onEvent(
                        if (result ==
                            SnackbarResult.ActionPerformed
                        ) {
                            PlannerEvent.RestoreCleared
                        } else {
                            PlannerEvent.ClearedSnackbarGone
                        },
                    )
                }
                is PlannerEffect.Saved -> snackbarHostState.showSnackbar(context.getString(PlannerR.string.planner_saved, effect.name))
                is PlannerEffect.Export -> exporting = effect.route
            }
        }
    }

    MapContent(
        uiState = uiState,
        planner = plannerState,
        contourProvider = viewModel.contourProvider,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onPlannerEvent = planner::onEvent,
        onControllerReady = { controller = it },
        onOpenSettings = onOpenSettings,
        onOpenOfflineMaps = onOpenOfflineMaps,
        onOpenStravaLogin = onOpenStravaLogin,
        onOpenLibrary = onOpenLibrary,
    )
    exporting?.let { route ->
        GpxExportDialog(
            route = route,
            onMessage = { },
            onDismiss = { exporting = null },
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun MapContent(
    uiState: MapUiState,
    planner: PlannerUiState,
    contourProvider: GeometryTileProvider,
    snackbarHostState: SnackbarHostState,
    onEvent: (MapEvent) -> Unit,
    onPlannerEvent: (PlannerEvent) -> Unit,
    onControllerReady: (MapLibreController) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onOpenStravaLogin: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    var deleteAnchor by remember { mutableStateOf<Offset?>(null) }
    val gestures =
        remember(onPlannerEvent) {
            PlannerGestures(
                onMapTap = { onPlannerEvent(PlannerEvent.MapTapped(it)) },
                onPointTap = { onPlannerEvent(PlannerEvent.PointTapped(it)) },
                onDragStart = { onPlannerEvent(PlannerEvent.DragStarted(it)) },
                onDragMove = { onPlannerEvent(PlannerEvent.DragMoved(it)) },
                onDragEnd = { onPlannerEvent(PlannerEvent.DragEnded(it)) },
                onSelectedPointMoved = { deleteAnchor = it },
            )
        }
    Box(Modifier.fillMaxSize()) {
        val initialCamera = uiState.initialCamera
        if (uiState.isReady && initialCamera != null) {
            MapLibreMapView(
                state = uiState,
                planner = planner,
                initialCamera = initialCamera,
                contourProvider = contourProvider,
                gestures = gestures,
                onCameraMoved = { lat, lon, zoom -> onEvent(MapEvent.CameraMoved(lat, lon, zoom)) },
                onCameraIdle = { camera, area -> onEvent(MapEvent.CameraIdle(camera, area)) },
                onControllerReady = onControllerReady,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Crosshair(Modifier.align(Alignment.Center))

        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
        ) {
            Column(Modifier.align(Alignment.TopEnd), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MapButton(icon = Icons.Filled.Settings, label = R.string.map_open_settings, onClick = onOpenSettings)
                MapButton(drawable = R.drawable.ic_layers, label = R.string.map_open_layers) { onEvent(MapEvent.LayersClicked) }
                MapButton(drawable = R.drawable.ic_tune, label = R.string.map_open_route) { onPlannerEvent(PlannerEvent.OpenSheet) }
                MapButton(drawable = R.drawable.ic_routes, label = R.string.map_open_library, onClick = onOpenLibrary)
            }
            if (uiState.heatmap.settings.visible) {
                HeatmapStatusChip(
                    heatmap = uiState.heatmap,
                    onRefresh = { onEvent(MapEvent.HeatmapRefreshArea) },
                    onLogin = onOpenStravaLogin,
                    modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 64.dp),
                )
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SnackbarHost(snackbarHostState)
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        uiState.scaleBar?.let { ScaleBar(it) }
                        uiState.center?.let { CenterReadout(it) }
                    }
                    Spacer(Modifier.weight(1f))
                    val colors = MaterialTheme.colorScheme
                    FloatingActionButton(
                        onClick = { onEvent(MapEvent.CenterOnMeClicked) },
                        containerColor = colors.surface,
                        contentColor = if (uiState.isLocationEnabled) colors.primary else colors.onSurface,
                    ) {
                        Icon(painterResource(R.drawable.ic_my_location), contentDescription = stringResource(R.string.map_center_on_me))
                    }
                }
                PlannerPanel(planner, onPlannerEvent)
            }
        }

        val anchor = deleteAnchor
        if (planner.selectedPoint != null && anchor != null) {
            val density = LocalDensity.current
            DeletePointButton(
                onDelete = { onPlannerEvent(PlannerEvent.DeleteSelected) },
                modifier =
                    Modifier.offset {
                        // Centred above the point, clear of the finger.
                        IntOffset(
                            (anchor.x - with(density) { DELETE_HALF_WIDTH.dp.toPx() }).roundToInt(),
                            (
                                anchor.y -
                                    with(density) { DELETE_LIFT.dp.toPx() }
                            ).roundToInt(),
                        )
                    },
            )
        }
    }

    if (uiState.isLayerSheetOpen) {
        LayerSheet(
            style = uiState.style,
            contoursVisible = uiState.contoursVisible,
            hillshadeVisible = uiState.hillshadeVisible,
            onStyleSelected = { onEvent(MapEvent.StyleSelected(it)) },
            onContoursToggled = { onEvent(MapEvent.ContoursToggled(it)) },
            onHillshadeToggled = { onEvent(MapEvent.HillshadeToggled(it)) },
            onDismiss = { onEvent(MapEvent.LayersDismissed) },
            onOpenOfflineMaps = {
                onEvent(MapEvent.LayersDismissed)
                onOpenOfflineMaps()
            },
            heatmap = uiState.heatmap,
            onHeatmapEvent = onEvent,
            onOpenStravaLogin = {
                onEvent(MapEvent.LayersDismissed)
                onOpenStravaLogin()
            },
        )
    }
    if (planner.isSheetOpen) RouteSheet(planner, onPlannerEvent)
    if (planner.isSaveDialogOpen) {
        SaveRouteDialog(
            initialName = planner.routeName.orEmpty(),
            initialSport = planner.sport,
            onSave = { name, sport -> onPlannerEvent(PlannerEvent.SaveConfirmed(name, sport)) },
            onDismiss = { onPlannerEvent(PlannerEvent.SaveDismissed) },
        )
    }
}

@Composable
private fun MapButton(
    label: Int,
    icon: ImageVector? = null,
    drawable: Int? = null,
    onClick: () -> Unit,
) {
    SmallFloatingActionButton(onClick = onClick, containerColor = MaterialTheme.colorScheme.surface) {
        val description = stringResource(label)
        when {
            icon != null -> Icon(icon, contentDescription = description)
            drawable != null -> Icon(painterResource(drawable), contentDescription = description)
        }
    }
}

private const val DELETE_HALF_WIDTH = 56
private const val DELETE_LIFT = 72
