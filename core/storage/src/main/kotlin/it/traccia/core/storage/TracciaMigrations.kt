package it.traccia.core.storage

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Every schema change must bump [TracciaDatabase.VERSION] and add a [Migration] here,
 * with a test in `TracciaMigrationTest`. Destructive migrations are never allowed:
 * the database holds the user's routes.
 */
object TracciaMigrations {
    /** M2: installed offline region packs. */
    val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `offline_regions` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`version` TEXT NOT NULL, `west` REAL NOT NULL, `south` REAL NOT NULL, `east` REAL NOT NULL, " +
                        "`north` REAL NOT NULL, `installed_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `offline_region_files` (`region_id` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                        "`path` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, `sha256` TEXT NOT NULL, " +
                        "PRIMARY KEY(`region_id`, `type`), FOREIGN KEY(`region_id`) REFERENCES `offline_regions`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_offline_region_files_region_id` ON `offline_region_files` (`region_id`)",
                )
            }
        }

    /** M5/M7: route geometry, routing settings and statistics. */
    val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                listOf(
                    "`description` TEXT NOT NULL DEFAULT ''",
                    "`straight_line` INTEGER NOT NULL DEFAULT 0",
                    "`routing_options` TEXT NOT NULL DEFAULT ''",
                    "`points` TEXT NOT NULL DEFAULT ''",
                    "`segments` TEXT NOT NULL DEFAULT ''",
                    "`preview` TEXT NOT NULL DEFAULT ''",
                    "`distance_m` REAL NOT NULL DEFAULT 0",
                    "`ascent_m` REAL NOT NULL DEFAULT 0",
                    "`descent_m` REAL NOT NULL DEFAULT 0",
                ).forEach { column -> connection.execSQL("ALTER TABLE `routes` ADD COLUMN $column") }
            }
        }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
