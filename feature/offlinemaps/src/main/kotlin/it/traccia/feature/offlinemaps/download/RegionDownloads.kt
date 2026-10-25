package it.traccia.feature.offlinemaps.download

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import it.traccia.feature.offlinemaps.DownloadError
import it.traccia.feature.offlinemaps.DownloadState
import it.traccia.feature.offlinemaps.catalog.CatalogRegion
import it.traccia.feature.offlinemaps.storage.StorageLocation
import it.traccia.feature.offlinemaps.storage.StorageLocations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Starts, pauses, resumes and cancels region downloads (M2.3). */
interface RegionDownloadController {
    /** Current download per region id; regions with no work are absent. */
    val states: Flow<Map<String, DownloadState>>

    /** Bytes already downloaded and kept on disk for each paused region. */
    suspend fun partialBytes(): Map<String, Long>

    /** Starts (or resumes) the download on [location], or on the volume with the partial files. */
    suspend fun start(
        region: CatalogRegion,
        location: StorageLocation,
    )

    /** Stops the transfer and keeps the partial files. */
    fun pause(regionId: String)

    /** Stops the transfer and deletes the partial files. */
    suspend fun cancel(regionId: String)
}

internal class WorkManagerRegionDownloads
    @Inject
    constructor(
        private val workManager: WorkManager,
        private val storage: StorageLocations,
    ) : RegionDownloadController {
        override val states: Flow<Map<String, DownloadState>> =
            workManager.getWorkInfosByTagFlow(TAG).map { infos ->
                infos
                    .mapNotNull { info -> regionIdOf(info)?.let { it to info } }
                    // A region can have old finished work: the most recent attempt wins.
                    .groupBy({ it.first }, { it.second })
                    .mapNotNull { (id, list) -> toState(list.last())?.let { id to it } }
                    .toMap()
            }

        override suspend fun partialBytes(): Map<String, Long> =
            withContext(Dispatchers.IO) {
                storage
                    .available()
                    .flatMap { location ->
                        File(location.root, "maps").listFiles().orEmpty().mapNotNull { regionDir ->
                            val bytes = File(regionDir, RegionDownloadWorker.STAGING).walk().filter { it.isFile }.sumOf { it.length() }
                            if (bytes > 0) regionDir.name to bytes else null
                        }
                    }.toMap()
            }

        override suspend fun start(
            region: CatalogRegion,
            location: StorageLocation,
        ) {
            val target = storage.stagingLocation(region.id) ?: location
            val request =
                OneTimeWorkRequestBuilder<RegionDownloadWorker>()
                    .setInputData(
                        workDataOf(
                            RegionDownloadWorker.KEY_REGION to Json.encodeToString(CatalogRegion.serializer(), region),
                            RegionDownloadWorker.KEY_REGION_DIR to target.regionDir(region.id).absolutePath,
                        ),
                    ).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(TAG)
                    .addTag(REGION_TAG_PREFIX + region.id)
                    .build()
            // Previous finished attempts are dropped so their state does not linger.
            workManager.pruneWork()
            workManager.enqueueUniqueWork(workName(region.id), ExistingWorkPolicy.KEEP, request)
        }

        override fun pause(regionId: String) {
            workManager.cancelUniqueWork(workName(regionId))
        }

        override suspend fun cancel(regionId: String) {
            workManager.cancelUniqueWork(workName(regionId))
            workManager.pruneWork()
            withContext(Dispatchers.IO) {
                storage.available().forEach { it.stagingDir(regionId).deleteRecursively() }
            }
        }

        private fun regionIdOf(info: WorkInfo): String? =
            info.tags.firstOrNull { it.startsWith(REGION_TAG_PREFIX) }?.removePrefix(REGION_TAG_PREFIX)

        private fun toState(info: WorkInfo): DownloadState? =
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadState.Queued
                WorkInfo.State.RUNNING ->
                    DownloadState.Running(
                        bytes = info.progress.getLong(RegionDownloadWorker.KEY_BYTES, 0),
                        totalBytes = info.progress.getLong(RegionDownloadWorker.KEY_TOTAL, 0),
                    )
                WorkInfo.State.FAILED ->
                    DownloadState.Failed(
                        DownloadError.entries.firstOrNull { it.name == info.outputData.getString(RegionDownloadWorker.KEY_ERROR) }
                            ?: DownloadError.NETWORK,
                    )
                // Succeeded: the region is installed. Cancelled: paused or cancelled, told apart by the partial files.
                WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> null
            }

        private fun workName(regionId: String) = "region-download-$regionId"

        private companion object {
            const val TAG = "region-download"
            const val REGION_TAG_PREFIX = "region:"
            const val BACKOFF_SECONDS = 30L
        }
    }
