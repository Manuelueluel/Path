package it.traccia.feature.heatmap.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import it.traccia.feature.heatmap.config.HeatmapConfig
import it.traccia.feature.heatmap.session.StravaCookies
import it.traccia.feature.heatmap.session.StravaSession
import it.traccia.feature.heatmap.session.WebCookies
import it.traccia.feature.heatmap.session.hasRequiredCookies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class StravaLoginUiState(
    /** URL the WebView must show; changes only when the app redirects it. */
    val url: String,
    val isLoading: Boolean = true,
    val isCompleted: Boolean = false,
)

/**
 * Login on strava.com inside a WebView, with the user's own account (M3.1). The app never sees
 * the password: it only reads the session cookies once Strava has set them.
 */
@HiltViewModel
class StravaLoginViewModel
    @Inject
    constructor(
        private val config: HeatmapConfig,
        private val session: StravaSession,
        private val webCookies: WebCookies,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(StravaLoginUiState(url = config.loginUrl))
        val uiState: StateFlow<StravaLoginUiState> = _uiState.asStateFlow()
        private var openedHeatmapPage = false

        fun onPageStarted() = _uiState.update { it.copy(isLoading = true) }

        fun onPageFinished(url: String) {
            _uiState.update { it.copy(isLoading = false) }
            if (_uiState.value.isCompleted) return
            val tileCookies = webCookies.get(config.tileOrigin)
            if (hasRequiredCookies(tileCookies, config.requiredCookies) && tileCookies != null) {
                session.save(StravaCookies(tileCookies, webCookies.get(config.sessionCookieUrl).orEmpty(), System.currentTimeMillis()))
                _uiState.update { it.copy(isCompleted = true) }
            } else if (isLoggedInPage(url) && !openedHeatmapPage) {
                // Logged in, but the heatmap cookies are only issued by the heatmap page.
                openedHeatmapPage = true
                _uiState.update { it.copy(url = config.heatmapPageUrl) }
            }
        }

        private fun isLoggedInPage(url: String): Boolean =
            url.startsWith(config.sessionCookieUrl) && LOGIN_PATHS.none { url.contains(it) }

        private companion object {
            val LOGIN_PATHS = listOf("/login", "/session", "/register", "/oauth", "/password")
        }
    }
