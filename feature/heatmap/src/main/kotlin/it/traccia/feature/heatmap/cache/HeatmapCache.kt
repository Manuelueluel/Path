package it.traccia.feature.heatmap.cache

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class CachedTile(
    /** Empty when Strava has no heatmap for the tile. */
    val data: ByteArray,
    val downloadedAtMillis: Long,
)

/** Tile range of a map area at one zoom, in XYZ coordinates. */
data class TileRange(
    val zoom: Int,
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int,
)

data class CacheAge(
    val oldestMillis: Long,
    val newestMillis: Long,
    val tiles: Int,
)

interface HeatmapTileCache {
    fun get(
        sport: String,
        color: String,
        z: Int,
        x: Int,
        y: Int,
    ): CachedTile?

    fun put(
        sport: String,
        color: String,
        z: Int,
        x: Int,
        y: Int,
        tile: CachedTile,
    )

    /** Age of the cached tiles of an area (M3.10), null when nothing is cached there. */
    fun age(
        sport: String,
        color: String,
        ranges: List<TileRange>,
    ): CacheAge?

    /** Marks the tiles of an area as outdated so the next display downloads them again (M3.9). */
    fun markStale(
        sport: String,
        color: String,
        ranges: List<TileRange>,
    )

    /** Bytes used by each sport (M3.12). */
    fun sizes(): Map<String, Long>

    fun clear(sport: String)
}

/**
 * One MBTiles-like SQLite file per sport (M3.7), with the download date of every tile.
 * Lives in no-backup storage and is never exported, shared or published.
 */
class SqliteHeatmapCache(
    private val dir: File,
) : HeatmapTileCache {
    private val databases = ConcurrentHashMap<String, SQLiteDatabase>()

    override fun get(
        sport: String,
        color: String,
        z: Int,
        x: Int,
        y: Int,
    ): CachedTile? =
        db(sport)
            .rawQuery(
                "SELECT tile_data, downloaded_at FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ? AND color = ?",
                arrayOf(z.toString(), x.toString(), tmsRow(z, y).toString(), color),
            ).use { c -> if (c.moveToFirst()) CachedTile(c.getBlob(0) ?: ByteArray(0), c.getLong(1)) else null }

    override fun put(
        sport: String,
        color: String,
        z: Int,
        x: Int,
        y: Int,
        tile: CachedTile,
    ) {
        val values =
            ContentValues().apply {
                put("zoom_level", z)
                put("tile_column", x)
                put("tile_row", tmsRow(z, y))
                put("color", color)
                put("tile_data", tile.data)
                put("downloaded_at", tile.downloadedAtMillis)
            }
        db(sport).insertWithOnConflict("tiles", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun age(
        sport: String,
        color: String,
        ranges: List<TileRange>,
    ): CacheAge? {
        if (!file(sport).exists()) return null
        var oldest = Long.MAX_VALUE
        var newest = 0L
        var count = 0
        ranges.forEach { r ->
            db(sport)
                .rawQuery(
                    "SELECT MIN(downloaded_at), MAX(downloaded_at), COUNT(*) FROM tiles WHERE color = ? AND zoom_level = ? " +
                        "AND tile_column BETWEEN ? AND ? AND tile_row BETWEEN ? AND ? AND downloaded_at > 0",
                    rangeArgs(color, r),
                ).use { c ->
                    if (c.moveToFirst() && c.getInt(2) > 0) {
                        oldest = minOf(oldest, c.getLong(0))
                        newest = maxOf(newest, c.getLong(1))
                        count += c.getInt(2)
                    }
                }
        }
        return if (count > 0) CacheAge(oldest, newest, count) else null
    }

    override fun markStale(
        sport: String,
        color: String,
        ranges: List<TileRange>,
    ) {
        if (!file(sport).exists()) return
        ranges.forEach { r ->
            db(sport).execSQL(
                "UPDATE tiles SET downloaded_at = 0 WHERE color = ? AND zoom_level = ? " +
                    "AND tile_column BETWEEN ? AND ? AND tile_row BETWEEN ? AND ?",
                rangeArgs(color, r),
            )
        }
    }

    override fun sizes(): Map<String, Long> =
        dir
            .listFiles { f -> f.name.endsWith(EXTENSION) }
            .orEmpty()
            .associate { it.name.removeSuffix(EXTENSION) to it.length() }

    override fun clear(sport: String) {
        databases.remove(sport)?.close()
        File(dir, "$sport$EXTENSION").delete()
        File(dir, "$sport$EXTENSION-journal").delete()
    }

    private fun rangeArgs(
        color: String,
        r: TileRange,
    ) = arrayOf(
        color,
        r.zoom.toString(),
        r.minX.toString(),
        r.maxX.toString(),
        tmsRow(r.zoom, r.maxY).toString(),
        tmsRow(r.zoom, r.minY).toString(),
    )

    private fun file(sport: String) = File(dir, "$sport$EXTENSION")

    private fun db(sport: String): SQLiteDatabase =
        databases.computeIfAbsent(sport) {
            dir.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(file(sport), null).apply {
                execSQL("CREATE TABLE IF NOT EXISTS metadata (name TEXT, value TEXT)")
                execSQL(
                    "CREATE TABLE IF NOT EXISTS tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, color TEXT, " +
                        "tile_data BLOB, downloaded_at INTEGER, PRIMARY KEY (zoom_level, tile_column, tile_row, color))",
                )
            }
        }

    /** MBTiles rows follow the TMS scheme. */
    private fun tmsRow(
        z: Int,
        y: Int,
    ) = (1 shl z) - 1 - y

    private companion object {
        const val EXTENSION = ".mbtiles"
    }
}
