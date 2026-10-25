package it.traccia.core.terrain.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.traccia.core.storage.offline.OfflineTileStore
import it.traccia.core.terrain.AndroidTerrariumDecoder
import it.traccia.core.terrain.DemTileDecoder
import it.traccia.core.terrain.ElevationRepository
import it.traccia.core.terrain.ElevationSource
import it.traccia.core.terrain.HeatmapTileHandler
import it.traccia.core.terrain.MapSources
import it.traccia.core.terrain.TileRouter
import java.io.IOException
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TerrainModule {
    @Binds
    abstract fun bindDecoder(impl: AndroidTerrariumDecoder): DemTileDecoder

    @Binds
    abstract fun bindElevationSource(impl: ElevationRepository): ElevationSource

    companion object {
        @Provides
        @Singleton
        fun provideMapSources(
            @ApplicationContext context: Context,
        ): MapSources = MapSources.fromAssets(context)

        @Provides
        @Singleton
        fun provideTileRouter(
            @ApplicationContext context: Context,
            sources: MapSources,
            localTiles: OfflineTileStore,
            heatmap: HeatmapTileHandler,
        ): TileRouter =
            TileRouter(sources, localTiles, heatmap) { fontStack, range ->
                try {
                    context.assets.open("fonts/$fontStack/$range.pbf").use { it.readBytes() }
                } catch (_: IOException) {
                    // Not bundled: the router fetches it online.
                    null
                }
            }
    }
}
