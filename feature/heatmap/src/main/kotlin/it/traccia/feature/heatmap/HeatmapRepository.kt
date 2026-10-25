package it.traccia.feature.heatmap

import it.traccia.core.geo.WebMercator
import it.traccia.core.terrain.MapSources
import it.traccia.feature.heatmap.cache.CacheAge
import it.traccia.feature.heatmap.cache.HeatmapTileCache
import it.traccia.feature.heatmap.cache.TileRange
import it.traccia.feature.heatmap.config.HeatmapConfig
import it.traccia.feature.heatmap.session.SessionState
import it.traccia.feature.heatmap.session.StravaSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

/** A visible map area. */
data class MapArea(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
    val zoom: Double,
)

/** Raster source parameters for the map. */
data class HeatmapSource(
    val tilesUrl: String,
    val tileSize: Int,
    val minZoom: Int,
    val maxZoom: Int,
)

/** Everything the map needs from the heatmap feature. */
interface Heatmap {
    val settings: Flow<HeatmapSettings>
    val session: StateFlow<SessionState>
    val config: HeatmapConfig

    suspend fun updateSettings(transform: (HeatmapSettings) -> HeatmapSettings)

    fun source(settings: HeatmapSettings): HeatmapSource

    /** Age of the cached heatmap in [area] (M3.10). */
    suspend fun age(
        settings: HeatmapSettings,
        area: MapArea,
    ): CacheAge?

    /** Forces a fresh download of the tiles in [area] the next time they are shown (M3.9). */
    suspend fun refresh(
        settings: HeatmapSettings,
        area: MapArea,
    )
}

@Singleton
class HeatmapRepository
    @Inject
    constructor(
        override val config: HeatmapConfig,
        private val sources: MapSources,
        private val settingsRepository: HeatmapSettingsRepository,
        private val stravaSession: StravaSession,
        private val cache: HeatmapTileCache,
    ) : Heatmap {
        override val settings: Flow<HeatmapSettings> = settingsRepository.settings
        override val session: StateFlow<SessionState> = stravaSession.state

        override suspend fun updateSettings(transform: (HeatmapSettings) -> HeatmapSettings) = settingsRepository.update(transform)

        override fun source(settings: HeatmapSettings) =
            HeatmapSource(
                tilesUrl = sources.virtualHeatmapTiles(settings.sport, settings.color),
                tileSize = config.tileSize,
                minZoom = config.minZoom,
                maxZoom = config.maxZoom,
            )

        override suspend fun age(
            settings: HeatmapSettings,
            area: MapArea,
        ): CacheAge? = withContext(Dispatchers.IO) { cache.age(settings.sport, settings.color, tileRanges(area, config)) }

        override suspend fun refresh(
            settings: HeatmapSettings,
            area: MapArea,
        ) = withContext(Dispatchers.IO) { cache.markStale(settings.sport, settings.color, tileRanges(area, config)) }
    }

/**
 * Tile ranges MapLibre requests for [area]. MapLibre works with 512 px tiles: a 256 px raster source
 * is requested one zoom level higher. Both neighbouring levels are included to be safe.
 */
fun tileRanges(
    area: MapArea,
    config: HeatmapConfig,
): List<TileRange> {
    val offset = if (config.tileSize < MAPLIBRE_TILE) 1 else 0
    val base = floor(area.zoom).toInt() + offset
    return (base..base + 1)
        .map { it.coerceIn(config.minZoom, config.maxZoom) }
        .distinct()
        .map { z ->
            val world = (1 shl z).toDouble()
            val maxIndex = (1 shl z) - 1
            TileRange(
                zoom = z,
                minX = floor(WebMercator.lonToX(area.west, world)).toInt().coerceIn(0, maxIndex),
                maxX = floor(WebMercator.lonToX(area.east, world)).toInt().coerceIn(0, maxIndex),
                minY = floor(WebMercator.latToY(area.north, world)).toInt().coerceIn(0, maxIndex),
                maxY = floor(WebMercator.latToY(area.south, world)).toInt().coerceIn(0, maxIndex),
            )
        }
}

private const val MAPLIBRE_TILE = 512
