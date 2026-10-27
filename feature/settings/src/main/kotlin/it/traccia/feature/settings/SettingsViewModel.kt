package it.traccia.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.traccia.core.common.SpeedReference
import it.traccia.core.common.Sport
import it.traccia.core.storage.prefs.UserPreferences
import it.traccia.core.storage.prefs.UserPreferencesRepository
import it.traccia.core.ui.permission.AppPermission
import it.traccia.core.ui.permission.PermissionChecker
import it.traccia.core.ui.permission.PermissionStatus
import it.traccia.core.ui.permission.resolvePermissionStatus
import it.traccia.feature.elevation.DurationEstimate
import it.traccia.feature.heatmap.config.HeatmapConfig
import it.traccia.feature.map.prefs.MapPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val permissionChecker: PermissionChecker,
        private val preferences: UserPreferencesRepository,
        private val mapPreferences: MapPreferencesRepository,
        private val heatmapConfig: HeatmapConfig,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                SettingsUiState(
                    permissions = AppPermission.entries.map(::initialItem),
                    heatmapRefreshChoices = (REFRESH_CHOICES + heatmapConfig.refreshAfterDays).distinct().sorted(),
                ),
            )
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                preferences.preferences.collect { p ->
                    _uiState.update { s ->
                        s.copy(
                            units = p.units,
                            defaultSport = p.defaultSport,
                            heatmapRefreshDays = p.heatmapRefreshDays ?: heatmapConfig.refreshAfterDays,
                            speeds =
                                Sport.entries.map { sport ->
                                    val reference = p.speedFor(sport)
                                    val usesDescent = DurationEstimate.usesDescent(sport)
                                    SportSpeed(
                                        sport = sport,
                                        reference = reference,
                                        usesDescent = usesDescent,
                                        isCustom = sport in p.speeds,
                                        display = SpeedInput.from(reference, p.units, usesDescent),
                                    )
                                },
                        )
                    }
                }
            }
            viewModelScope.launch {
                mapPreferences.preferences.collect { p -> _uiState.update { it.copy(mapStyle = p.style) } }
            }
        }

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
                is SettingsEvent.UnitsSelected -> savePreferences { it.copy(units = event.units) }
                is SettingsEvent.DefaultSportSelected -> savePreferences { it.copy(defaultSport = event.sport) }
                is SettingsEvent.MapStyleSelected -> viewModelScope.launch { mapPreferences.setStyle(event.style) }
                is SettingsEvent.HeatmapRefreshSelected ->
                    savePreferences {
                        // The configured default is not stored: a future heatmap.json change still applies.
                        it.copy(heatmapRefreshDays = event.days.takeIf { d -> d != heatmapConfig.refreshAfterDays })
                    }
                else -> onSpeedEvent(event)
            }
        }

        /** Reference speeds dialog (M8.2). */
        private fun onSpeedEvent(event: SettingsEvent) {
            when (event) {
                is SettingsEvent.SpeedEditOpened -> {
                    val s = _uiState.value
                    val speed = s.speeds.firstOrNull { it.sport == event.sport } ?: return
                    _uiState.update {
                        it.copy(speedEditor = SpeedEditor(event.sport, SpeedInput.from(speed.reference, s.units, speed.usesDescent)))
                    }
                }
                is SettingsEvent.SpeedFieldChanged ->
                    _uiState.update { s ->
                        val editor = s.speedEditor ?: return@update s
                        val input =
                            when (event.field) {
                                SpeedField.FLAT -> editor.input.copy(flat = event.text)
                                SpeedField.ASCENT -> editor.input.copy(ascent = event.text)
                                SpeedField.DESCENT -> editor.input.copy(descent = event.text)
                            }
                        s.copy(speedEditor = editor.copy(input = input, isInvalid = false))
                    }
                SettingsEvent.SpeedEditConfirmed -> confirmSpeed()
                SettingsEvent.SpeedEditReset -> {
                    val sport = _uiState.value.speedEditor?.sport ?: return
                    _uiState.update { it.copy(speedEditor = null) }
                    savePreferences { it.copy(speeds = it.speeds - sport) }
                }
                SettingsEvent.SpeedEditDismissed -> _uiState.update { it.copy(speedEditor = null) }
                else -> Unit
            }
        }

        private fun confirmSpeed() {
            val s = _uiState.value
            val editor = s.speedEditor ?: return
            val default = SpeedReference.defaultFor(editor.sport)
            val reference = editor.input.toReference(s.units, default)
            if (reference == null) {
                _uiState.update { it.copy(speedEditor = editor.copy(isInvalid = true)) }
                return
            }
            _uiState.update { it.copy(speedEditor = null) }
            val unchanged = SpeedInput.from(default, s.units, DurationEstimate.usesDescent(editor.sport)) == editor.input
            savePreferences { p -> p.copy(speeds = if (unchanged) p.speeds - editor.sport else p.speeds + (editor.sport to reference)) }
        }

        private fun savePreferences(transform: (UserPreferences) -> UserPreferences) {
            viewModelScope.launch { preferences.update(transform) }
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

        private companion object {
            val REFRESH_CHOICES = listOf(7, 14, 30, 60, 90)
        }
    }
