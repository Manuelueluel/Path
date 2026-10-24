package it.traccia.feature.settings

import it.traccia.core.ui.permission.AppPermission
import it.traccia.core.ui.permission.PermissionResult
import it.traccia.core.ui.permission.PermissionStatus

data class SettingsUiState(
    val permissions: List<PermissionItem> = emptyList(),
)

data class PermissionItem(
    val permission: AppPermission,
    val status: PermissionStatus,
)

sealed interface SettingsEvent {
    /** The screen became visible: the user may have changed permissions from system settings. */
    data object Refresh : SettingsEvent

    data class PermissionResultReceived(
        val result: PermissionResult,
    ) : SettingsEvent
}
