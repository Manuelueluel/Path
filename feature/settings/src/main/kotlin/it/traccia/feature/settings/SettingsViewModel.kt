package it.traccia.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import it.traccia.core.ui.permission.AppPermission
import it.traccia.core.ui.permission.PermissionChecker
import it.traccia.core.ui.permission.PermissionStatus
import it.traccia.core.ui.permission.resolvePermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val permissionChecker: PermissionChecker,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsUiState(permissions = AppPermission.entries.map(::initialItem)))
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        fun onEvent(event: SettingsEvent) {
            when (event) {
                SettingsEvent.Refresh -> refresh()
                is SettingsEvent.PermissionResultReceived -> {
                    val result = event.result
                    updateStatus(
                        result.permission,
                        resolvePermissionStatus(
                            isGranted = result.isGranted,
                            shouldShowRationale = result.shouldShowRationale,
                            wasRequested = true,
                        ),
                    )
                }
            }
        }

        private fun refresh() {
            _uiState.update { state ->
                state.copy(
                    permissions =
                        state.permissions.map { item ->
                            val status =
                                when {
                                    permissionChecker.isGranted(item.permission) -> PermissionStatus.GRANTED
                                    // Revoked from system settings: it can be asked again.
                                    item.status == PermissionStatus.GRANTED -> PermissionStatus.NOT_REQUESTED
                                    else -> item.status
                                }
                            item.copy(status = status)
                        },
                )
            }
        }

        private fun updateStatus(
            permission: AppPermission,
            status: PermissionStatus,
        ) {
            _uiState.update { state ->
                state.copy(permissions = state.permissions.map { if (it.permission == permission) it.copy(status = status) else it })
            }
        }

        private fun initialItem(permission: AppPermission) =
            PermissionItem(
                permission = permission,
                status = if (permissionChecker.isGranted(permission)) PermissionStatus.GRANTED else PermissionStatus.NOT_REQUESTED,
            )
    }
