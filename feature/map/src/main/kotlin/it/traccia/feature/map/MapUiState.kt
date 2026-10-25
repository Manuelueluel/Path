package it.traccia.feature.map

import it.traccia.core.geo.ScaleBarSpec
import it.traccia.core.storage.route.Route
import it.traccia.core.ui.permission.PermissionResult
import it.traccia.feature.heatmap.HeatmapSettings
import it.traccia.feature.heatmap.HeatmapSource
import it.traccia.feature.heatmap.MapArea
import it.traccia.feature.heatmap.session.SessionState
import it.traccia.feature.map.prefs.CameraSnapshot
import it.traccia.feature.map.style.MapStyle

data class MapUiState(
    /** False until the saved preferences are loaded: the map is created only afterwards. */
    val isReady: Boolean = false,
    val style: MapStyle = MapStyle.TOPO,
    /** Resolved style JSON for [style], null while loading. */
    val styleJson: String? = null,
    val contoursVisible: Boolean = true,
    val hillshadeVisible: Boolean = true,
    val initialCamera: CameraSnapshot? = null,
    val center: CenterInfo? = null,
    val scaleBar: ScaleBarSpec? = null,
    val isLayerSheetOpen: Boolean = false,
    /** Location permission granted: the user position is drawn on the map. */
    val isLocationEnabled: Boolean = false,
    val heatmap: HeatmapUi = HeatmapUi(),
    /** Saved routes shown together on the map (M7.9). */
    val shownRoutes: List<Route> = emptyList(),
)

/** Strava heatmap overlay (M3). */
data class HeatmapUi(
    val settings: HeatmapSettings = HeatmapSettings(),
    /** Raster source to show, null when the overlay is off. */
    val source: HeatmapSource? = null,
    val session: SessionState = SessionState.LoggedOut,
    val sports: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    /** Download date of the oldest cached tile in the visible area (M3.10), null when unknown. */
    val oldestTileMillis: Long? = null,
    /** Bumped to make the map request the visible heatmap tiles again (M3.9). */
    val reloadToken: Int = 0,
)

/** What is under the crosshair at the center of the map. */
data class CenterInfo(
    val latitude: Double,
    val longitude: Double,
    /** Null while the camera moves or when no DEM data is available. */
    val elevation: Double?,
)

sealed interface MapEvent {
    data class CameraMoved(
        val latitude: Double,
        val longitude: Double,
        val zoom: Double,
    ) : MapEvent

    data class CameraIdle(
        val camera: CameraSnapshot,
        /** Visible area, null when the map cannot compute it yet. */
        val area: MapArea? = null,
    ) : MapEvent

    data object LayersClicked : MapEvent

    data object LayersDismissed : MapEvent

    data class StyleSelected(
        val style: MapStyle,
    ) : MapEvent

    data class ContoursToggled(
        val visible: Boolean,
    ) : MapEvent

    data class HillshadeToggled(
        val visible: Boolean,
    ) : MapEvent

    data object CenterOnMeClicked : MapEvent

    data class LocationPermissionResult(
        val result: PermissionResult,
    ) : MapEvent

    /** Back on screen: permissions may have changed in system settings. */
    data object Resumed : MapEvent

    data class HeatmapToggled(
        val visible: Boolean,
    ) : HeatmapEvent

    data class HeatmapSportSelected(
        val sport: String,
    ) : HeatmapEvent

    data class HeatmapColorSelected(
        val color: String,
    ) : HeatmapEvent

    data class HeatmapOpacityChanged(
        val opacity: Float,
    ) : HeatmapEvent

    /** Download again the heatmap tiles of the visible area (M3.9). */
    data object HeatmapRefreshArea : HeatmapEvent
}

/** Events of the heatmap overlay, handled together. */
sealed interface HeatmapEvent : MapEvent

/** One-shot commands for the view. */
sealed interface MapEffect {
    data object CenterOnUser : MapEffect

    data object RequestLocationPermission : MapEffect

    /** Location permanently denied: offer a shortcut to system settings. */
    data object LocationPermissionDenied : MapEffect
}
