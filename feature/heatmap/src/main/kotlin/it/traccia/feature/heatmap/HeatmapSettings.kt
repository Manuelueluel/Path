package it.traccia.feature.heatmap

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import it.traccia.feature.heatmap.di.HeatmapDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** How the heatmap overlay is shown on the map (M3.5, M3.6). */
data class HeatmapSettings(
    val visible: Boolean = false,
    val sport: String = DEFAULT_SPORT,
    val color: String = DEFAULT_COLOR,
    val opacity: Float = DEFAULT_OPACITY,
) {
    companion object {
        const val DEFAULT_SPORT = "all"
        const val DEFAULT_COLOR = "hot"
        const val DEFAULT_OPACITY = 0.7f
    }
}

interface HeatmapSettingsRepository {
    val settings: Flow<HeatmapSettings>

    suspend fun update(transform: (HeatmapSettings) -> HeatmapSettings)
}

internal class DataStoreHeatmapSettings
    @Inject
    constructor(
        @param:HeatmapDataStore private val dataStore: DataStore<Preferences>,
    ) : HeatmapSettingsRepository {
        override val settings: Flow<HeatmapSettings> =
            dataStore.data.map {
                HeatmapSettings(
                    visible = it[VISIBLE] ?: false,
                    sport = it[SPORT] ?: HeatmapSettings.DEFAULT_SPORT,
                    color = it[COLOR] ?: HeatmapSettings.DEFAULT_COLOR,
                    opacity = it[OPACITY] ?: HeatmapSettings.DEFAULT_OPACITY,
                )
            }

        override suspend fun update(transform: (HeatmapSettings) -> HeatmapSettings) {
            dataStore.edit { prefs ->
                val current =
                    HeatmapSettings(
                        prefs[VISIBLE] ?: false,
                        prefs[SPORT] ?: HeatmapSettings.DEFAULT_SPORT,
                        prefs[COLOR] ?: HeatmapSettings.DEFAULT_COLOR,
                        prefs[OPACITY] ?: HeatmapSettings.DEFAULT_OPACITY,
                    )
                val next = transform(current)
                prefs[VISIBLE] = next.visible
                prefs[SPORT] = next.sport
                prefs[COLOR] = next.color
                prefs[OPACITY] = next.opacity
            }
        }

        private companion object {
            val VISIBLE = booleanPreferencesKey("visible")
            val SPORT = stringPreferencesKey("sport")
            val COLOR = stringPreferencesKey("color")
            val OPACITY = floatPreferencesKey("opacity")
        }
    }
