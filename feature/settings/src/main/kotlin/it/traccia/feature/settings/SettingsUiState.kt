package it.traccia.feature.settings

import it.traccia.core.common.SpeedReference
import it.traccia.core.common.Sport
import it.traccia.core.common.UnitSystem
import it.traccia.core.ui.permission.AppPermission
import it.traccia.core.ui.permission.PermissionResult
import it.traccia.core.ui.permission.PermissionStatus
import it.traccia.feature.map.style.MapStyle

data class SettingsUiState(
    val permissions: List<PermissionItem> = emptyList(),
    val units: UnitSystem = UnitSystem.METRIC,
    val defaultSport: Sport = Sport.HIKING,
    val mapStyle: MapStyle = MapStyle.TOPO,
    val heatmapRefreshDays: Int = 30,
    val heatmapRefreshChoices: List<Int> = emptyList(),
    val speeds: List<SportSpeed> = emptyList(),
    val speedEditor: SpeedEditor? = null,
)

data class PermissionItem(
    val permission: AppPermission,
    val status: PermissionStatus,
)

data class SportSpeed(
    val sport: Sport,
    val reference: SpeedReference,
    val usesDescent: Boolean,
    val isCustom: Boolean,
    /** [reference] in the chosen units, for display. */
    val display: SpeedInput,
)

/** Dialog editing the reference speeds of one sport, outside the drawing. */
data class SpeedEditor(
    val sport: Sport,
    val input: SpeedInput,
    val isInvalid: Boolean = false,
)

enum class SpeedField { FLAT, ASCENT, DESCENT }

sealed interface SettingsEvent {
    /** The screen became visible: the user may have changed permissions from system settings. */
    data object Refresh : SettingsEvent

    data class PermissionResultReceived(
        val result: PermissionResult,
    ) : SettingsEvent

    data class UnitsSelected(
        val units: UnitSystem,
    ) : SettingsEvent

    data class DefaultSportSelected(
        val sport: Sport,
    ) : SettingsEvent

    data class MapStyleSelected(
        val style: MapStyle,
    ) : SettingsEvent

    data class HeatmapRefreshSelected(
        val days: Int,
    ) : SettingsEvent

    data class SpeedEditOpened(
        val sport: Sport,
    ) : SettingsEvent

    data class SpeedFieldChanged(
        val field: SpeedField,
        val text: String,
    ) : SettingsEvent

    data object SpeedEditConfirmed : SettingsEvent

    /** Back to the default speeds of the sport being edited. */
    data object SpeedEditReset : SettingsEvent

    data object SpeedEditDismissed : SettingsEvent
}
