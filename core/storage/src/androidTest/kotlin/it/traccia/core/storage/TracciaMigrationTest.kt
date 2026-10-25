package it.traccia.core.storage

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TracciaMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TracciaDatabase::class.java)

    @Test
    fun migrate1To2KeepsRoutesAndMatchesSchema() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO routes (name, sport, created_at, updated_at) VALUES ('Anello', 'HIKING', 1, 2)")
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, TracciaMigrations.MIGRATION_1_2).use { db ->
            db.query("SELECT name FROM routes").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Anello", cursor.getString(0))
            }
        }
    }

    @Test
    fun migrate2To3AddsRouteColumnsWithDefaults() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL("INSERT INTO routes (name, sport, created_at, updated_at) VALUES ('Anello', 'HIKING', 1, 2)")
        }
        helper.runMigrationsAndValidate(TEST_DB, 3, true, TracciaMigrations.MIGRATION_2_3).use { db ->
            db.query("SELECT name, points, distance_m, straight_line FROM routes").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Anello", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertEquals(0.0, cursor.getDouble(2), 0.0)
                assertEquals(0, cursor.getInt(3))
            }
        }
    }

    @Test
    fun allMigrationsReachCurrentVersion() =
        runTest {
            helper.createDatabase(TEST_DB, 1).close()
            val db =
                Room
                    .databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TracciaDatabase::class.java, TEST_DB)
                    .addMigrations(*TracciaMigrations.ALL)
                    .build()
            assertEquals(emptyList<Any>(), db.offlineRegionDao().observeAll().first())
            db.close()
        }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
