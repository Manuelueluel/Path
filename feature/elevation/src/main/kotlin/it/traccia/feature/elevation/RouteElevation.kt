package it.traccia.feature.elevation

import it.traccia.core.geo.GeoPoint
import it.traccia.core.terrain.ElevationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import javax.inject.Inject

/** Elevation profile of a route geometry, from the same DEM as the map (online or offline). */
fun interface RouteElevation {
    suspend fun profile(track: List<GeoPoint>): ElevationProfile?
}

class DemRouteElevation
    @Inject
    constructor(
        private val terrain: ElevationRepository,
    ) : RouteElevation {
        override suspend fun profile(track: List<GeoPoint>): ElevationProfile? =
            runInterruptible(Dispatchers.IO) { ProfileBuilder.build(track) { terrain.sampler.elevationAt(it.latitude, it.longitude) } }
    }
