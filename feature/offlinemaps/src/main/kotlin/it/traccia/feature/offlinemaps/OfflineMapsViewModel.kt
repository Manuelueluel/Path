package it.traccia.feature.offlinemaps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.traccia.core.common.AppResult
import it.traccia.core.ui.permission.AppPermission
import it.traccia.core.ui.permission.PermissionChecker
import it.traccia.feature.offlinemaps.catalog.Catalog
import it.traccia.feature.offlinemaps.catalog.CatalogSource
import it.traccia.feature.offlinemaps.download.RegionDownloadController
import it.traccia.feature.offlinemaps.installed.InstalledRegion
import it.traccia.feature.offlinemaps.installed.InstalledRegions
import it.traccia.feature.offlinemaps.storage.StorageLocations
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

@HiltViewModel
class OfflineMapsViewModel
    @Inject
    constructor(
        private val catalog: CatalogSource,
        private val installed: InstalledRegions,
        private val downloads: RegionDownloadController,
        private val storage: StorageLocations,
        private val permissionChecker: PermissionChecker,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OfflineMapsUiState())
        val uiState: StateFlow<OfflineMapsUiState> = _uiState.asStateFlow()

        private val _effects = Channel<OfflineMapsEffect>(Channel.BUFFERED)
        val effects: Flow<OfflineMapsEffect> = _effects.receiveAsFlow()

        /** Bumped when files change outside the observed flows (pause, cancel, free space). */
        private val diskChanges = MutableStateFlow(0)
        private var notificationsAsked = false

        init {
            viewModelScope.launch {
                combine(catalog.catalog, installed.regions, downloads.states, storage.selectedId, diskChanges) {
                    remote,
                    local,
                    states,
                    selected,
                    _,
                    ->
                    Snapshot(remote, local, states, selected)
                }.collect { s ->
                    val items = resolveRegionItems(s.catalog, s.installed, s.downloads, downloads.partialBytes())
                    _uiState.update {
                        it.copy(
                            items = items,
                            locations = storage.available(),
                            selectedLocationId = s.selectedId,
                            // Shared routing segments are counted once.
                            usedBytes =
                                s.installed
                                    .flatMap { region ->
                                        region.files
                                    }.distinctBy { file -> file.path }
                                    .sumOf { file -> file.sizeBytes },
                        )
                    }
                }
            }
            refresh()
        }

        fun onEvent(event: OfflineMapsEvent) {
            when (event) {
                OfflineMapsEvent.Refresh -> refresh()
                OfflineMapsEvent.Resumed -> diskChanges.update { it + 1 }
                is OfflineMapsEvent.Download -> download(event.id)
                is OfflineMapsEvent.Pause -> {
                    downloads.pause(event.id)
                    diskChanges.update { it + 1 }
                }
                is OfflineMapsEvent.Cancel ->
                    viewModelScope.launch {
                        downloads.cancel(event.id)
                        diskChanges.update { it + 1 }
                    }
                is OfflineMapsEvent.Delete ->
                    _uiState.update { state -> state.copy(pendingDeletion = state.items.firstOrNull { it.id == event.id }) }
                OfflineMapsEvent.ConfirmDelete -> confirmDelete()
                OfflineMapsEvent.DismissDelete -> _uiState.update { it.copy(pendingDeletion = null) }
                is OfflineMapsEvent.SelectLocation -> viewModelScope.launch { storage.select(event.id) }
                // Downloads work without notifications too: nothing to do on denial.
                is OfflineMapsEvent.NotificationPermissionResult -> Unit
            }
        }

        private fun refresh() {
            _uiState.update { it.copy(isRefreshing = true) }
            viewModelScope.launch {
                val result = catalog.refresh()
                val failed = result is AppResult.Failure
                _uiState.update { it.copy(isRefreshing = false, catalogUnavailable = failed) }
                if (failed) _effects.send(OfflineMapsEffect.Show(OfflineMessage.CATALOG_UNAVAILABLE))
            }
        }

        private fun download(id: String) {
            val region =
                _uiState.value.items
                    .firstOrNull { it.id == id }
                    ?.catalogRegion ?: return
            viewModelScope.launch {
                val location = storage.stagingLocation(id) ?: storage.selected()
                val remaining = region.totalBytes - (downloads.partialBytes()[id] ?: 0L)
                if (location.freeBytes < remaining + FREE_SPACE_MARGIN_BYTES) {
                    _effects.send(OfflineMapsEffect.Show(OfflineMessage.STORAGE_FULL))
                    return@launch
                }
                if (!notificationsAsked && !permissionChecker.isGranted(AppPermission.NOTIFICATIONS)) {
                    notificationsAsked = true
                    _effects.send(OfflineMapsEffect.RequestNotificationPermission)
                }
                downloads.start(region, location)
            }
        }

        private fun confirmDelete() {
            val item = _uiState.value.pendingDeletion ?: return
            _uiState.update { it.copy(pendingDeletion = null) }
            viewModelScope.launch {
                installed.delete(item.id)
                diskChanges.update { it + 1 }
            }
        }

        private data class Snapshot(
            val catalog: Catalog?,
            val installed: List<InstalledRegion>,
            val downloads: Map<String, DownloadState>,
            val selectedId: String,
        )

        private companion object {
            /** Keep some room for the system and for the database. */
            const val FREE_SPACE_MARGIN_BYTES = 50L * 1024 * 1024
        }
    }
