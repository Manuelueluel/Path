package it.traccia.feature.map.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.traccia.feature.map.StyleLoader
import it.traccia.feature.map.maplibre.ContourTileProvider
import it.traccia.feature.map.prefs.DataStoreMapPreferencesRepository
import it.traccia.feature.map.prefs.MapPreferencesRepository
import it.traccia.feature.map.style.StyleRepository
import org.maplibre.android.style.sources.GeometryTileProvider
import javax.inject.Qualifier
import javax.inject.Singleton

/** The map's own preferences file. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MapDataStore

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MapModule {
    @Binds
    abstract fun bindPreferences(impl: DataStoreMapPreferencesRepository): MapPreferencesRepository

    @Binds
    abstract fun bindStyleLoader(impl: StyleRepository): StyleLoader

    @Binds
    abstract fun bindContourProvider(impl: ContourTileProvider): GeometryTileProvider

    companion object {
        @Provides
        @Singleton
        @MapDataStore
        fun provideDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("map") }
    }
}
