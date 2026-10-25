package it.traccia.feature.offlinemaps.storage

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import it.traccia.feature.offlinemaps.di.OfflineMapsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

enum class StorageKind { INTERNAL, REMOVABLE }

/** A place where region packs can be saved (M2.8). */
data class StorageLocation(
    val id: String,
    val kind: StorageKind,
    val root: File,
    val freeBytes: Long,
) {
    /** Region packs: `<root>/maps/<region>/<version>/`, partial downloads in `<root>/maps/<region>/download/`. */
    fun regionDir(regionId: String): File = File(root, "maps/$regionId")

    fun stagingDir(regionId: String): File = File(regionDir(regionId), "download")

    /** BRouter segment files, shared by all regions on this volume. */
    fun segmentsDir(): File = File(root, "maps/segments")
}

interface StorageLocations {
    /** Mounted volumes, internal storage first. */
    fun available(): List<StorageLocation>

    val selectedId: Flow<String>

    suspend fun select(id: String)

    /** Where new downloads go: the selected volume if still mounted, internal storage otherwise. */
    suspend fun selected(): StorageLocation

    /** Volume holding a partial download of [regionId], to resume it in place. */
    fun stagingLocation(regionId: String): StorageLocation?
}

internal class AndroidStorageLocations
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @param:OfflineMapsDataStore private val dataStore: DataStore<Preferences>,
    ) : StorageLocations {
        override fun available(): List<StorageLocation> {
            // App-specific external dirs: no permission needed, removed with the app. Index 0 is internal storage.
            val dirs = context.getExternalFilesDirs(null).toList()
            val locations =
                dirs
                    .mapIndexedNotNull { index, dir ->
                        if (dir == null ||
                            Environment.getExternalStorageState(dir) != Environment.MEDIA_MOUNTED
                        ) {
                            return@mapIndexedNotNull null
                        }
                        val removable = index > 0 && Environment.isExternalStorageRemovable(dir)
                        StorageLocation(
                            id = if (removable) "sd-$index" else INTERNAL_ID,
                            kind = if (removable) StorageKind.REMOVABLE else StorageKind.INTERNAL,
                            root = dir,
                            freeBytes = dir.usableSpace,
                        )
                    }.distinctBy { it.id }
            return locations.ifEmpty {
                listOf(StorageLocation(INTERNAL_ID, StorageKind.INTERNAL, context.filesDir, context.filesDir.usableSpace))
            }
        }

        override val selectedId: Flow<String> = dataStore.data.map { it[KEY_SELECTED] ?: INTERNAL_ID }

        override suspend fun select(id: String) {
            dataStore.edit { it[KEY_SELECTED] = id }
        }

        override suspend fun selected(): StorageLocation {
            val id = selectedId.first()
            val all = available()
            return all.firstOrNull { it.id == id } ?: all.first()
        }

        override fun stagingLocation(regionId: String): StorageLocation? =
            available().firstOrNull { it.stagingDir(regionId).listFiles()?.isNotEmpty() == true }

        private companion object {
            const val INTERNAL_ID = "internal"
            val KEY_SELECTED = stringPreferencesKey("storage_location")
        }
    }
