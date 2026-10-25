package it.traccia.feature.gpx

import it.traccia.core.geo.GeoPoint

data class GpxWaypoint(
    val point: GeoPoint,
    val name: String?,
)

/** The content of a GPX file that matters for routes. */
data class GpxDocument(
    val name: String?,
    val description: String?,
    /** One list per `trk`, its `trkseg`s joined in order. */
    val tracks: List<List<GeoPoint>>,
    /** One list per `rte`. */
    val routes: List<List<GeoPoint>>,
    val waypoints: List<GpxWaypoint>,
) {
    /** The line to import: the longest track, or the longest route when there are no tracks. */
    val mainLine: List<GeoPoint>?
        get() = (tracks.ifEmpty { routes }).maxByOrNull { it.size }?.takeIf { it.size >= 2 }
}
