package it.traccia.core.storage.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import it.traccia.core.common.SpeedReference
import it.traccia.core.common.Sport
import it.traccia.core.common.UnitSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** App-wide preferences edited in the settings screen (M8.1, M8.2). */
data class UserPreferences(
    val units: UnitSystem = UnitSystem.METRIC,
    val defaultSport: Sport = Sport.HIKING,
    /** Days after which a cached heatmap tile is downloaded again; null = value of heatmap.json. */
    val heatmapRefreshDays: Int? = null,
    /** Only the sports the user changed; the others use [SpeedReference.defaultFor]. */
    val speeds: Map<Sport, SpeedReference> = emptyMap(),
    val onboardingDone: Boolean = false,
) {
    fun speedFor(sport: Sport): SpeedReference = speeds[sport] ?: SpeedReference.defaultFor(sport)
}

interface UserPreferencesRepository {
    /** Always up to date; starts from the defaults until the file is read. */
    val preferences: StateFlow<UserPreferences>

    /** The stored values, waiting for the file to be read (e.g. to decide whether to show onboarding). */
    suspend fun load(): UserPreferences

    suspend fun update(transform: (UserPreferences) -> UserPreferences)
}

class DataStoreUserPreferences(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : UserPreferencesRepository {
    private val flow: Flow<UserPreferences> = dataStore.data.map { it.toUserPreferences() }

    override val preferences: StateFlow<UserPreferences> = flow.stateIn(scope, SharingStarted.Eagerly, UserPreferences())

    override suspend fun load(): UserPreferences = flow.first()

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toUserPreferences())) }
    }

    private fun Preferences.toUserPreferences(): UserPreferences =
        UserPreferences(
            units = UnitSystem.entries.firstOrNull { it.name == this[UNITS] } ?: UnitSystem.METRIC,
            defaultSport = Sport.entries.firstOrNull { it.name == this[DEFAULT_SPORT] } ?: Sport.HIKING,
            heatmapRefreshDays = this[HEATMAP_REFRESH_DAYS],
            speeds =
                Sport.entries
                    .mapNotNull { sport ->
                        val flat = this[flatKey(sport)] ?: return@mapNotNull null
                        val ascent = this[ascentKey(sport)] ?: return@mapNotNull null
                        val descent = this[descentKey(sport)] ?: return@mapNotNull null
                        sport to SpeedReference(flat, ascent, descent)
                    }.toMap(),
            onboardingDone = this[ONBOARDING_DONE] ?: false,
        )

    private fun MutablePreferences.write(p: UserPreferences) {
        this[UNITS] = p.units.name
        this[DEFAULT_SPORT] = p.defaultSport.name
        val days = p.heatmapRefreshDays
        if (days == null) remove(HEATMAP_REFRESH_DAYS) else this[HEATMAP_REFRESH_DAYS] = days
        Sport.entries.forEach { sport ->
            val speed = p.speeds[sport]
            if (speed == null) {
                remove(flatKey(sport))
                remove(ascentKey(sport))
                remove(descentKey(sport))
            } else {
                this[flatKey(sport)] = speed.flatKmh
                this[ascentKey(sport)] = speed.ascentMh
                this[descentKey(sport)] = speed.descentMh
            }
        }
        this[ONBOARDING_DONE] = p.onboardingDone
    }

    private companion object {
        val UNITS = stringPreferencesKey("units")
        val DEFAULT_SPORT = stringPreferencesKey("default_sport")
        val HEATMAP_REFRESH_DAYS = intPreferencesKey("heatmap_refresh_days")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

        fun flatKey(sport: Sport) = doublePreferencesKey("speed_${sport.name}_flat")

        fun ascentKey(sport: Sport) = doublePreferencesKey("speed_${sport.name}_ascent")

        fun descentKey(sport: Sport) = doublePreferencesKey("speed_${sport.name}_descent")
    }
}

/** Preferences kept in memory: for tests and previews. */
class InMemoryUserPreferences(
    initial: UserPreferences = UserPreferences(),
) : UserPreferencesRepository {
    private val state = MutableStateFlow(initial)

    override val preferences: StateFlow<UserPreferences> = state.asStateFlow()

    override suspend fun load(): UserPreferences = state.value

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) = state.update(transform)
}
