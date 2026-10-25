package it.traccia.core.storage.offline

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** An installed offline region pack. */
@Entity(tableName = "offline_regions")
data class OfflineRegionEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Data version from the catalog (ISO date), compared to detect updates. */
    val version: String,
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
    @ColumnInfo(name = "installed_at") val installedAtMillis: Long,
)

/** One MBTiles file of a region pack (basemap, trails or dem). */
@Entity(
    tableName = "offline_region_files",
    primaryKeys = ["region_id", "type"],
    foreignKeys = [
        ForeignKey(
            entity = OfflineRegionEntity::class,
            parentColumns = ["id"],
            childColumns = ["region_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("region_id")],
)
data class OfflineRegionFileEntity(
    @ColumnInfo(name = "region_id") val regionId: String,
    val type: String,
    val path: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val sha256: String,
)

data class OfflineRegionWithFiles(
    @Embedded val region: OfflineRegionEntity,
    @Relation(parentColumn = "id", entityColumn = "region_id")
    val files: List<OfflineRegionFileEntity>,
)
