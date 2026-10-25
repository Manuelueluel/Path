package it.traccia.feature.offlinemaps

import it.traccia.core.ui.permission.PermissionResult
import it.traccia.feature.offlinemaps.storage.StorageLocation

data class OfflineMapsUiState(
    val isRefreshing: Boolean = true,
    /** The last catalog refresh failed (the cached catalog, if any, is still shown). */
    val catalogUnavailable: Boolean = false,
    val items: List<RegionItem> = emptyList(),
    val locations: List<StorageLocation> = emptyList(),
    val selectedLocationId: String? = null,
    val usedBytes: Long = 0,
    /** Region waiting for the delete confirmation. */
    val pendingDeletion: RegionItem? = null,
) {
    val installed: List<RegionItem> get() = items.filter { it.isInstalled }
    val available: List<RegionItem> get() = items.filterNot { it.isInstalled }
}

sealed interface OfflineMapsEvent {
    data object Refresh : OfflineMapsEvent

    /** Back on screen: free space may have changed. */
    data object Resumed : OfflineMapsEvent

    /** Download, resume, retry or update: they all (re)start the download of the catalog version. */
    data class Download(
        val id: String,
    ) : OfflineMapsEvent

    data class Pause(
        val id: String,
    ) : OfflineMapsEvent

    data class Cancel(
        val id: String,
    ) : OfflineMapsEvent

    data class Delete(
        val id: String,
    ) : OfflineMapsEvent

    data object ConfirmDelete : OfflineMapsEvent

    data object DismissDelete : OfflineMapsEvent

    data class SelectLocation(
        val id: String,
    ) : OfflineMapsEvent

    data class NotificationPermissionResult(
        val result: PermissionResult,
    ) : OfflineMapsEvent
}

enum class OfflineMessage { CATALOG_UNAVAILABLE, STORAGE_FULL }

sealed interface OfflineMapsEffect {
    data class Show(
        val message: OfflineMessage,
    ) : OfflineMapsEffect

    data object RequestNotificationPermission : OfflineMapsEffect
}
