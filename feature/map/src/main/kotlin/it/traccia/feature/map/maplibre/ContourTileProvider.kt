package it.traccia.feature.map.maplibre

import it.traccia.core.common.catching
import it.traccia.core.common.getOrNull
import it.traccia.core.common.onFailure
import it.traccia.core.terrain.ElevationRepository
import it.traccia.core.terrain.GeoContour
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.log.Logger
import org.maplibre.android.style.sources.GeometryTileProvider
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import javax.inject.Inject

/**
 * Feeds MapLibre's custom geometry source with contour lines computed from the DEM.
 * Called on MapLibre worker threads, so blocking DEM downloads are acceptable here.
 */
class ContourTileProvider
    @Inject
    constructor(
        private val terrain: ElevationRepository,
    ) : GeometryTileProvider {
        override fun getFeaturesForBounds(
            bounds: LatLngBounds,
            zoomLevel: Int,
        ): FeatureCollection {
            val contours =
                catching {
                    terrain.sampler.contours(
                        west = bounds.longitudeWest,
                        south = bounds.latitudeSouth,
                        east = bounds.longitudeEast,
                        north = bounds.latitudeNorth,
                        zoom = zoomLevel,
                    )
                }.onFailure { Logger.e(TAG, "Contours failed for z$zoomLevel $bounds", it.cause) }
            val lines = contours.getOrNull().orEmpty()
            return FeatureCollection.fromFeatures(lines.map(::toFeature))
        }

        private fun toFeature(contour: GeoContour): Feature {
            val points = List(contour.lonLat.size / 2) { Point.fromLngLat(contour.lonLat[it * 2], contour.lonLat[it * 2 + 1]) }
            return Feature.fromGeometry(LineString.fromLngLats(points)).apply {
                addNumberProperty(PROP_ELEVATION, contour.elevation.toInt())
                addBooleanProperty(PROP_MAJOR, contour.isMajor)
            }
        }

        companion object {
            const val PROP_ELEVATION = "ele"
            const val PROP_MAJOR = "major"
            private const val TAG = "ContourTileProvider"
        }
    }
