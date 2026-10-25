package it.traccia.core.terrain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import javax.inject.Inject
import javax.inject.Singleton

/** Point elevation lookup, main-safe and cancellable. */
fun interface ElevationSource {
    /** Elevation in meters, or null when no DEM data is available for the point. */
    suspend fun elevationAt(
        latitude: Double,
        longitude: Double,
    ): Double?
}

/** Terrain queries backed by the online DEM (and, from M2, by offline DEM files). */
@Singleton
class ElevationRepository
    @Inject
    constructor(
        repository: DemTileRepository,
    ) : ElevationSource {
        val sampler = TerrainSampler(repository.tileSize, repository.maxZoom, repository::tile)

        override suspend fun elevationAt(
            latitude: Double,
            longitude: Double,
        ): Double? = runInterruptible(Dispatchers.IO) { sampler.elevationAt(latitude, longitude) }
    }
