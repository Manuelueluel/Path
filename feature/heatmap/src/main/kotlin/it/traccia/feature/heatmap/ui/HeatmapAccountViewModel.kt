package it.traccia.feature.heatmap.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.traccia.core.storage.prefs.UserPreferencesRepository
import it.traccia.feature.heatmap.cache.HeatmapTileCache
import it.traccia.feature.heatmap.config.HeatmapConfig
import it.traccia.feature.heatmap.session.SessionState
import it.traccia.feature.heatmap.session.StravaSession
import it.traccia.feature.heatmap.session.WebCookies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SportCache(
    val sport: String,
    val bytes: Long,
)

data class HeatmapAccountUiState(
    val session: SessionState = SessionState.LoggedOut,
    val caches: List<SportCache> = emptyList(),
    val refreshAfterDays: Int = 0,
) {
    val totalBytes: Long get() = caches.sumOf { it.bytes }
}

/** Account and cache management (M3.12, M3.13). */
@HiltViewModel
class HeatmapAccountViewModel
    @Inject
    constructor(
        private val config: HeatmapConfig,
        private val session: StravaSession,
        private val cache: HeatmapTileCache,
        private val webCookies: WebCookies,
        private val preferences: UserPreferencesRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HeatmapAccountUiState(refreshAfterDays = config.refreshAfterDays))
        val uiState: StateFlow<HeatmapAccountUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch { session.state.collect { s -> _uiState.update { it.copy(session = s) } } }
            viewModelScope.launch {
                preferences.preferences.collect { p ->
                    _uiState.update { it.copy(refreshAfterDays = p.heatmapRefreshDays ?: config.refreshAfterDays) }
                }
            }
            reloadSizes()
        }

        fun reloadSizes() {
            viewModelScope.launch {
                val sizes = withContext(Dispatchers.IO) { cache.sizes() }
                _uiState.update { state ->
                    state.copy(
                        caches =
                            sizes
                                .filterValues { it > 0 }
                                .map { (sport, bytes) ->
                                    SportCache(sport, bytes)
                                }.sortedBy { it.sport },
                    )
                }
            }
        }

        fun clear(sport: String) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { cache.clear(sport) }
                reloadSizes()
            }
        }

        fun clearAll() {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { _uiState.value.caches.forEach { cache.clear(it.sport) } }
                reloadSizes()
            }
        }

        /** Forgets the session and the WebView cookies; the cache stays usable offline. */
        fun logout() {
            session.logout()
            webCookies.clear()
        }
    }
