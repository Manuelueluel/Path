package it.traccia.feature.planner.draft

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import it.traccia.core.common.Sport
import it.traccia.core.geo.Polyline
import it.traccia.core.storage.route.RouteSegment
import it.traccia.core.storage.route.RoutingSettings
import it.traccia.core.storage.route.SegmentKind
import it.traccia.feature.planner.PlannedRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The route being edited, saved automatically so no work is lost (M5.11). */
data class PlannerDraft(
    val route: PlannedRoute,
    val sport: Sport,
    val routing: RoutingSettings,
    /** Saved route being edited, null for a new route. */
    val routeId: Long?,
    val routeName: String?,
)

interface DraftStore {
    suspend fun load(): PlannerDraft?

    suspend fun save(draft: PlannerDraft)
}

@Singleton
class FileDraftStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : DraftStore {
        private val file = File(context.filesDir, "planner/draft.json")

        override suspend fun load(): PlannerDraft? =
            withContext(Dispatchers.IO) {
                if (!file.exists()) return@withContext null
                try {
                    decode(json.decodeFromString(DraftFile.serializer(), file.readText()))
                } catch (e: SerializationException) {
                    Log.w(TAG, "Corrupted draft, starting empty", e)
                    null
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Invalid draft, starting empty", e)
                    null
                } catch (e: IOException) {
                    Log.w(TAG, "Cannot read the draft", e)
                    null
                }
            }

        override suspend fun save(draft: PlannerDraft) =
            withContext(Dispatchers.IO) {
                file.parentFile?.mkdirs()
                // Write then rename: a crash mid-write never corrupts the previous draft.
                val tmp = File(file.parentFile, "draft.json.tmp")
                tmp.writeText(json.encodeToString(DraftFile.serializer(), encode(draft)))
                if (!tmp.renameTo(file)) throw IOException("Cannot replace the draft")
            }

        @Serializable
        private data class DraftFile(
            val routeId: Long? = null,
            val routeName: String? = null,
            val sport: String,
            val straightLine: Boolean = false,
            val options: List<String> = emptyList(),
            val points: String,
            /** "KIND:polyline", or null for a segment still to compute. */
            val segments: List<String?>,
        )

        private fun encode(d: PlannerDraft) =
            DraftFile(
                routeId = d.routeId,
                routeName = d.routeName,
                sport = d.sport.name,
                straightLine = d.routing.straightLine,
                options = d.routing.options.sorted(),
                points = Polyline.encode(d.route.points),
                segments = d.route.segments.map { s -> s?.let { it.kind.name + ":" + Polyline.encode(it.geometry) } },
            )

        private fun decode(f: DraftFile): PlannerDraft {
            val segments =
                f.segments.map { line ->
                    line?.let { RouteSegment(Polyline.decode(it.substringAfter(':')), SegmentKind.valueOf(it.substringBefore(':'))) }
                }
            return PlannerDraft(
                route = PlannedRoute(Polyline.decode(f.points), segments),
                sport = Sport.valueOf(f.sport),
                routing = RoutingSettings(f.straightLine, f.options.toSet()),
                routeId = f.routeId,
                routeName = f.routeName,
            )
        }

        private companion object {
            const val TAG = "DraftStore"
            val json = Json { ignoreUnknownKeys = true }
        }
    }
