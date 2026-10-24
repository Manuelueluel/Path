package it.traccia.core.storage

import androidx.room.migration.Migration

/**
 * Every schema change must bump [TracciaDatabase.VERSION] and add a [Migration] here,
 * with a test in `TracciaMigrationTest`. Destructive migrations are never allowed:
 * the database holds the user's routes.
 */
object TracciaMigrations {
    val ALL: Array<Migration> = emptyArray()
}
