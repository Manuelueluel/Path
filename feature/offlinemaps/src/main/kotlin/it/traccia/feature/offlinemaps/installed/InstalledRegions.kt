package it.traccia.feature.offlinemaps.installed

import it.traccia.core.storage.offline.OfflineRegionDao
import it.traccia.core.storage.offline.OfflineRegionEntity
import it.traccia.core.storage.offline.OfflineRegionFileEntity
import it.traccia.core.storage.offline.OfflineRegionWithFiles
import it.traccia.core.storage.offline.OfflineTileStore
import it.traccia.feature.offlinemaps.catalog.CatalogRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledFile(
    val type: String,
    val path: String,
    val sizeBytes: Long,
)

data class InstalledRegion(
    val id: String,
    val name: String,
    val version: String,
    val files: List<InstalledFile>,
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}

interface InstalledRegions {
    val regions: Flow<List<InstalledRegion>>

    /**
     * Registers a fully downloaded and verified pack, removing the previous version if any.
     * @param files installed file by [it.traccia.feature.offlinemaps.catalog.CatalogFile.fileKey].
     */
    suspend fun install(
        region: CatalogRegion,
        files: Map<String, File>,
    )

    /** Removes a region and its files. */
    suspend fun delete(id: String)
}

@Singleton
class RoomInstalledRegions
    @Inject
    constructor(
        private val dao: OfflineRegionDao,
        private val tileStore: OfflineTileStore,
    ) : InstalledRegions {
        override val regions: Flow<List<InstalledRegion>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

        override suspend fun install(
            region: CatalogRegion,
            files: Map<String, File>,
        ) {
            val previous = dao.find(region.id)
            val bbox = region.bbox
            dao.replace(
                OfflineRegionEntity(
                    id = region.id,
                    name = region.name,
                    version = region.version,
                    west = bbox[0],
                    south = bbox[1],
                    east = bbox[2],
                    north = bbox[3],
                    installedAtMillis = System.currentTimeMillis(),
                ),
                region.files.map { f ->
                    val file = files.getValue(f.fileKey())
                    OfflineRegionFileEntity(region.id, f.fileKey(), file.absolutePath, file.length(), f.sha256)
                },
            )
            // Old version files are no longer referenced: close and delete them.
            previous?.let { old -> deleteUnreferenced(old.files.map { it.path }) }
        }

        override suspend fun delete(id: String) {
            val region = dao.find(id) ?: return
            dao.delete(id)
            deleteUnreferenced(region.files.map { it.path })
        }

        /** Deletes the files no installed region uses any more (routing segments are shared). */
        private suspend fun deleteUnreferenced(paths: List<String>) {
            val inUse = dao.getAll().flatMap { r -> r.files.map { it.path } }.toSet()
            deleteFiles(paths.filterNot { it in inUse })
        }

        private suspend fun deleteFiles(paths: List<String>) =
            withContext(Dispatchers.IO) {
                tileStore.release(paths)
                paths.forEach { path ->
                    val file = File(path)
                    file.delete()
                    // Remove the version directory when it becomes empty.
                    file.parentFile?.takeIf { it.listFiles()?.isEmpty() == true }?.delete()
                }
            }

        private fun OfflineRegionWithFiles.toDomain() =
            InstalledRegion(
                id = region.id,
                name = region.name,
                version = region.version,
                files = files.map { InstalledFile(it.type, it.path, it.sizeBytes) },
            )
    }
