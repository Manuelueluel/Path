package it.traccia.core.storage.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineRegionDao {
    @Transaction
    @Query("SELECT * FROM offline_regions ORDER BY name")
    fun observeAll(): Flow<List<OfflineRegionWithFiles>>

    @Transaction
    @Query("SELECT * FROM offline_regions ORDER BY name")
    suspend fun getAll(): List<OfflineRegionWithFiles>

    @Transaction
    @Query("SELECT * FROM offline_regions WHERE id = :id")
    suspend fun find(id: String): OfflineRegionWithFiles?

    @Query("DELETE FROM offline_regions WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegion(region: OfflineRegionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<OfflineRegionFileEntity>)

    /** Installs or updates a region atomically: the old file rows are dropped with the old region row. */
    @Transaction
    suspend fun replace(
        region: OfflineRegionEntity,
        files: List<OfflineRegionFileEntity>,
    ) {
        delete(region.id)
        insertRegion(region)
        insertFiles(files)
    }
}
