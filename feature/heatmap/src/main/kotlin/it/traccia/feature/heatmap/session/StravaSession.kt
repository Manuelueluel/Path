package it.traccia.feature.heatmap.session

import android.util.Log
import it.traccia.feature.heatmap.config.HeatmapConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Headers
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException

sealed interface SessionState {
    data object LoggedOut : SessionState

    data class Active(
        /** Expiry of the tile cookies, when readable from the CloudFront policy. */
        val expiresAtMillis: Long?,
    ) : SessionState

    /** Cookies rejected or expired and not renewable: cached tiles only, a new login is suggested. */
    data object Expired : SessionState
}

/** What the tile service needs from the session. */
interface HeatmapSession {
    val state: StateFlow<SessionState>

    /** `Cookie` header for tile requests, or null without a usable session. */
    fun tileCookies(): String?

    /**
     * Strava rejected the cookies. [renew] performs `GET heatmapPageUrl` with the given `Cookie`
     * header and returns the response headers (null on failure): if Strava hands out fresh tile
     * cookies, the session continues; otherwise it becomes [SessionState.Expired].
     * @return true when the session was renewed and the request can be retried.
     */
    fun onUnauthorized(renew: (sessionCookies: String) -> Headers?): Boolean
}

/**
 * Keeps the Strava session cookies, encrypted on disk, and their state (M3.2, M3.11, M3.13).
 * Thread-safe: tile requests come from OkHttp threads.
 */
class StravaSession(
    private val config: HeatmapConfig,
    private val file: File,
    private val cipher: CookieCipher,
    private val clock: () -> Long = System::currentTimeMillis,
) : HeatmapSession {
    @Volatile
    private var cookies: StravaCookies? = load()
    private val _state = MutableStateFlow(stateOf(cookies))
    override val state: StateFlow<SessionState> = _state.asStateFlow()
    private var lastRenewalMillis = 0L

    /**
     * Cookies are sent even past their CloudFront expiry: the server's 403 is what triggers a renewal
     * through [onUnauthorized], which needs a first rejected request.
     */
    override fun tileCookies(): String? = cookies?.tileCookies?.takeIf { _state.value != SessionState.Expired }

    @Synchronized
    override fun onUnauthorized(renew: (sessionCookies: String) -> Headers?): Boolean {
        val current = cookies ?: return false
        val now = clock()
        if (now - lastRenewalMillis < RENEWAL_INTERVAL_MS) return _state.value is SessionState.Active
        lastRenewalMillis = now
        val fresh = renew(current.sessionCookies)?.let { renewedTileCookies(current, it) }
        return if (fresh != null) {
            save(current.copy(tileCookies = fresh, savedAtMillis = now))
            true
        } else {
            _state.value = SessionState.Expired
            false
        }
    }

    /** Stores the cookies captured by the login WebView. */
    @Synchronized
    fun save(newCookies: StravaCookies) {
        file.parentFile?.mkdirs()
        file.writeBytes(cipher.encrypt(json.encodeToString(StravaCookies.serializer(), newCookies).toByteArray()))
        cookies = newCookies
        _state.value = stateOf(newCookies)
    }

    /** Forgets the session (M3.13). The caller also clears the WebView cookies. */
    @Synchronized
    fun logout() {
        file.delete()
        cookies = null
        _state.value = SessionState.LoggedOut
    }

    private fun renewedTileCookies(
        current: StravaCookies,
        headers: Headers,
    ): String? {
        val set =
            headers
                .values("Set-Cookie")
                .map { it.substringBefore(';') }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
        if (!config.requiredCookies.all { !set[it].isNullOrEmpty() }) return null
        val merged = parseCookieHeader(current.tileCookies) + set.filterKeys { it in config.requiredCookies }
        return merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun stateOf(c: StravaCookies?): SessionState = if (c == null) SessionState.LoggedOut else SessionState.Active(expiryOf(c))

    private fun expiryOf(c: StravaCookies): Long? = cloudFrontExpiryMillis(parseCookieHeader(c.tileCookies)[config.policyCookie])

    private fun load(): StravaCookies? =
        if (!file.exists()) {
            null
        } else {
            try {
                json.decodeFromString(StravaCookies.serializer(), String(cipher.decrypt(file.readBytes())))
            } catch (e: GeneralSecurityException) {
                // Key lost (e.g. restored on another device): behave as logged out.
                Log.w(TAG, "Cannot decrypt the Strava session", e)
                null
            } catch (e: IOException) {
                Log.w(TAG, "Cannot read the Strava session", e)
                null
            } catch (e: SerializationException) {
                Log.w(TAG, "Corrupted Strava session", e)
                null
            }
        }

    private companion object {
        const val TAG = "StravaSession"
        const val RENEWAL_INTERVAL_MS = 10 * 60_000L
        val json = Json { ignoreUnknownKeys = true }
    }
}
