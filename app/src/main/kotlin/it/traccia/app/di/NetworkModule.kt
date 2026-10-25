package it.traccia.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.traccia.app.BuildConfig
import it.traccia.core.terrain.MapSources
import it.traccia.core.terrain.TileRouter
import it.traccia.feature.offlinemaps.catalog.CatalogUrl
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val HTTP_CACHE_BYTES = 200L * 1024 * 1024
    private const val TIMEOUT_SECONDS = 20L

    /** Single HTTP client for the app and MapLibre, sharing the disk cache used when offline. */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        tileRouter: TileRouter,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .cache(Cache(File(context.cacheDir, "http"), HTTP_CACHE_BYTES))
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", "Path/${BuildConfig.VERSION_NAME} (Android; open source route planner)")
                        .build(),
                )
            }
            // Resolves the virtual tile URLs to offline packs or online sources (M2.7).
            .addInterceptor(tileRouter)
            .build()

    @Provides
    @CatalogUrl
    fun provideCatalogUrl(sources: MapSources): String = BuildConfig.CATALOG_URL.ifBlank { sources.catalogUrl }
}
