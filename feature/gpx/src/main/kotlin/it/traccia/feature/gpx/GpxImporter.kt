package it.traccia.feature.gpx

import android.net.Uri
import android.util.Log
import it.traccia.core.common.Sport
import it.traccia.core.geo.lengthMeters
import it.traccia.core.storage.route.RouteRepository
import it.traccia.feature.elevation.RouteElevation
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject

/** Why an import failed, for the message shown to the user. */
enum class ImportError { NOT_GPX, EMPTY, UNREADABLE }

sealed interface ImportResult {
    data class Imported(
        val routeId: Long,
        val name: String,
    ) : ImportResult

    data class Failed(
        val reason: ImportError,
    ) : ImportResult
}

/** GPX file to editable saved route (M7.4, M7.5). */
class GpxImporter
    @Inject
    constructor(
        private val files: GpxFiles,
        private val routes: RouteRepository,
        private val elevation: RouteElevation,
    ) {
        /** @return the id of the new route. */
        suspend fun import(
            uri: Uri,
            sport: Sport = Sport.HIKING,
        ): ImportResult =
            try {
                val doc = files.read(uri)
                val line = doc.mainLine
                if (line == null) {
                    ImportResult.Failed(ImportError.EMPTY)
                } else {
                    val name = doc.name ?: files.displayName(uri) ?: DEFAULT_NAME
                    val route = GpxConversion.toRoute(line, name, sport).copy(description = doc.description.orEmpty())
                    val profile = elevation.profile(route.track)
                    val id =
                        routes.save(
                            route.copy(
                                distanceMeters = route.track.lengthMeters(),
                                ascentMeters = profile?.ascentMeters ?: 0.0,
                                descentMeters = profile?.descentMeters ?: 0.0,
                            ),
                        )
                    ImportResult.Imported(id, name)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GpxFormatException) {
                Log.i(TAG, "Not a GPX: $uri", e)
                ImportResult.Failed(ImportError.NOT_GPX)
            } catch (e: IOException) {
                Log.w(TAG, "Cannot read $uri", e)
                ImportResult.Failed(ImportError.UNREADABLE)
            } catch (e: SecurityException) {
                // The sharing app revoked access to the file.
                Log.w(TAG, "No access to $uri", e)
                ImportResult.Failed(ImportError.UNREADABLE)
            }

        private companion object {
            const val TAG = "GpxImporter"
            const val DEFAULT_NAME = "Percorso importato"
        }
    }
