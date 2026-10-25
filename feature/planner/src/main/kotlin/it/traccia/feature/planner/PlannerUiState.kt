package it.traccia.feature.planner

import it.traccia.core.common.Sport
import it.traccia.core.geo.GeoPoint
import it.traccia.core.storage.route.Route
import it.traccia.core.storage.route.RoutingSettings
import it.traccia.feature.elevation.ElevationProfile

data class RouteStats(
    val distanceMeters: Double,
    /** Null until the elevation profile is computed. */
    val ascentMeters: Double?,
    val descentMeters: Double?,
    val durationSeconds: Double?,
)

/** A point being dragged (M5.4): drawn at its new place, segments recomputed on release. */
data class DragPreview(
    val index: Int,
    val position: GeoPoint,
)

data class PlannerUiState(
    val route: PlannedRoute = PlannedRoute(),
    val sport: Sport = Sport.HIKING,
    val routing: RoutingSettings = RoutingSettings(),
    val availableOptions: List<String> = emptyList(),
    val routeId: Long? = null,
    val routeName: String? = null,
    /** Point showing its only option, "Cancella" (M5.3). */
    val selectedPoint: Int? = null,
    val drag: DragPreview? = null,
    val stats: RouteStats? = null,
    val profile: ElevationProfile? = null,
    val profileExpanded: Boolean = false,
    /** Distance highlighted on chart and map (M6.4). */
    val highlightMeters: Double? = null,
    val isRouting: Boolean = false,
    val isSheetOpen: Boolean = false,
    /** Save dialog: the only question the planner asks, once the drawing is done (M5.10). */
    val isSaveDialogOpen: Boolean = false,
) {
    val canUndo: Boolean get() = !route.isEmpty
    val canClear: Boolean get() = !route.isEmpty
    val canSave: Boolean get() = route.points.size >= 2

    /** Map position of the highlighted distance. */
    val highlightPoint: GeoPoint? get() = highlightMeters?.let { profile?.at(it)?.point }
}

sealed interface PlannerEvent {
    data class MapTapped(
        val position: GeoPoint,
    ) : PlannerEvent

    data class PointTapped(
        val index: Int,
    ) : PlannerEvent

    data object SelectionDismissed : PlannerEvent

    data object DeleteSelected : PlannerEvent

    data class DragStarted(
        val index: Int,
    ) : PlannerEvent

    data class DragMoved(
        val position: GeoPoint,
    ) : PlannerEvent

    data class DragEnded(
        val position: GeoPoint,
    ) : PlannerEvent

    data object Undo : PlannerEvent

    data object CloseLoop : PlannerEvent

    data object ClearAll : PlannerEvent

    data object RestoreCleared : PlannerEvent

    data object ClearedSnackbarGone : PlannerEvent

    data object OpenSheet : PlannerEvent

    data object CloseSheet : PlannerEvent

    data class SportSelected(
        val sport: Sport,
    ) : PlannerEvent

    data class StraightLineSelected(
        val straight: Boolean,
    ) : PlannerEvent

    data class OptionToggled(
        val option: String,
    ) : PlannerEvent

    data object SaveClicked : PlannerEvent

    data class SaveConfirmed(
        val name: String,
        val sport: Sport,
    ) : PlannerEvent

    data object SaveDismissed : PlannerEvent

    data object ExportClicked : PlannerEvent

    data object NewRoute : PlannerEvent

    data object ProfileToggled : PlannerEvent

    data class ProfileScrubbed(
        val meters: Double?,
    ) : PlannerEvent
}

sealed interface PlannerEffect {
    /** "Cancella tutto" happened: offer "Ripristina" for a few seconds (M5.6). */
    data object Cleared : PlannerEffect

    data class Saved(
        val name: String,
    ) : PlannerEffect

    data class Export(
        val route: Route,
    ) : PlannerEffect
}
