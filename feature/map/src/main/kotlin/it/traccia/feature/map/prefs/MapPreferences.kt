package it.traccia.feature.map.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import it.traccia.feature.map.di.MapDataStore
import it.traccia.feature.map.style.MapStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Camera position to restore when the map is reopened. */
data class CameraSnapshot(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val bearing: Double = 0.0,
)

data class MapPreferences(
    val style: MapStyle = MapStyle.TOPO,
    val contoursVisible: Boolean = true,
    val hillshadeVisible: Boolean = true,
    val camera: CameraSnapshot = DEFAULT_CAMERA,
) {
    companion object {
        /** Resegone / Lecco area: first-run test zone. */
        val DEFAULT_CAMERA = CameraSnapshot(latitude = 45.857, longitude = 9.48, zoom = 12.5)
    }
}

interface MapPreferencesRepository {
    val preferences: Flow<MapPreferences>

    suspend fun setStyle(style: MapStyle)

    suspend fun setContoursVisible(visible: Boolean)

    suspend fun setHillshadeVisible(visible: Boolean)

    suspend fun saveCamera(camera: CameraSnapshot)
}

internal class DataStoreMapPreferencesRepository
    @Inject
    constructor(
        @param:MapDataStore private val dataStore: DataStore<Preferences>,
    ) : MapPreferencesRepository {
        override val preferences: Flow<MapPreferences> = dataStore.data.map { it.toMapPreferences() }

        override suspend fun setStyle(style: MapStyle) {
            dataStore.edit { it[Keys.STYLE] = style.name }
        }

        override suspend fun setContoursVisible(visible: Boolean) {
            dataStore.edit { it[Keys.CONTOURS] = visible }
        }

        override suspend fun setHillshadeVisible(visible: Boolean) {
            dataStore.edit { it[Keys.HILLSHADE] = visible }
        }

        override suspend fun saveCamera(camera: CameraSnapshot) {
            dataStore.edit {
                it[Keys.LAT] = camera.latitude
                it[Keys.LON] = camera.longitude
                it[Keys.ZOOM] = camera.zoom
                it[Keys.BEARING] = camera.bearing
            }
        }

        private fun Preferences.toMapPreferences(): MapPreferences {
            val defaults = MapPreferences()
            val lat = this[Keys.LAT]
            val lon = this[Keys.LON]
            val zoom = this[Keys.ZOOM]
            return MapPreferences(
                style = MapStyle.entries.firstOrNull { it.name == this[Keys.STYLE] } ?: defaults.style,
                contoursVisible = this[Keys.CONTOURS] ?: defaults.contoursVisible,
                hillshadeVisible = this[Keys.HILLSHADE] ?: defaults.hillshadeVisible,
                camera =
                    if (lat != null && lon != null && zoom != null) {
                        CameraSnapshot(lat, lon, zoom, this[Keys.BEARING] ?: 0.0)
                    } else {
                        defaults.camera
                    },
            )
        }

        private object Keys {
            val STYLE = stringPreferencesKey("style")
            val CONTOURS = booleanPreferencesKey("contours")
            val HILLSHADE = booleanPreferencesKey("hillshade")
            val LAT = doublePreferencesKey("camera_lat")
            val LON = doublePreferencesKey("camera_lon")
            val ZOOM = doublePreferencesKey("camera_zoom")
            val BEARING = doublePreferencesKey("camera_bearing")
        }
    }
