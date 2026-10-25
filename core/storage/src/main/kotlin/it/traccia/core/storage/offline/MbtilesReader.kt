package it.traccia.core.storage.offline

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.io.Closeable

/**
 * Read-only access to an MBTiles file (works with Planetiler's deduplicated `tiles` view too).
 * Thread-safe: Android's SQLiteDatabase serializes access to its connection.
 */
class MbtilesReader(
    val path: String,
) : Closeable {
    private val db: SQLiteDatabase =
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)

    /** Tile in XYZ (slippy map) coordinates, or null when absent. */
    fun tile(
        z: Int,
        x: Int,
        y: Int,
    ): ByteArray? {
        // MBTiles rows follow the TMS scheme: origin at the bottom-left.
        val tmsY = (1 shl z) - 1 - y
        return try {
            db
                .rawQuery(QUERY, arrayOf(z.toString(), x.toString(), tmsY.toString()))
                .use { cursor -> if (cursor.moveToFirst()) cursor.getBlob(0) else null }
        } catch (e: SQLiteException) {
            throw MbtilesException("Cannot read tile $z/$x/$y from $path", e)
        }
    }

    override fun close() = db.close()

    private companion object {
        const val QUERY = "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?"
    }
}

class MbtilesException(
    message: String,
    cause: Throwable,
) : Exception(message, cause)
