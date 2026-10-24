package it.traccia.core.ui.permission

import android.Manifest
import android.os.Build

/** Runtime permissions the app may ask for, each tied to a user-visible reason. */
enum class AppPermission(
    val manifestPermissions: List<String>,
    private val minSdk: Int = Build.VERSION_CODES.BASE,
) {
    /** "Centra su di me" and user position on the map. */
    LOCATION(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
    ),

    /** Progress notifications for offline map downloads. Runtime permission only from Android 13. */
    NOTIFICATIONS(
        listOf(Manifest.permission.POST_NOTIFICATIONS),
        minSdk = Build.VERSION_CODES.TIRAMISU,
    ),
    ;

    fun isRuntimePermission(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= minSdk
}
