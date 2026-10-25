package it.traccia.feature.offlinemaps.download

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import it.traccia.feature.offlinemaps.DownloadError
import it.traccia.feature.offlinemaps.catalog.CatalogFile
import it.traccia.feature.offlinemaps.catalog.CatalogRegion
import it.traccia.feature.offlinemaps.installed.InstalledRegions
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException

/**
 * Downloads all files of a region pack into its staging directory, verifies the checksums,
 * then moves them into place and registers the region. Stopping the work keeps partial files,
 * so enqueuing it again resumes the download.
 */
@HiltWorker
class RegionDownloadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        client: OkHttpClient,
        private val installed: InstalledRegions,
    ) : CoroutineWorker(context, params) {
        // Large files must not go into the shared HTTP cache.
        private val downloader = FileDownloader(client.newBuilder().cache(null).build())
        private val notifications = DownloadNotifications(context)

        override suspend fun doWork(): Result {
            val region = Json.decodeFromString(CatalogRegion.serializer(), requireNotNull(inputData.getString(KEY_REGION)))
            val regionDir = File(requireNotNull(inputData.getString(KEY_REGION_DIR)))
            val staging = File(regionDir, STAGING)
            val total = region.totalBytes
            var lastReport = 0L

            suspend fun report(bytes: Long) {
                val now = System.currentTimeMillis()
                if (now - lastReport < PROGRESS_INTERVAL_MS && bytes < total) return
                lastReport = now
                setProgress(workDataOf(KEY_BYTES to bytes, KEY_TOTAL to total))
                tryForeground(region, bytes, total)
            }
            tryForeground(region, 0, total)
            return try {
                if (downloadAll(region, regionDir, staging, ::report)) {
                    installFrom(region, regionDir, staging)
                    Result.success()
                } else {
                    failure(DownloadError.CHECKSUM)
                }
            } catch (e: PermanentDownloadException) {
                Log.w(TAG, "Region ${region.id} not downloadable", e)
                failure(DownloadError.UNAVAILABLE)
            } catch (e: IOException) {
                Log.w(TAG, "Download of ${region.id} interrupted (attempt $runAttemptCount)", e)
                if (regionDir.usableSpace < MIN_FREE_BYTES) {
                    failure(DownloadError.STORAGE)
                } else if (runAttemptCount < MAX_ATTEMPTS) {
                    Result.retry()
                } else {
                    failure(DownloadError.NETWORK)
                }
            }
        }

        /**
         * Downloads every missing file into [staging] and verifies it.
         * @return false when a file does not match its checksum (it is deleted).
         */
        private suspend fun downloadAll(
            region: CatalogRegion,
            regionDir: File,
            staging: File,
            report: suspend (Long) -> Unit,
        ): Boolean {
            var completed = 0L
            val segments = File(regionDir.parentFile, SEGMENTS_DIR)
            for (file in region.files) {
                val target = File(staging, file.fileName)
                if (!isInstalledShared(file, segments) && !target.exists()) {
                    val part = File(staging, file.fileName + PART)
                    downloader.download(file.url, part, file.size) { written -> report(completed + written) }
                    if (part.sha256() != file.sha256) {
                        part.delete()
                        return false
                    }
                    if (!part.renameTo(target)) throw IOException("Cannot finalize ${target.name}")
                }
                completed += file.size
            }
            return true
        }

        /** A shared segment already installed by another region is reused if identical. */
        private fun isInstalledShared(
            file: CatalogFile,
            segments: File,
        ): Boolean {
            if (!file.isShared) return false
            val installed = File(segments, file.localName)
            return installed.length() == file.size && installed.sha256() == file.sha256
        }

        private suspend fun installFrom(
            region: CatalogRegion,
            regionDir: File,
            staging: File,
        ) {
            val versionDir = File(regionDir, region.version).apply { mkdirs() }
            val segments = File(regionDir.parentFile, SEGMENTS_DIR).apply { mkdirs() }
            val files =
                region.files.associate { f ->
                    val target = if (f.isShared) File(segments, f.localName) else File(versionDir, f.fileName)
                    val downloaded = File(staging, f.fileName)
                    // Shared segment reused as it is, or replaced by the freshly downloaded copy.
                    if (downloaded.exists() && !downloaded.renameTo(target)) throw IOException("Cannot move ${f.fileName}")
                    f.fileKey() to target
                }
            installed.install(region, files)
            staging.deleteRecursively()
        }

        private suspend fun tryForeground(
            region: CatalogRegion,
            bytes: Long,
            total: Long,
        ) {
            try {
                setForeground(notifications.foregroundInfo(id, region.id, region.name, bytes, total))
            } catch (e: IllegalStateException) {
                // Not allowed from the background (Android 12+): keep downloading without the notification.
                Log.i(TAG, "Foreground not available", e)
            }
        }

        private fun failure(error: DownloadError) = Result.failure(workDataOf(KEY_ERROR to error.name))

        companion object {
            const val KEY_REGION = "region"
            const val KEY_REGION_DIR = "region_dir"
            const val KEY_BYTES = "bytes"
            const val KEY_TOTAL = "total"
            const val KEY_ERROR = "error"
            const val STAGING = "download"
            const val SEGMENTS_DIR = "segments"
            const val PART = ".part"
            private const val TAG = "RegionDownloadWorker"
            private const val PROGRESS_INTERVAL_MS = 500L
            private const val MAX_ATTEMPTS = 5
            private const val MIN_FREE_BYTES = 16L * 1024 * 1024
        }
    }
