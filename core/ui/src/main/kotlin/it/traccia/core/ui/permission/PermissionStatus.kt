package it.traccia.core.ui.permission

enum class PermissionStatus {
    /** Granted, or not needed on this Android version. */
    GRANTED,

    /** Never asked in this session: show the explanation and the "allow" button. */
    NOT_REQUESTED,

    /** Denied once: the system dialog can still be shown again. */
    DENIED,

    /** Denied with "don't ask again": only the system settings can grant it. */
    PERMANENTLY_DENIED,
}

/**
 * Derives the status from what Android reports.
 * Android exposes no direct "permanently denied" flag: it is inferred when a request was made,
 * the permission is still missing and the system no longer wants to show a rationale.
 */
fun resolvePermissionStatus(
    isGranted: Boolean,
    shouldShowRationale: Boolean,
    wasRequested: Boolean,
): PermissionStatus =
    when {
        isGranted -> PermissionStatus.GRANTED
        shouldShowRationale -> PermissionStatus.DENIED
        wasRequested -> PermissionStatus.PERMANENTLY_DENIED
        else -> PermissionStatus.NOT_REQUESTED
    }
