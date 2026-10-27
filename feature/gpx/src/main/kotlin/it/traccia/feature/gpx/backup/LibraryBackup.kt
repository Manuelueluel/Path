package it.traccia.feature.gpx.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import it.traccia.core.common.Sport
import it.traccia.core.geo.lengthMeters
import it.traccia.core.storage.route.Route
import it.traccia.core.storage.route.RouteRepository
import it.traccia.feature.elevation.RouteElevation
import it.traccia.feature.gpx.ExportOptions
import it.traccia.feature.gpx.GpxContent
import it.traccia.feature.gpx.GpxConversion
import it.traccia.feature.gpx.GpxExporter
import it.traccia.feature.gpx.GpxFormatException
import it.traccia.feature.gpx.GpxParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import kotlin.math.abs

sealed interface BackupResult {
    data class Exported(
        val count: Int,
    ) : BackupResult

    data class Restored(
        val count: Int,
        /** Already in the library: not added twice. */
        val skipped: Int,
    ) : BackupResult

    data object NotABackup : BackupResult

    data object Failed : BackupResult
}

/** Export and restore of the whole route library as a GPX archive (M8.6). */
class LibraryBackup
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val routes: RouteRepository,
        private val exporter: GpxExporter,
        private val elevation: RouteElevation,
    ) {
        suspend fun export(uri: Uri): BackupResult =
            guarded {
                val all = routes.summaries.first().mapNotNull { routes.get(it.id) }
                // Track and control points (rte): the GPX alone is enough to rebuild the route elsewhere.
                val entries =
                    all.map { route ->
                        currentCoroutineContext().ensureActive()
                        BackupEntry(route, exporter.gpx(route, ExportOptions(content = GpxContent.BOTH)))
                    }
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot write $uri")
                    out.use { BackupArchive.write(entries, it) }
                }
                BackupResult.Exported(entries.size)
            }

        suspend fun restore(uri: Uri): BackupResult =
            guarded {
                val contents =
                    withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
                        input.use(BackupArchive::read)
                    }
                val existing = routes.summaries.first()
                val loose =
                    contents.looseGpx.mapNotNull { (file, bytes) ->
                        val line =
                            try {
                                GpxParser.parse(bytes.inputStream()).let { doc -> doc.mainLine?.let { doc.name to it } }
                            } catch (e: GpxFormatException) {
                                Log.i(TAG, "Skipping $file: not a GPX", e)
                                null
                            }
                        line?.takeIf { it.second.size >= 2 }?.let { (name, points) ->
                            GpxConversion.toRoute(points, name ?: file.removeSuffix(".gpx"), Sport.HIKING)
                        }
                    }
                var restored = 0
                var skipped = 0
                (contents.routes + loose).forEach { route ->
                    currentCoroutineContext().ensureActive()
                    val distance = route.distanceMeters.takeIf { it > 0 } ?: route.track.lengthMeters()
                    if (existing.any { it.name == route.name && abs(it.distanceMeters - distance) < SAME_ROUTE_TOLERANCE_M }) {
                        skipped++
                    } else {
                        routes.save(withStats(route, distance))
                        restored++
                    }
                }
                BackupResult.Restored(restored, skipped)
            }

        private suspend fun withStats(
            route: Route,
            distance: Double,
        ): Route {
            if (route.ascentMeters > 0 || route.descentMeters > 0) return route.copy(id = 0, distanceMeters = distance)
            val profile = elevation.profile(route.track)
            return route.copy(
                id = 0,
                distanceMeters = distance,
                ascentMeters = profile?.ascentMeters ?: 0.0,
                descentMeters = profile?.descentMeters ?: 0.0,
            )
        }

        private suspend fun guarded(block: suspend () -> BackupResult): BackupResult =
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupFormatException) {
                Log.i(TAG, "Not a library backup", e)
                BackupResult.NotABackup
            } catch (e: java.util.zip.ZipException) {
                Log.i(TAG, "Not a ZIP archive", e)
                BackupResult.NotABackup
            } catch (e: IOException) {
                Log.w(TAG, "Backup I/O failed", e)
                BackupResult.Failed
            } catch (e: SecurityException) {
                Log.w(TAG, "No access to the backup file", e)
                BackupResult.Failed
            }

        private companion object {
            const val TAG = "LibraryBackup"
            const val SAME_ROUTE_TOLERANCE_M = 1.0
        }
    }
