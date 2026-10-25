package it.traccia.core.storage.offline

import android.database.sqlite.SQLiteException
import android.util.Log
import it.traccia.core.geo.WebMercator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Serves tiles from the installed region packs, keeping their MBTiles files open. */
@Singleton
class OfflineTileStore
    @Inject
    constructor(
        private val dao: OfflineRegionDao,
    ) : LocalTileSource {
        private class Pack(
            val west: Double,
            val south: Double,
            val east: Double,
            val north: Double,
            val files: Map<String, String>,
        )

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val readers = ConcurrentHashMap<String, MbtilesReader>()

        // Loaded synchronously on first use, so the very first tiles after an offline start are local too.
        @Volatile
        private var packs: List<Pack>? = null

        init {
            scope.launch {
                dao
                    .observeAll()
                    .drop(1)
                    // The store must never crash the app: on a database error keep serving the last known packs.
                    .catch { Log.e(TAG, "Cannot observe offline regions", it) }
                    .collect { update(it) }
            }
        }

        override fun find(
            layer: String,
            z: Int,
            x: Int,
            y: Int,
        ): LocalTile? {
            val tile = TileBounds.of(z, x, y)
            val candidates =
                currentPacks()
                    .filter { it.files.containsKey(layer) && tile.intersects(it) }
                    // Prefer a pack that covers the whole tile.
                    .sortedByDescending { tile.isInside(it) }
            for (pack in candidates) {
                val path = pack.files.getValue(layer)
                val data = readTile(path, z, x, y) ?: continue
                return LocalTile(data, coversTile = tile.isInside(pack))
            }
            return null
        }

        /** Closes the files of a region before they are deleted or replaced. */
        fun release(paths: Collection<String>) {
            paths.forEach { readers.remove(it)?.close() }
        }

        private fun currentPacks(): List<Pack> = packs ?: runBlocking { update(dao.getAll()) }

        private fun update(regions: List<OfflineRegionWithFiles>): List<Pack> {
            val next =
                regions.map { r ->
                    Pack(r.region.west, r.region.south, r.region.east, r.region.north, r.files.associate { it.type to it.path })
                }
            packs = next
            val alive = next.flatMap { it.files.values }.toSet()
            release(readers.keys - alive)
            return next
        }

        private fun readTile(
            path: String,
            z: Int,
            x: Int,
            y: Int,
        ): ByteArray? {
            if (!File(path).exists()) return null
            return try {
                readers.computeIfAbsent(path, ::MbtilesReader).tile(z, x, y)
            } catch (e: MbtilesException) {
                Log.w(TAG, e.message, e)
                null
            } catch (e: SQLiteException) {
                // Corrupted or unreadable file (e.g. SD card removed): behave as if it was not installed.
                Log.w(TAG, "Cannot open $path", e)
                null
            }
        }

        private class TileBounds(
            val west: Double,
            val south: Double,
            val east: Double,
            val north: Double,
        ) {
            fun intersects(p: Pack) = west < p.east && east > p.west && south < p.north && north > p.south

            fun isInside(p: Pack) = west >= p.west && east <= p.east && south >= p.south && north <= p.north

            companion object {
                fun of(
                    z: Int,
                    x: Int,
                    y: Int,
                ): TileBounds {
                    // World of 2^z "pixels": one per tile.
                    val world = (1 shl z).toDouble()
                    return TileBounds(
                        west = WebMercator.xToLon(x.toDouble(), world),
                        south = WebMercator.yToLat(y + 1.0, world),
                        east = WebMercator.xToLon(x + 1.0, world),
                        north = WebMercator.yToLat(y.toDouble(), world),
                    )
                }
            }
        }

        private companion object {
            const val TAG = "OfflineTileStore"
        }
    }
