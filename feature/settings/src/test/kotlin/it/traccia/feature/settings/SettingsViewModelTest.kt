package it.traccia.feature.settings

import it.traccia.core.ui.permission.AppPermission
import it.traccia.core.ui.permission.PermissionChecker
import it.traccia.core.ui.permission.PermissionResult
import it.traccia.core.ui.permission.PermissionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsViewModelTest {
    private val checker = FakePermissionChecker()

    @Test
    fun `initial state reflects granted permissions`() {
        checker.granted += AppPermission.NOTIFICATIONS
        val viewModel = SettingsViewModel(checker)
        assertEquals(PermissionStatus.NOT_REQUESTED, viewModel.statusOf(AppPermission.LOCATION))
        assertEquals(PermissionStatus.GRANTED, viewModel.statusOf(AppPermission.NOTIFICATIONS))
    }

    @Test
    fun `denial with rationale can be asked again`() {
        val viewModel = SettingsViewModel(checker)
        viewModel.onEvent(result(AppPermission.LOCATION, granted = false, rationale = true))
        assertEquals(PermissionStatus.DENIED, viewModel.statusOf(AppPermission.LOCATION))
    }

    @Test
    fun `denial without rationale is permanent`() {
        val viewModel = SettingsViewModel(checker)
        viewModel.onEvent(result(AppPermission.LOCATION, granted = false, rationale = false))
        assertEquals(PermissionStatus.PERMANENTLY_DENIED, viewModel.statusOf(AppPermission.LOCATION))
    }

    @Test
    fun `refresh picks up a grant made from system settings`() {
        val viewModel = SettingsViewModel(checker)
        viewModel.onEvent(result(AppPermission.LOCATION, granted = false, rationale = false))
        checker.granted += AppPermission.LOCATION
        viewModel.onEvent(SettingsEvent.Refresh)
        assertEquals(PermissionStatus.GRANTED, viewModel.statusOf(AppPermission.LOCATION))
    }

    @Test
    fun `refresh keeps permanent denial while still not granted`() {
        val viewModel = SettingsViewModel(checker)
        viewModel.onEvent(result(AppPermission.LOCATION, granted = false, rationale = false))
        viewModel.onEvent(SettingsEvent.Refresh)
        assertEquals(PermissionStatus.PERMANENTLY_DENIED, viewModel.statusOf(AppPermission.LOCATION))
    }

    @Test
    fun `refresh after revocation allows asking again`() {
        checker.granted += AppPermission.LOCATION
        val viewModel = SettingsViewModel(checker)
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

    private class FakePermissionChecker : PermissionChecker {
        val granted = mutableSetOf<AppPermission>()

        override fun isGranted(permission: AppPermission) = permission in granted
    }
}
