package it.traccia.core.storage

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TracciaMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TracciaDatabase::class.java)

    @Test
    fun createsFirstSchemaVersion() {
        helper.createDatabase(TEST_DB, 1).close()
    }

    @Test
    fun allMigrationsReachCurrentVersion() {
        helper.createDatabase(TEST_DB, 1).close()
        Room
            .databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TracciaDatabase::class.java, TEST_DB)
            .addMigrations(*TracciaMigrations.ALL)
            .build()
            .apply { openHelper.writableDatabase.close() }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
