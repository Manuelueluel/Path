package it.traccia.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import it.traccia.core.storage.route.RouteDao
import it.traccia.core.storage.route.RouteEntity

@Database(
    entities = [RouteEntity::class],
    version = TracciaDatabase.VERSION,
    exportSchema = true,
)
abstract class TracciaDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao

    companion object {
        const val VERSION = 1
        const val NAME = "traccia.db"
    }
}
