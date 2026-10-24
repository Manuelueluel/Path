package it.traccia.core.ui.permission

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat

/** Outcome of a system permission dialog, forwarded as-is to the ViewModel. */
data class PermissionResult(
    val permission: AppPermission,
    val isGranted: Boolean,
    val shouldShowRationale: Boolean,
)

/**
 * Platform glue: returns a function that opens the system permission dialog
 * and reports the raw outcome through [onResult]. No decisions are taken here.
 */
@Composable
fun rememberPermissionLauncher(onResult: (PermissionResult) -> Unit): (AppPermission) -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    // Saveable so the pending request survives a configuration change while the dialog is open.
    var pending by rememberSaveable { mutableStateOf<AppPermission?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val permission = pending ?: return@rememberLauncherForActivityResult
            pending = null
            val activity = context.findActivity()
            currentOnResult.value(
                PermissionResult(
                    permission = permission,
                    isGranted = grants.values.any { it },
                    shouldShowRationale =
                        activity != null &&
                            permission.manifestPermissions.any {
                                ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
                            },
                ),
            )
        }
    return { permission ->
        pending = permission
        launcher.launch(permission.manifestPermissions.toTypedArray())
    }
}

/** Opens the app page in system settings, the only way out of a permanent denial. */
fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
