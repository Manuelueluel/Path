package it.traccia.core.ui.permission

import android.os.Build
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PermissionStatusTest {
    @Test
    fun `granted wins over everything`() {
        assertEquals(PermissionStatus.GRANTED, resolvePermissionStatus(isGranted = true, shouldShowRationale = true, wasRequested = true))
    }

    @Test
    fun `not granted and never requested`() {
        assertEquals(
            PermissionStatus.NOT_REQUESTED,
            resolvePermissionStatus(isGranted = false, shouldShowRationale = false, wasRequested = false),
        )
    }

    @Test
    fun `rationale means it can still be asked`() {
        assertEquals(PermissionStatus.DENIED, resolvePermissionStatus(isGranted = false, shouldShowRationale = true, wasRequested = true))
    }

    @Test
    fun `requested without rationale is permanently denied`() {
        assertEquals(
            PermissionStatus.PERMANENTLY_DENIED,
            resolvePermissionStatus(isGranted = false, shouldShowRationale = false, wasRequested = true),
        )
    }

    @Test
    fun `notifications need a runtime grant only from Android 13`() {
        assertFalse(AppPermission.NOTIFICATIONS.isRuntimePermission(Build.VERSION_CODES.S_V2))
        assertTrue(AppPermission.NOTIFICATIONS.isRuntimePermission(Build.VERSION_CODES.TIRAMISU))
    }
}
