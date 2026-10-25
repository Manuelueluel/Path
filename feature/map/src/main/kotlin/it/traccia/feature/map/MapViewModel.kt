package it.traccia.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.traccia.core.geo.MapScale
import it.traccia.core.storage.route.RouteRepository
import it.traccia.core.terrain.ElevationSource
import it.traccia.core.ui.permission.AppPermission
import it.traccia.core.ui.permission.PermissionChecker
import it.traccia.core.ui.permission.PermissionStatus
import it.traccia.core.ui.permission.resolvePermissionStatus
import it.traccia.feature.heatmap.Heatmap
import it.traccia.feature.heatmap.HeatmapSettings
import it.traccia.feature.heatmap.MapArea
import it.traccia.feature.map.prefs.CameraSnapshot
import it.traccia.feature.map.prefs.MapPreferencesRepository
import it.traccia.feature.map.style.MapStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.maplibre.android.style.sources.GeometryTileProvider
import javax.inject.Inject

/** Loads a style JSON; a function type so tests do not need assets. */
fun interface StyleLoader {
    suspend fun load(style: MapStyle): String
}

@HiltViewModel
class MapViewModel
    @Inject
    constructor(
        private val preferences: MapPreferencesRepository,
        private val elevation: ElevationSource,
        private val permissionChecker: PermissionChecker,
        private val styleLoader: StyleLoader,
        /** Source of in-app computed contour lines, handed to the map view. */
        val contourProvider: GeometryTileProvider,
        private val heatmap: Heatmap,
        private val routes: RouteRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MapUiState())
        val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

        private val _effects = Channel<MapEffect>(Channel.BUFFERED)
        val effects: Flow<MapEffect> = _effects.receiveAsFlow()

        private var elevationJob: Job? = null
        private var heatmapAgeJob: Job? = null
        private var visibleArea: MapArea? = null

        init {
            _uiState.update {
                it.copy(
                    isLocationEnabled = permissionChecker.isGranted(AppPermission.LOCATION),
                    heatmap = it.heatmap.copy(sports = heatmap.config.sports.map { s -> s.id }, colors = heatmap.config.colors),
                )
            }
            viewModelScope.launch {
                routes.shownRoutes.collect { shown -> _uiState.update { it.copy(shownRoutes = shown) } }
            }
            viewModelScope.launch {
                combine(heatmap.settings, heatmap.session) { settings, session -> settings to session }.collect { (settings, session) ->
                    _uiState.update {
                        it.copy(
                            heatmap =
                                it.heatmap.copy(
                                    settings = settings,
                                    session = session,
                                    source = if (settings.visible) heatmap.source(settings) else null,
                                ),
                        )
                    }
                    updateHeatmapAge()
                }
            }
            viewModelScope.launch {
                preferences.preferences.collect { prefs ->
                    _uiState.update {
                        it.copy(
                            isReady = true,
                            style = prefs.style,
                            contoursVisible = prefs.contoursVisible,
                            hillshadeVisible = prefs.hillshadeVisible,
                            // The initial camera is read once; afterwards the map owns the camera.
                            initialCamera = it.initialCamera ?: prefs.camera,
                        )
                    }
                }
            }
            viewModelScope.launch {
                // The previous style stays on screen until the new one is loaded.
                uiState
                    .filter { it.isReady }
                    .map { it.style }
                    .distinctUntilChanged()
                    .collectLatest { style ->
                        val json = styleLoader.load(style)
                        _uiState.update { if (it.style == style) it.copy(styleJson = json) else it }
                    }
            }
        }

        fun onEvent(event: MapEvent) {
            when (event) {
                is MapEvent.CameraMoved -> onCameraMoved(event)
                is MapEvent.CameraIdle -> {
                    visibleArea = event.area
                    onCameraIdle(event.camera)
                    updateHeatmapAge()
                }
                MapEvent.LayersClicked -> _uiState.update { it.copy(isLayerSheetOpen = true) }
                MapEvent.LayersDismissed -> _uiState.update { it.copy(isLayerSheetOpen = false) }
                is MapEvent.StyleSelected -> viewModelScope.launch { preferences.setStyle(event.style) }
                is MapEvent.ContoursToggled -> viewModelScope.launch { preferences.setContoursVisible(event.visible) }
                is MapEvent.HillshadeToggled -> viewModelScope.launch { preferences.setHillshadeVisible(event.visible) }
                MapEvent.CenterOnMeClicked -> onCenterOnMe()
                is MapEvent.LocationPermissionResult -> onLocationPermissionResult(event)
                MapEvent.Resumed ->
                    _uiState.update { it.copy(isLocationEnabled = permissionChecker.isGranted(AppPermission.LOCATION)) }
                is HeatmapEvent -> onHeatmapEvent(event)
            }
        }

        private fun onHeatmapEvent(event: HeatmapEvent) {
            when (event) {
                is MapEvent.HeatmapToggled -> updateHeatmap { it.copy(visible = event.visible) }
                is MapEvent.HeatmapSportSelected -> updateHeatmap { it.copy(sport = event.sport) }
                is MapEvent.HeatmapColorSelected -> updateHeatmap { it.copy(color = event.color) }
                is MapEvent.HeatmapOpacityChanged -> updateHeatmap { it.copy(opacity = event.opacity) }
                MapEvent.HeatmapRefreshArea -> refreshHeatmapArea()
            }
        }

        private fun updateHeatmap(transform: (HeatmapSettings) -> HeatmapSettings) {
            viewModelScope.launch { heatmap.updateSettings(transform) }
        }

        private fun refreshHeatmapArea() {
            val area = visibleArea ?: return
            val settings = _uiState.value.heatmap.settings
            if (!settings.visible) return
            viewModelScope.launch {
                heatmap.refresh(settings, area)
                _uiState.update { it.copy(heatmap = it.heatmap.copy(reloadToken = it.heatmap.reloadToken + 1, oldestTileMillis = null)) }
                updateHeatmapAge()
            }
        }

        /** Age of the visible heatmap, read again after the tiles had time to load. */
        private fun updateHeatmapAge() {
            heatmapAgeJob?.cancel()
            val area = visibleArea ?: return
            val settings = _uiState.value.heatmap.settings
            if (!settings.visible) return
            heatmapAgeJob =
                viewModelScope.launch {
                    repeat(HEATMAP_AGE_CHECKS) {
                        val age = heatmap.age(settings, area)
                        _uiState.update { it.copy(heatmap = it.heatmap.copy(oldestTileMillis = age?.oldestMillis)) }
                        delay(HEATMAP_AGE_RECHECK_MS)
                    }
                }
        }

        private fun onCameraMoved(event: MapEvent.CameraMoved) {
            elevationJob?.cancel()
            _uiState.update {
                it.copy(
                    center = CenterInfo(event.latitude, event.longitude, elevation = null),
                    scaleBar = MapScale.scaleBar(MapScale.metersPerDp(event.latitude, event.zoom), SCALE_BAR_MAX_WIDTH_DP),
                )
            }
        }

        private fun onCameraIdle(camera: CameraSnapshot) {
            onCameraMoved(MapEvent.CameraMoved(camera.latitude, camera.longitude, camera.zoom))
            elevationJob =
                viewModelScope.launch {
                    preferences.saveCamera(camera)
                    // Short debounce: idle events can come in bursts after a fling.
                    delay(ELEVATION_DEBOUNCE_MS)
                    val meters = elevation.elevationAt(camera.latitude, camera.longitude)
                    _uiState.update { state ->
                        val center = state.center
                        if (center != null && center.latitude == camera.latitude && center.longitude == camera.longitude) {
                            state.copy(center = center.copy(elevation = meters))
                        } else {
                            state
                        }
                    }
                }
        }

        private fun onCenterOnMe() {
            val effect =
                if (permissionChecker.isGranted(AppPermission.LOCATION)) {
                    _uiState.update { it.copy(isLocationEnabled = true) }
                    MapEffect.CenterOnUser
                } else {
                    MapEffect.RequestLocationPermission
                }
            _effects.trySend(effect)
        }

        private fun onLocationPermissionResult(event: MapEvent.LocationPermissionResult) {
            val status =
                resolvePermissionStatus(
                    isGranted = event.result.isGranted,
                    shouldShowRationale = event.result.shouldShowRationale,
                    wasRequested = true,
                )
            _uiState.update { it.copy(isLocationEnabled = status == PermissionStatus.GRANTED) }
            when (status) {
                PermissionStatus.GRANTED -> _effects.trySend(MapEffect.CenterOnUser)
                PermissionStatus.PERMANENTLY_DENIED -> _effects.trySend(MapEffect.LocationPermissionDenied)
                // Denied once: the user can simply tap the button again.
                PermissionStatus.DENIED, PermissionStatus.NOT_REQUESTED -> Unit
            }
        }

        private companion object {
            const val SCALE_BAR_MAX_WIDTH_DP = 96f
            const val ELEVATION_DEBOUNCE_MS = 150L
            const val HEATMAP_AGE_CHECKS = 2
            const val HEATMAP_AGE_RECHECK_MS = 3_000L
        }
    }
