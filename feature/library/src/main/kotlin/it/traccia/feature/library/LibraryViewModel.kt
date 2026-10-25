package it.traccia.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.traccia.core.common.Sport
import it.traccia.core.storage.route.Route
import it.traccia.core.storage.route.RouteRepository
import it.traccia.core.storage.route.RouteSummary
import it.traccia.feature.gpx.ImportError
import it.traccia.feature.gpx.ImportResult
import it.traccia.feature.planner.PlannerCommands
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val routes: List<RouteSummary> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val sport: Sport? = null,
    val sort: LibrarySort = LibrarySort.RECENT,
    /** Routes ticked to be shown together on the map (M7.9). */
    val selection: Set<Long> = emptySet(),
    val shownOnMap: Set<Long> = emptySet(),
    val renaming: RouteSummary? = null,
    val deleting: RouteSummary? = null,
    val exporting: Route? = null,
    val isImporting: Boolean = false,
) {
    val isSelecting: Boolean get() = selection.isNotEmpty()
}

sealed interface LibraryEvent {
    data class QueryChanged(
        val query: String,
    ) : LibraryEvent

    data class SportFilter(
        val sport: Sport?,
    ) : LibraryEvent

    data class SortChanged(
        val sort: LibrarySort,
    ) : LibraryEvent

    data class Open(
        val id: Long,
    ) : LibraryEvent

    data class ToggleSelection(
        val id: Long,
    ) : LibraryEvent

    data object ClearSelection : LibraryEvent

    data object ShowSelectionOnMap : LibraryEvent

    data object HideShownRoutes : LibraryEvent

    data class Duplicate(
        val route: RouteSummary,
        val copyName: String,
    ) : LibraryEvent

    data class Rename(
        val route: RouteSummary,
    ) : LibraryEvent

    data class RenameConfirmed(
        val name: String,
    ) : LibraryEvent

    data class Delete(
        val route: RouteSummary,
    ) : LibraryEvent

    data object DeleteConfirmed : LibraryEvent

    data class Export(
        val id: Long,
    ) : LibraryEvent

    data object DialogDismissed : LibraryEvent

    data class Import(
        val uri: Uri,
    ) : LibraryEvent
}

sealed interface LibraryEffect {
    data object BackToMap : LibraryEffect

    data class Imported(
        val name: String,
    ) : LibraryEffect

    data class ImportFailed(
        val reason: ImportError,
    ) : LibraryEffect
}

/** Saved routes (M7.6 - M7.9). */
@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val routes: RouteRepository,
        private val planner: PlannerCommands,
        private val importer: RouteImporter,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(LibraryUiState())
        val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
        private val _effects = Channel<LibraryEffect>(Channel.BUFFERED)
        val effects: Flow<LibraryEffect> = _effects.receiveAsFlow()
        private var all: List<RouteSummary> = emptyList()

        init {
            viewModelScope.launch {
                combine(routes.summaries, routes.shownOnMap) { list, shown -> list to shown }.collect { (list, shown) ->
                    all = list
                    _uiState.update { it.copy(shownOnMap = shown) }
                    refilter()
                }
            }
        }

        @Suppress("CyclomaticComplexMethod")
        fun onEvent(event: LibraryEvent) {
            when (event) {
                is LibraryEvent.QueryChanged -> update { copy(query = event.query) }
                is LibraryEvent.SportFilter -> update { copy(sport = event.sport) }
                is LibraryEvent.SortChanged -> update { copy(sort = event.sort) }
                is LibraryEvent.Open -> {
                    planner.open(event.id)
                    _effects.trySend(LibraryEffect.BackToMap)
                }
                is LibraryEvent.ToggleSelection ->
                    _uiState.update { s ->
                        s.copy(
                            selection =
                                if (event.id in
                                    s.selection
                                ) {
                                    s.selection - event.id
                                } else {
                                    s.selection + event.id
                                },
                        )
                    }
                LibraryEvent.ClearSelection -> _uiState.update { it.copy(selection = emptySet()) }
                LibraryEvent.ShowSelectionOnMap -> {
                    routes.setShownOnMap(_uiState.value.selection)
                    _uiState.update { it.copy(selection = emptySet()) }
                    _effects.trySend(LibraryEffect.BackToMap)
                }
                LibraryEvent.HideShownRoutes -> routes.setShownOnMap(emptySet())
                is LibraryEvent.Duplicate -> viewModelScope.launch { routes.duplicate(event.route.id, event.copyName) }
                is LibraryEvent.Rename -> _uiState.update { it.copy(renaming = event.route) }
                is LibraryEvent.RenameConfirmed -> rename(event.name)
                is LibraryEvent.Delete -> _uiState.update { it.copy(deleting = event.route) }
                LibraryEvent.DeleteConfirmed -> delete()
                is LibraryEvent.Export ->
                    viewModelScope.launch {
                        routes.get(event.id)?.let { r ->
                            _uiState.update { it.copy(exporting = r) }
                        }
                    }
                LibraryEvent.DialogDismissed -> _uiState.update { it.copy(renaming = null, deleting = null, exporting = null) }
                is LibraryEvent.Import -> import(event.uri)
            }
        }

        private fun rename(name: String) {
            val target = _uiState.value.renaming ?: return
            _uiState.update { it.copy(renaming = null) }
            if (name.isNotBlank()) viewModelScope.launch { routes.rename(target.id, name.trim()) }
        }

        private fun delete() {
            val target = _uiState.value.deleting ?: return
            _uiState.update { it.copy(deleting = null, selection = it.selection - target.id) }
            viewModelScope.launch { routes.delete(target.id) }
        }

        private fun import(uri: Uri) {
            _uiState.update { it.copy(isImporting = true) }
            viewModelScope.launch {
                when (val result = importer.import(uri)) {
                    is ImportResult.Imported -> {
                        _effects.send(LibraryEffect.Imported(result.name))
                        planner.open(result.routeId)
                        _effects.send(LibraryEffect.BackToMap)
                    }
                    is ImportResult.Failed -> _effects.send(LibraryEffect.ImportFailed(result.reason))
                }
                _uiState.update { it.copy(isImporting = false) }
            }
        }

        private fun update(transform: LibraryUiState.() -> LibraryUiState) {
            _uiState.update(transform)
            refilter()
        }

        private fun refilter() {
            _uiState.update { s -> s.copy(routes = filterRoutes(all, s.query, s.sport, s.sort), totalCount = all.size) }
        }
    }

/** GPX import as seen by the library (the real one is [it.traccia.feature.gpx.GpxImporter]). */
fun interface RouteImporter {
    suspend fun import(uri: Uri): ImportResult
}
