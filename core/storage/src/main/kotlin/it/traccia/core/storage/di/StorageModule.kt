package it.traccia.core.storage.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.traccia.core.storage.TracciaDatabase
import it.traccia.core.storage.TracciaMigrations
import it.traccia.core.storage.offline.OfflineRegionDao
import it.traccia.core.storage.prefs.DataStoreUserPreferences
import it.traccia.core.storage.prefs.UserPreferencesRepository
import it.traccia.core.storage.route.RoomRouteRepository
import it.traccia.core.storage.route.RouteDao
import it.traccia.core.storage.route.RouteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageBindings {
    @Binds
    abstract fun bindRouteRepository(impl: RoomRouteRepository): RouteRepository
}

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): TracciaDatabase =
        Room
            .databaseBuilder(context, TracciaDatabase::class.java, TracciaDatabase.NAME)
            .addMigrations(*TracciaMigrations.ALL)
            .build()

    @Provides
    fun provideRouteDao(database: TracciaDatabase): RouteDao = database.routeDao()

    @Provides
    fun provideOfflineRegionDao(database: TracciaDatabase): OfflineRegionDao = database.offlineRegionDao()

    @Provides
    @Singleton
    fun provideUserPreferences(
        @ApplicationContext context: Context,
    ): UserPreferencesRepository =
        DataStoreUserPreferences(
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("user") },
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
}
