package it.traccia.core.storage

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.traccia.core.storage.offline.OfflineRegionEntity
import it.traccia.core.storage.offline.OfflineRegionFileEntity
import it.traccia.core.storage.offline.OfflineTileStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OfflineTileStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: TracciaDatabase
    private lateinit var mbtiles: File

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, TracciaDatabase::class.java).build()
        mbtiles = File(context.cacheDir, "test-basemap.mbtiles").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(mbtiles, null).use { sql ->
            sql.execSQL("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
            // z14 tile of the Resegone (8622, 5837) stored with its TMS row, and the z2 tile containing Italy (2, 1).
            sql.execSQL("INSERT INTO tiles VALUES (14, 8622, ${(1 shl 14) - 1 - 5837}, X'0102')")
            sql.execSQL("INSERT INTO tiles VALUES (2, 2, ${(1 shl 2) - 1 - 1}, X'0304')")
        }
    }

    @After
    fun tearDown() {
        db.close()
        mbtiles.delete()
    }

    private suspend fun install() {
        db.offlineRegionDao().replace(
            OfflineRegionEntity(
                "test",
                "Test",
                "2026-10-24",
                west = 9.25,
                south = 45.78,
                east = 9.60,
                north = 46.05,
                installedAtMillis = 0,
            ),
            listOf(OfflineRegionFileEntity("test", "basemap", mbtiles.path, mbtiles.length(), "")),
        )
    }

    @Test
    fun servesTileFullyInsideTheRegion() =
        runTest {
            install()
            val tile = OfflineTileStore(db.offlineRegionDao()).find("basemap", 14, 8622, 5837)
            assertArrayEquals(byteArrayOf(1, 2), tile?.data)
            assertTrue(checkNotNull(tile).coversTile)
        }

    @Test
    fun lowZoomTileIsOnlyPartiallyCovered() =
        runTest {
            install()
            val tile = OfflineTileStore(db.offlineRegionDao()).find("basemap", 2, 2, 1)
            assertFalse(checkNotNull(tile).coversTile)
        }

    @Test
    fun missingLayerOrAreaGivesNull() =
        runTest {
            install()
            val store = OfflineTileStore(db.offlineRegionDao())
            assertNull(store.find("dem", 14, 8622, 5837))
            assertNull(store.find("basemap", 14, 0, 0))
        }

    @Test
    fun nothingInstalledGivesNull() {
        assertNull(OfflineTileStore(db.offlineRegionDao()).find("basemap", 14, 8622, 5837))
    }
}
