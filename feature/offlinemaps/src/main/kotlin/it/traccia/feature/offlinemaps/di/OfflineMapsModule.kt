package it.traccia.feature.offlinemaps.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.traccia.feature.offlinemaps.catalog.CatalogRepository
import it.traccia.feature.offlinemaps.catalog.CatalogSource
import it.traccia.feature.offlinemaps.download.RegionDownloadController
import it.traccia.feature.offlinemaps.download.WorkManagerRegionDownloads
import it.traccia.feature.offlinemaps.installed.InstalledRegions
import it.traccia.feature.offlinemaps.installed.RoomInstalledRegions
import it.traccia.feature.offlinemaps.storage.AndroidStorageLocations
import it.traccia.feature.offlinemaps.storage.StorageLocations
import javax.inject.Qualifier
import javax.inject.Singleton

/** Preferences of the offline maps feature (storage location). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OfflineMapsDataStore

@Module
@InstallIn(SingletonComponent::class)
internal abstract class OfflineMapsModule {
    @Binds
    abstract fun bindCatalog(impl: CatalogRepository): CatalogSource

    @Binds
    abstract fun bindInstalled(impl: RoomInstalledRegions): InstalledRegions

    @Binds
    abstract fun bindDownloads(impl: WorkManagerRegionDownloads): RegionDownloadController

    @Binds
    abstract fun bindStorage(impl: AndroidStorageLocations): StorageLocations

    companion object {
        @Provides
        @Singleton
        @OfflineMapsDataStore
        fun provideDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("offline_maps") }

        @Provides
        fun provideWorkManager(
            @ApplicationContext context: Context,
        ): WorkManager = WorkManager.getInstance(context)
    }
}
