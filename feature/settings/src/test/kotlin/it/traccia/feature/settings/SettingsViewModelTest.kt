package it.traccia.feature.settings

import it.traccia.core.common.Sport
import it.traccia.core.common.UnitSystem
import it.traccia.core.storage.prefs.InMemoryUserPreferences
import it.traccia.core.ui.permission.AppPermission
import it.traccia.core.ui.permission.PermissionChecker
import it.traccia.core.ui.permission.PermissionResult
import it.traccia.core.ui.permission.PermissionStatus
import it.traccia.feature.heatmap.config.HeatmapConfig
import it.traccia.feature.map.prefs.CameraSnapshot
import it.traccia.feature.map.prefs.MapPreferences
import it.traccia.feature.map.prefs.MapPreferencesRepository
import it.traccia.feature.map.style.MapStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SettingsViewModelTest {
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val checker = FakePermissionChecker()
    private val preferences = InMemoryUserPreferences()
    private val mapPreferences = FakeMapPreferences()

    private fun viewModel() = SettingsViewModel(checker, preferences, mapPreferences, heatmapConfig())

    @Test
    fun `units, default sport and map style are saved`() {
        val viewModel = viewModel()
        viewModel.onEvent(SettingsEvent.UnitsSelected(UnitSystem.IMPERIAL))
        viewModel.onEvent(SettingsEvent.DefaultSportSelected(Sport.GRAVEL))
        viewModel.onEvent(SettingsEvent.MapStyleSelected(MapStyle.MINIMAL))
        assertEquals(UnitSystem.IMPERIAL, preferences.preferences.value.units)
        assertEquals(Sport.GRAVEL, preferences.preferences.value.defaultSport)
        assertEquals(MapStyle.MINIMAL, viewModel.uiState.value.mapStyle)
    }

    @Test
    fun `heatmap refresh equal to the configured default is not stored`() {
        val viewModel = viewModel()
        viewModel.onEvent(SettingsEvent.HeatmapRefreshSelected(7))
        assertEquals(7, preferences.preferences.value.heatmapRefreshDays)
        assertEquals(7, viewModel.uiState.value.heatmapRefreshDays)
        viewModel.onEvent(SettingsEvent.HeatmapRefreshSelected(30))
        assertNull(preferences.preferences.value.heatmapRefreshDays)
        assertEquals(30, viewModel.uiState.value.heatmapRefreshDays)
    }

    @Test
    fun `edited speeds are saved, invalid ones keep the dialog open`() {
        val viewModel = viewModel()
        viewModel.onEvent(SettingsEvent.SpeedEditOpened(Sport.HIKING))
        viewModel.onEvent(SettingsEvent.SpeedFieldChanged(SpeedField.FLAT, "abc"))
        viewModel.onEvent(SettingsEvent.SpeedEditConfirmed)
        assertTrue(
            viewModel.uiState.value.speedEditor
                ?.isInvalid == true,
        )

        viewModel.onEvent(SettingsEvent.SpeedFieldChanged(SpeedField.FLAT, "4,5"))
        viewModel.onEvent(SettingsEvent.SpeedEditConfirmed)
        assertNull(viewModel.uiState.value.speedEditor)
        assertEquals(
            4.5,
            preferences.preferences.value
                .speedFor(Sport.HIKING)
                .flatKmh,
            1e-9,
        )
        assertTrue(
            viewModel.uiState.value.speeds
                .first { it.sport == Sport.HIKING }
                .isCustom,
        )

        viewModel.onEvent(SettingsEvent.SpeedEditOpened(Sport.HIKING))
        viewModel.onEvent(SettingsEvent.SpeedEditReset)
        assertTrue(
            preferences.preferences.value.speeds
                .isEmpty(),
        )
    }

    @Test
    fun `initial state reflects granted permissions`() {
        checker.granted += AppPermission.NOTIFICATIONS
        val viewModel = viewModel()
        assertEquals(PermissionStatus.NOT_REQUESTED, viewModel.statusOf(AppPermission.LOCATION))
        assertEquals(PermissionStatus.GRANTED, viewModel.statusOf(AppPermission.NOTIFICATIONS))
    }

    @Test
    fun `denial with rationale can be asked again`() {
        val viewModel = viewModel()
        viewModel.onEvent(result(AppPermission.LOCATION, granted = false, rationale = true))
        assertEquals(PermissionStatus.DENIED, viewModel.statusOf(AppPermission.LOCATION))
    }

    @Test
    fun `denial without rationale is permanent`() {
        val viewModel = viewModel()
        viewModel.onEvent(result(AppPermission.LOCATION, granted = false, rationale = false))
        assertEquals(PermissionStatus.PERMANENTLY_DENIED, viewModel.statusOf(AppPermission.LOCATION))
    }

    @Test
    fun `refresh picks up a grant made from system settings`() {
        val viewModel = viewModel()
        viewModel.onEvent(result(AppPermission.LOCATION, granted = false, rationale = false))
        checker.granted += AppPermission.LOCATION
        viewModel.onEvent(SettingsEvent.Refresh)
        assertEquals(PermissionStatus.GRANTED, viewModel.statusOf(AppPermission.LOCATION))
    }

    @Test
    fun `refresh keeps permanent denial while still not granted`() {
        val viewModel = viewModel()
        viewModel.onEvent(result(AppPermission.LOCATION, granted = false, rationale = false))
        viewModel.onEvent(SettingsEvent.Refresh)
        assertEquals(PermissionStatus.PERMANENTLY_DENIED, viewModel.statusOf(AppPermission.LOCATION))
    }

    @Test
    fun `refresh after revocation allows asking again`() {
        checker.granted += AppPermission.LOCATION
        val viewModel = viewModel()
        checker.granted -= AppPermission.LOCATION
        viewModel.onEvent(SettingsEvent.Refresh)
        assertEquals(PermissionStatus.NOT_REQUESTED, viewModel.statusOf(AppPermission.LOCATION))
    }

    private fun SettingsViewModel.statusOf(permission: AppPermission) =
        uiState.value.permissions
            .first { it.permission == permission }
            .status

    private fun result(
        permission: AppPermission,
        granted: Boolean,
        rationale: Boolean,
    ) = SettingsEvent.PermissionResultReceived(PermissionResult(permission, granted, rationale))

    private class FakeMapPreferences : MapPreferencesRepository {
        val state = MutableStateFlow(MapPreferences())
        override val preferences: Flow<MapPreferences> = state

        override suspend fun setStyle(style: MapStyle) = state.update { it.copy(style = style) }

        override suspend fun setContoursVisible(visible: Boolean) = state.update { it.copy(contoursVisible = visible) }

        override suspend fun setHillshadeVisible(visible: Boolean) = state.update { it.copy(hillshadeVisible = visible) }

        override suspend fun saveCamera(camera: CameraSnapshot) = state.update { it.copy(camera = camera) }
    }

    private fun heatmapConfig() =
        HeatmapConfig(
            tileUrl = "https://h/{sport}/{color}/{z}/{x}/{y}.png",
            tileSize = 256,
            minZoom = 3,
            maxZoom = 15,
            loginUrl = "",
            heatmapPageUrl = "",
            sessionCookieUrl = "",
            requiredCookies = emptyList(),
            policyCookie = "",
            refreshAfterDays = 30,
            sports = emptyList(),
            colors = emptyList(),
        )

    private class FakePermissionChecker : PermissionChecker {
        val granted = mutableSetOf<AppPermission>()

        override fun isGranted(permission: AppPermission) = permission in granted
    }
}
