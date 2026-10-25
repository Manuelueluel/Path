package it.traccia.feature.offlinemaps

import it.traccia.feature.offlinemaps.catalog.Catalog
import it.traccia.feature.offlinemaps.catalog.CatalogRegion
import it.traccia.feature.offlinemaps.installed.InstalledRegion

/** Why a download stopped for good. */
enum class DownloadError { NETWORK, CHECKSUM, UNAVAILABLE, STORAGE }

/** What WorkManager reports for a region download. */
sealed interface DownloadState {
    data object Queued : DownloadState

    data class Running(
        val bytes: Long,
        val totalBytes: Long,
    ) : DownloadState

    data class Failed(
        val error: DownloadError,
    ) : DownloadState
}

sealed interface RegionStatus {
    data object NotInstalled : RegionStatus

    data object Queued : RegionStatus

    data class Downloading(
        val bytes: Long,
        val totalBytes: Long,
    ) : RegionStatus

    data class Paused(
        val bytes: Long,
        val totalBytes: Long,
    ) : RegionStatus

    data class Failed(
        val error: DownloadError,
    ) : RegionStatus

    data class Installed(
        val updateAvailable: Boolean,
    ) : RegionStatus
}

data class RegionItem(
    val id: String,
    val name: String,
    /** Download size from the catalog, or the installed size for regions no longer in the catalog. */
    val sizeBytes: Long,
    val status: RegionStatus,
    val installedVersion: String?,
    val catalogVersion: String?,
    /** Null when the region was removed from the catalog: it can only be deleted. */
    val catalogRegion: CatalogRegion?,
) {
    val isInstalled: Boolean get() = installedVersion != null
}

/**
 * Merges catalog, installed packs, running downloads and partial files into one list.
 * Pure function: all the precedence rules live here and are unit-tested.
 */
fun resolveRegionItems(
    catalog: Catalog?,
    installed: List<InstalledRegion>,
    downloads: Map<String, DownloadState>,
    partialBytes: Map<String, Long>,
): List<RegionItem> {
    val installedById = installed.associateBy { it.id }
    val catalogById = catalog?.regions.orEmpty().associateBy { it.id }
    val ids = (catalogById.keys + installedById.keys)
    return ids
        .map { id ->
            val remote = catalogById[id]
            val local = installedById[id]
            val total = remote?.totalBytes ?: local?.totalBytes ?: 0L
            val status = statusOf(remote, local, downloads[id], partialBytes[id] ?: 0L, total)
            RegionItem(
                id = id,
                name = remote?.name ?: local?.name ?: id,
                sizeBytes = if (local != null && remote == null) local.totalBytes else total,
                status = status,
                installedVersion = local?.version,
                catalogVersion = remote?.version,
                catalogRegion = remote,
            )
        }.sortedBy { it.name }
}

/** Precedence: an active or failed download, then partial files (paused), then installed. */
private fun statusOf(
    remote: CatalogRegion?,
    local: InstalledRegion?,
    download: DownloadState?,
    partial: Long,
    total: Long,
): RegionStatus =
    when (download) {
        is DownloadState.Running -> RegionStatus.Downloading(download.bytes, download.totalBytes)
        DownloadState.Queued -> RegionStatus.Queued
        is DownloadState.Failed -> RegionStatus.Failed(download.error)
        null ->
            when {
                partial > 0 && remote != null -> RegionStatus.Paused(partial, total)
                local != null -> RegionStatus.Installed(updateAvailable = remote != null && remote.version > local.version)
                else -> RegionStatus.NotInstalled
            }
    }
