package it.traccia.feature.heatmap.ui

import it.traccia.feature.heatmap.bundledConfig
import it.traccia.feature.heatmap.session.CookieCipher
import it.traccia.feature.heatmap.session.SessionState
import it.traccia.feature.heatmap.session.StravaSession
import it.traccia.feature.heatmap.session.WebCookies
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class StravaLoginViewModelTest {
    @TempDir
    lateinit var dir: File

    private val cookies = mutableMapOf<String, String>()
    private val webCookies =
        object : WebCookies {
            override fun get(url: String) = cookies[url]

            override fun clear() = cookies.clear()
        }
    private val plain =
        object : CookieCipher {
            override fun encrypt(plain: ByteArray) = plain

            override fun decrypt(encrypted: ByteArray) = encrypted
        }

    private fun setUp(): Pair<StravaLoginViewModel, StravaSession> {
        val session = StravaSession(bundledConfig, File(dir, "s.bin"), plain)
        return StravaLoginViewModel(bundledConfig, session, webCookies) to session
    }

    @Test
    fun `starts on the strava login page`() {
        assertEquals(
            bundledConfig.loginUrl,
            setUp()
                .first.uiState.value.url,
        )
    }

    @Test
    fun `login pages do not complete anything`() {
        val (vm, session) = setUp()
        vm.onPageFinished("https://www.strava.com/login")
        assertFalse(vm.uiState.value.isCompleted)
        assertEquals(bundledConfig.loginUrl, vm.uiState.value.url)
        assertEquals(SessionState.LoggedOut, session.state.value)
    }

    @Test
    fun `after login the heatmap page is opened once to get the tile cookies`() {
        val (vm, _) = setUp()
        vm.onPageFinished("https://www.strava.com/dashboard")
        assertEquals(bundledConfig.heatmapPageUrl, vm.uiState.value.url)
        vm.onPageFinished("https://www.strava.com/maps/global-heatmap")
        assertEquals(bundledConfig.heatmapPageUrl, vm.uiState.value.url)
    }

    @Test
    fun `tile cookies complete the login and are saved`() {
        val (vm, session) = setUp()
        cookies[bundledConfig.tileOrigin] = "CloudFront-Key-Pair-Id=K; CloudFront-Policy=P; CloudFront-Signature=S"
        cookies[bundledConfig.sessionCookieUrl] = "_strava4_session=abc"
        vm.onPageFinished("https://www.strava.com/maps/global-heatmap")
        assertTrue(vm.uiState.value.isCompleted)
        assertTrue(session.state.value is SessionState.Active)
        assertEquals(cookies[bundledConfig.tileOrigin], session.tileCookies())
    }
}
