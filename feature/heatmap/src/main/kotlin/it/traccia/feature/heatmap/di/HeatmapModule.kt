package it.traccia.feature.heatmap.di

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
import it.traccia.core.storage.prefs.UserPreferencesRepository
import it.traccia.core.terrain.HeatmapTileHandler
import it.traccia.feature.heatmap.DataStoreHeatmapSettings
import it.traccia.feature.heatmap.Heatmap
import it.traccia.feature.heatmap.HeatmapRepository
import it.traccia.feature.heatmap.HeatmapSettingsRepository
import it.traccia.feature.heatmap.cache.HeatmapTileCache
import it.traccia.feature.heatmap.cache.SqliteHeatmapCache
import it.traccia.feature.heatmap.config.HeatmapConfig
import it.traccia.feature.heatmap.session.AndroidWebCookies
import it.traccia.feature.heatmap.session.CookieCipher
import it.traccia.feature.heatmap.session.KeystoreCookieCipher
import it.traccia.feature.heatmap.session.StravaSession
import it.traccia.feature.heatmap.session.WebCookies
import it.traccia.feature.heatmap.tiles.HeatmapTileService
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HeatmapDataStore

@Module
@InstallIn(SingletonComponent::class)
internal abstract class HeatmapModule {
    @Binds
    abstract fun bindSettings(impl: DataStoreHeatmapSettings): HeatmapSettingsRepository

    @Binds
    abstract fun bindCipher(impl: KeystoreCookieCipher): CookieCipher

    @Binds
    abstract fun bindHeatmap(impl: HeatmapRepository): Heatmap

    @Binds
    abstract fun bindWebCookies(impl: AndroidWebCookies): WebCookies

    companion object {
        @Provides
        @Singleton
        fun provideConfig(
            @ApplicationContext context: Context,
        ): HeatmapConfig = HeatmapConfig.fromAssets(context)

        /** Session and cache live in no-backup storage: never part of backups or device transfers. */
        @Provides
        @Singleton
        fun provideSession(
            @ApplicationContext context: Context,
            config: HeatmapConfig,
            cipher: CookieCipher,
        ): StravaSession = StravaSession(config, File(context.noBackupFilesDir, "heatmap/session.bin"), cipher)

        @Provides
        @Singleton
        fun provideCache(
            @ApplicationContext context: Context,
        ): HeatmapTileCache = SqliteHeatmapCache(File(context.noBackupFilesDir, "heatmap/cache"))

        @Provides
        @Singleton
        fun provideTileHandler(
            config: HeatmapConfig,
            cache: HeatmapTileCache,
            session: StravaSession,
            preferences: UserPreferencesRepository,
        ): HeatmapTileHandler =
            HeatmapTileService(
                config,
                cache,
                session,
                refreshAfterDays = { preferences.preferences.value.heatmapRefreshDays ?: config.refreshAfterDays },
            )

        @Provides
        @Singleton
        @HeatmapDataStore
        fun provideDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("heatmap") }
    }
}
