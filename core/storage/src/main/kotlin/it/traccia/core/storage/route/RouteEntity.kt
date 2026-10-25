package it.traccia.core.storage.route

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import it.traccia.core.common.Sport

/**
 * A saved route. Geometries are encoded polylines (see [RouteCodec]); statistics are stored
 * so the library can list routes without decoding them.
 */
@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sport: Sport,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
    @ColumnInfo(defaultValue = "") val description: String = "",
    @ColumnInfo(name = "straight_line", defaultValue = "0") val straightLine: Boolean = false,
    @ColumnInfo(name = "routing_options", defaultValue = "") val routingOptions: String = "",
    @ColumnInfo(defaultValue = "") val points: String = "",
    @ColumnInfo(defaultValue = "") val segments: String = "",
    @ColumnInfo(defaultValue = "") val preview: String = "",
    @ColumnInfo(name = "distance_m", defaultValue = "0") val distanceMeters: Double = 0.0,
    @ColumnInfo(name = "ascent_m", defaultValue = "0") val ascentMeters: Double = 0.0,
    @ColumnInfo(name = "descent_m", defaultValue = "0") val descentMeters: Double = 0.0,
)
