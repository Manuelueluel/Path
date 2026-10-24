package it.traccia.core.geo

/** A WGS84 coordinate, with optional elevation in meters. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
) {
    init {
        require(latitude in -MAX_LATITUDE..MAX_LATITUDE) { "Invalid latitude: $latitude" }
        require(longitude in -MAX_LONGITUDE..MAX_LONGITUDE) { "Invalid longitude: $longitude" }
    }
}

private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0
