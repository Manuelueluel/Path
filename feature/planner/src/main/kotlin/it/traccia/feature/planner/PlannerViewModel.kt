package it.traccia.feature.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.traccia.core.common.AppResult
import it.traccia.core.common.Sport
import it.traccia.core.geo.GeoPoint
import it.traccia.core.geo.distanceTo
import it.traccia.core.geo.lengthMeters
import it.traccia.core.storage.route.Route
import it.traccia.core.storage.route.RouteRepository
import it.traccia.core.storage.route.RouteSegment
import it.traccia.core.storage.route.SegmentKind
import it.traccia.feature.elevation.DurationEstimate
import it.traccia.feature.elevation.RouteElevation
import it.traccia.feature.planner.draft.DraftStore
import it.traccia.feature.planner.draft.PlannerDraft
import it.traccia.feature.routing.RoutingOptions
import it.traccia.feature.routing.SegmentRouter
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Route drawing like the Suunto route planner: taps, long press to drag, and three buttons.
 * No popups or questions while drawing; the only question is the name when saving.
 */
@HiltViewModel
class PlannerViewModel
    @Inject
    constructor(
        private val router: SegmentRouter,
        private val routingOptions: RoutingOptions,
        private val elevation: RouteElevation,
        private val routes: RouteRepository,
        private val drafts: DraftStore,
        private val commands: PlannerCommands,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(PlannerUiState())
        val uiState: StateFlow<PlannerUiState> = _uiState.asStateFlow()

        private val _effects = Channel<PlannerEffect>(Channel.BUFFERED)
        val effects: Flow<PlannerEffect> = _effects.receiveAsFlow()

        /** Running segment computations, by end points (M4.5). */
        private val routingJobs = HashMap<Pair<GeoPoint, GeoPoint>, Job>()
        private var profileJob: Job? = null
        private var draftJob: Job? = null

        /** State before "Cancella tutto", kept while the "Ripristina" snackbar is visible. */
        private var cleared: PlannerUiState? = null

        init {
            _uiState.update { it.copy(availableOptions = routingOptions.optionsFor(it.sport)) }
            viewModelScope.launch {
                drafts.load()?.let { draft ->
                    _uiState.update {
                        it.copy(
                            route = draft.route,
                            sport = draft.sport,
                            routing = draft.routing,
                            routeId = draft.routeId,
                            routeName = draft.routeName,
                            availableOptions = routingOptions.optionsFor(draft.sport),
                        )
                    }
                    onRouteChanged(saveDraft = false)
                }
                commands.openRoute.filterNotNull().collect { id ->
                    open(id)
                    commands.consumed(id)
                }
            }
        }

        fun onEvent(event: PlannerEvent) {
            when (event) {
                is PlannerEvent.MapTapped, is PlannerEvent.PointTapped, PlannerEvent.SelectionDismissed, PlannerEvent.DeleteSelected,
                is PlannerEvent.DragStarted, is PlannerEvent.DragMoved, is PlannerEvent.DragEnded,
                -> onDrawingEvent(event)
                PlannerEvent.Undo -> editRoute { it.undo() }
                PlannerEvent.CloseLoop -> editRoute { it.closeLoop() }
                PlannerEvent.ClearAll -> clearAll(keepIdentity = true)
                PlannerEvent.NewRoute -> clearAll(keepIdentity = false)
                PlannerEvent.RestoreCleared -> restoreCleared()
                PlannerEvent.ClearedSnackbarGone -> cleared = null
                PlannerEvent.ProfileToggled -> _uiState.update { it.copy(profileExpanded = !it.profileExpanded, highlightMeters = null) }
                is PlannerEvent.ProfileScrubbed -> _uiState.update { it.copy(highlightMeters = event.meters) }
                else -> onSettingsEvent(event)
            }
        }

        /** Gestures on the map (M5.1 - M5.4). */
        private fun onDrawingEvent(event: PlannerEvent) {
            when (event) {
                is PlannerEvent.MapTapped -> onMapTapped(event.position)
                is PlannerEvent.PointTapped -> selectPoint(event.index)
                PlannerEvent.SelectionDismissed -> _uiState.update { it.copy(selectedPoint = null, highlightMeters = null) }
                PlannerEvent.DeleteSelected -> deleteSelected()
                is PlannerEvent.DragStarted ->
                    _uiState.update { s ->
                        s.copy(drag = DragPreview(event.index, s.route.points[event.index]), selectedPoint = null)
                    }
                is PlannerEvent.DragMoved -> _uiState.update { s -> s.copy(drag = s.drag?.copy(position = event.position)) }
                is PlannerEvent.DragEnded -> endDrag(event.position)
                else -> Unit
            }
        }

        /** Route settings, saving and export: outside the drawing (M5.9, M5.10). */
        private fun onSettingsEvent(event: PlannerEvent) {
            when (event) {
                PlannerEvent.OpenSheet -> _uiState.update { it.copy(isSheetOpen = true) }
                PlannerEvent.CloseSheet -> _uiState.update { it.copy(isSheetOpen = false) }
                is PlannerEvent.SportSelected ->
                    changeRouting { s ->
                        s.copy(sport = event.sport, availableOptions = routingOptions.optionsFor(event.sport))
                    }
                is PlannerEvent.StraightLineSelected ->
                    changeRouting { s ->
                        s.copy(routing = s.routing.copy(straightLine = event.straight))
                    }
                is PlannerEvent.OptionToggled ->
                    changeRouting { s ->
                        val options = s.routing.options
                        s.copy(
                            routing =
                                s.routing.copy(
                                    options =
                                        if (event.option in
                                            options
                                        ) {
                                            options - event.option
                                        } else {
                                            options + event.option
                                        },
                                ),
                        )
                    }
                PlannerEvent.SaveClicked ->
                    if (_uiState.value.canSave) {
                        _uiState.update {
                            it.copy(
                                isSaveDialogOpen = true,
                                isSheetOpen = false,
                            )
                        }
                    }
                is PlannerEvent.SaveConfirmed -> save(event.name, event.sport)
                PlannerEvent.SaveDismissed -> _uiState.update { it.copy(isSaveDialogOpen = false) }
                PlannerEvent.ExportClicked ->
                    if (_uiState.value.canSave) {
                        _effects.trySend(
                            PlannerEffect.Export(currentRoute(_uiState.value)),
                        )
                    }
                else -> Unit
            }
        }

        /** A tap on the map adds a point; with a point selected it only closes the "Cancella" option. */
        private fun onMapTapped(position: GeoPoint) {
            if (_uiState.value.selectedPoint != null) {
                _uiState.update { it.copy(selectedPoint = null, highlightMeters = null) }
            } else {
                editRoute { it.add(position) }
            }
        }

        private fun selectPoint(index: Int) {
            _uiState.update { s ->
                if (index !in s.route.points.indices) return@update s
                s.copy(selectedPoint = index, highlightMeters = s.route.controlPointDistances()[index].takeIf { s.profile != null })
            }
        }

        private fun deleteSelected() {
            val index = _uiState.value.selectedPoint ?: return
            _uiState.update { it.copy(selectedPoint = null, highlightMeters = null) }
            editRoute { it.deleteAt(index) }
        }

        private fun endDrag(position: GeoPoint) {
            val drag = _uiState.value.drag ?: return
            _uiState.update { it.copy(drag = null) }
            editRoute { it.moveTo(drag.index, position) }
        }

        private fun editRoute(transform: (PlannedRoute) -> PlannedRoute) {
            _uiState.update { it.copy(route = transform(it.route)) }
            onRouteChanged()
        }

        private fun changeRouting(transform: (PlannerUiState) -> PlannerUiState) {
            _uiState.update { transform(it).let { s -> s.copy(route = s.route.invalidateAll()) } }
            cancelAllRouting()
            onRouteChanged()
        }

        private fun clearAll(keepIdentity: Boolean) {
            val current = _uiState.value
            if (current.route.isEmpty && keepIdentity) return
            cleared = current
            cancelAllRouting()
            _uiState.update {
                it.copy(
                    route = PlannedRoute(),
                    selectedPoint = null,
                    drag = null,
                    isSheetOpen = false,
                    routeId = if (keepIdentity) it.routeId else null,
                    routeName = if (keepIdentity) it.routeName else null,
                )
            }
            onRouteChanged()
            _effects.trySend(PlannerEffect.Cleared)
        }

        private fun restoreCleared() {
            val snapshot = cleared ?: return
            cleared = null
            _uiState.update { snapshot.copy(isSheetOpen = false, isSaveDialogOpen = false) }
            onRouteChanged()
        }

        private fun onRouteChanged(saveDraft: Boolean = true) {
            scheduleRouting()
            updateStats()
            scheduleProfile()
            if (saveDraft) scheduleDraft()
        }

        /** Starts the missing segment computations and cancels the obsolete ones (M4.5). */
        private fun scheduleRouting() {
            val state = _uiState.value
            val route = state.route
            val wanted = route.pending.map { route.points[it] to route.points[it + 1] }.toSet()
            routingJobs.keys.filterNot { it in wanted }.forEach { routingJobs.remove(it)?.cancel() }
            wanted.filterNot { it in routingJobs }.forEach { key ->
                val (from, to) = key
                if (state.routing.straightLine || from == to) {
                    applySegment(from, to, RouteSegment(listOf(from, to), SegmentKind.STRAIGHT))
                } else {
                    routingJobs[key] =
                        viewModelScope.launch {
                            val segment =
                                when (val result = router.route(from, to, state.sport, state.routing)) {
                                    is AppResult.Success -> RouteSegment(attach(from, result.value, to), SegmentKind.ROUTED)
                                    // Not computable: dashed straight line, no dialog (M4.6).
                                    is AppResult.Failure -> RouteSegment(listOf(from, to), SegmentKind.FALLBACK)
                                }
                            routingJobs.remove(key)
                            applySegment(from, to, segment)
                        }
                }
            }
            _uiState.update { it.copy(isRouting = routingJobs.isNotEmpty()) }
        }

        private fun applySegment(
            from: GeoPoint,
            to: GeoPoint,
            segment: RouteSegment,
        ) {
            _uiState.update { it.copy(route = it.route.withSegment(from, to, segment), isRouting = routingJobs.isNotEmpty()) }
            updateStats()
            scheduleProfile()
            scheduleDraft()
        }

        private fun cancelAllRouting() {
            routingJobs.values.forEach { it.cancel() }
            routingJobs.clear()
        }

        private fun updateStats() {
            _uiState.update { s ->
                val distance = s.route.track.lengthMeters()
                s.copy(
                    stats =
                        if (s.route.points.size < 2) {
                            null
                        } else {
                            RouteStats(distance, s.stats?.ascentMeters, s.stats?.descentMeters, s.stats?.durationSeconds)
                        },
                )
            }
        }

        /** Elevation profile and climbing, once routing settles (M5.8, M6). */
        private fun scheduleProfile() {
            profileJob?.cancel()
            val state = _uiState.value
            if (state.route.points.size < 2) {
                _uiState.update { it.copy(profile = null, stats = null, highlightMeters = null) }
                return
            }
            profileJob =
                viewModelScope.launch {
                    delay(PROFILE_DEBOUNCE_MS)
                    val track = _uiState.value.route.track
                    val profile = elevation.profile(track)
                    _uiState.update { s ->
                        val distance = track.lengthMeters()
                        val ascent = profile?.ascentMeters
                        val descent = profile?.descentMeters
                        s.copy(
                            profile = profile,
                            stats =
                                RouteStats(
                                    distanceMeters = distance,
                                    ascentMeters = ascent,
                                    descentMeters = descent,
                                    durationSeconds = DurationEstimate.seconds(s.sport, distance, ascent ?: 0.0, descent ?: 0.0),
                                ),
                        )
                    }
                }
        }

        private fun scheduleDraft() {
            draftJob?.cancel()
            draftJob =
                viewModelScope.launch {
                    delay(DRAFT_DEBOUNCE_MS)
                    val s = _uiState.value
                    drafts.save(PlannerDraft(s.route, s.sport, s.routing, s.routeId, s.routeName))
                }
        }

        private fun save(
            name: String,
            sport: Sport,
        ) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return
            val sportChanged = sport != _uiState.value.sport
            _uiState.update { it.copy(isSaveDialogOpen = false, routeName = trimmed) }
            if (sportChanged) changeRouting { s -> s.copy(sport = sport, availableOptions = routingOptions.optionsFor(sport)) }
            viewModelScope.launch {
                val id = routes.save(currentRoute(_uiState.value))
                _uiState.update { it.copy(routeId = id) }
                scheduleDraft()
                _effects.send(PlannerEffect.Saved(trimmed))
            }
        }

        private suspend fun open(id: Long) {
            val saved = routes.get(id) ?: return
            cancelAllRouting()
            _uiState.update {
                it.copy(
                    route =
                        PlannedRoute(
                            saved.points,
                            saved.segments.takeIf { s ->
                                s.size == saved.points.size - 1
                            } ?: List(maxOf(saved.points.size - 1, 0)) { null },
                        ),
                    sport = saved.sport,
                    routing = saved.routing,
                    routeId = saved.id,
                    routeName = saved.name,
                    availableOptions = routingOptions.optionsFor(saved.sport),
                    selectedPoint = null,
                    drag = null,
                    profileExpanded = false,
                    highlightMeters = null,
                )
            }
            onRouteChanged()
        }

        private fun currentRoute(s: PlannerUiState): Route =
            Route(
                id = s.routeId ?: 0,
                name = s.routeName.orEmpty(),
                sport = s.sport,
                routing = s.routing,
                points = s.route.points,
                segments = s.route.completedSegments(),
                distanceMeters = s.stats?.distanceMeters ?: s.route.track.lengthMeters(),
                ascentMeters = s.stats?.ascentMeters ?: 0.0,
                descentMeters = s.stats?.descentMeters ?: 0.0,
            )

        internal companion object {
            const val PROFILE_DEBOUNCE_MS = 400L
            const val DRAFT_DEBOUNCE_MS = 1_000L

            /** Below this the routed line already starts on the control point. */
            const val ATTACH_THRESHOLD_M = 1.0

            /**
             * The router snaps to the nearest path: the segment must still start and end exactly on
             * its control points, joined to the path by a short straight piece when needed.
             */
            fun attach(
                from: GeoPoint,
                track: List<GeoPoint>,
                to: GeoPoint,
            ): List<GeoPoint> {
                val start = if (track.first().distanceTo(from) > ATTACH_THRESHOLD_M) listOf(from) + track else listOf(from) + track.drop(1)
                return if (start.last().distanceTo(to) > ATTACH_THRESHOLD_M) start + to else start.dropLast(1) + to
            }
        }
    }
