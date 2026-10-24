package it.traccia.core.storage.route

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun findById(id: Long): RouteEntity?

    @Upsert
    suspend fun upsert(route: RouteEntity): Long

    @Delete
    suspend fun delete(route: RouteEntity)
}
