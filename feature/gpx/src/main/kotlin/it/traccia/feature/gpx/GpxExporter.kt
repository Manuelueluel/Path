package it.traccia.feature.gpx

import it.traccia.core.geo.GeoPoint
import it.traccia.core.storage.route.Route
import it.traccia.core.terrain.ElevationRepository
import it.traccia.feature.gpx.GpxConversion.simplifiedFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import javax.inject.Inject

data class ExportOptions(
    val content: GpxContent = GpxContent.TRACK,
    val simplification: Simplification = Simplification.NONE,
    /** Start, arrival and control points as waypoints. */
    val waypoints: Boolean = false,
)

/** Builds the GPX of a route, with elevations from the DEM (M7.1, M7.2). */
class GpxExporter
    @Inject
    constructor(
        private val terrain: ElevationRepository,
    ) {
        suspend fun gpx(
            route: Route,
            options: ExportOptions,
        ): String =
            runInterruptible(Dispatchers.IO) {
                val track = route.track.simplifiedFor(options.simplification).map(::withElevation)
                val controls = route.points.map(::withElevation)
                val waypoints =
                    if (options.waypoints) {
                        controls.mapIndexed { i, p -> GpxWaypoint(p, waypointName(i, controls.lastIndex)) }
                    } else {
                        emptyList()
                    }
                GpxWriter.write(route.name, route.description, track, controls, waypoints, options.content)
            }

        /** Points of the exported track for each simplification level, to show the trade-off. */
        fun pointCounts(route: Route): Map<Simplification, Int> {
            val track = route.track
            return Simplification.entries.associateWith { track.simplifiedFor(it).size }
        }

        private fun withElevation(p: GeoPoint): GeoPoint =
            GeoPoint(
                p.latitude,
                p.longitude,
                terrain.sampler.elevationAt(p.latitude, p.longitude),
            )

        private fun waypointName(
            index: Int,
            last: Int,
        ) = when (index) {
            0 -> "Partenza"
            last -> "Arrivo"
            else -> "Punto $index"
        }
    }
