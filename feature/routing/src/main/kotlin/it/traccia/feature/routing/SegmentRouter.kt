package it.traccia.feature.routing

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import it.traccia.core.common.AppError
import it.traccia.core.common.AppResult
import it.traccia.core.common.LruCache
import it.traccia.core.common.Sport
import it.traccia.core.geo.GeoPoint
import it.traccia.core.storage.offline.OfflineRegionDao
import it.traccia.core.storage.route.RoutingSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Routes one segment of a planned route (M4.5). */
fun interface SegmentRouter {
    suspend fun route(
        from: GeoPoint,
        to: GeoPoint,
        sport: Sport,
        settings: RoutingSettings,
    ): AppResult<List<GeoPoint>>
}

/** User options available for each sport (M4.4). */
fun interface RoutingOptions {
    fun optionsFor(sport: Sport): List<String>
}

/**
 * BRouter on the segment files of the installed region packs, with an in-memory cache of
 * computed segments (M4.7). Profiles are copied from the assets on first use.
 */
@Singleton
class BRouterSegmentRouter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val regions: OfflineRegionDao,
    ) : SegmentRouter,
        RoutingOptions {
        private val profiles: RoutingProfiles by lazy {
            RoutingProfiles.parse(
                context.assets
                    .open(RoutingProfiles.ASSET)
                    .bufferedReader()
                    .use { it.readText() },
            )
        }
        private val profileDir = File(context.noBackupFilesDir, "brouter/profiles")
        private val engine = BRouterEngine(profileDir)
        private val cache = LruCache<String, List<GeoPoint>>(CACHE_SEGMENTS)

        override fun optionsFor(sport: Sport): List<String> = profiles.optionsFor(sport)

        override suspend fun route(
            from: GeoPoint,
            to: GeoPoint,
            sport: Sport,
            settings: RoutingSettings,
        ): AppResult<List<GeoPoint>> {
            val profile = profiles.profile(sport)
            val parameters = profiles.parameters(sport, settings.options)
            val key = "$from|$to|${profile.brf}|${parameters.toSortedMap()}"
            cache[key]?.let { return AppResult.Success(it) }
            return try {
                ensureProfiles()
                val segmentDir = segmentDir(from) ?: return AppResult.Failure(AppError.NotFound(BRouterEngine.segmentFileName(from)))
                val track = engine.route(from, to, profile.brf, parameters, segmentDir)
                cache.put(key, track)
                AppResult.Success(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RoutingException) {
                Log.i(TAG, "No route $from -> $to: ${e.message}")
                AppResult.Failure(AppError.NotFound("route", e))
            } catch (e: IOException) {
                AppResult.Failure(AppError.Storage(e))
            }
        }

        /** Directory holding the segment file for [p], among the installed regions. */
        private suspend fun segmentDir(p: GeoPoint): File? {
            val name = BRouterEngine.segmentFileName(p)
            return regions
                .getAll()
                .flatMap { it.files }
                .filter { it.type.startsWith(ROUTING_FILE_TYPE) }
                .map { File(it.path) }
                .firstOrNull { it.name == name && it.exists() }
                ?.parentFile
        }

        private suspend fun ensureProfiles() =
            withContext(Dispatchers.IO) {
                profileDir.mkdirs()
                context.assets.list(ASSET_DIR).orEmpty().forEach { name ->
                    val target = File(profileDir, name)
                    val bytes = context.assets.open("$ASSET_DIR/$name").use { it.readBytes() }
                    // Copy when missing or changed by an app update.
                    if (!target.exists() || target.length() != bytes.size.toLong()) target.writeBytes(bytes)
                }
            }

        private companion object {
            const val TAG = "BRouterSegmentRouter"
            const val ASSET_DIR = "brouter"
            const val ROUTING_FILE_TYPE = "routing"
            const val CACHE_SEGMENTS = 200
        }
    }
