package it.traccia.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import it.traccia.core.storage.offline.OfflineRegionDao
import it.traccia.core.storage.offline.OfflineRegionEntity
import it.traccia.core.storage.offline.OfflineRegionFileEntity
import it.traccia.core.storage.route.RouteDao
import it.traccia.core.storage.route.RouteEntity

@Database(
    entities = [RouteEntity::class, OfflineRegionEntity::class, OfflineRegionFileEntity::class],
    version = TracciaDatabase.VERSION,
    exportSchema = true,
)
abstract class TracciaDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao

    abstract fun offlineRegionDao(): OfflineRegionDao

    companion object {
        const val VERSION = 3
        const val NAME = "traccia.db"
    }
}
