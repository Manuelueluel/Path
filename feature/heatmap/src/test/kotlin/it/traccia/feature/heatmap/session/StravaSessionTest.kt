package it.traccia.feature.heatmap.session

import it.traccia.feature.heatmap.bundledConfig
import okhttp3.Headers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class StravaSessionTest {
    @TempDir
    lateinit var dir: File

    /** Reversible fake: the real cipher needs the Android Keystore (covered by an instrumented test). */
    private val cipher =
        object : CookieCipher {
            override fun encrypt(plain: ByteArray) = plain.reversedArray()

            override fun decrypt(encrypted: ByteArray) = encrypted.reversedArray()
        }
    private var now = 1_000_000L

    private fun session() = StravaSession(bundledConfig, File(dir, "session.bin"), cipher) { now }

    private val cookies = StravaCookies("CloudFront-Key-Pair-Id=K; CloudFront-Policy=P; CloudFront-Signature=S", "_strava4_session=abc", 0)

    @Test
    fun `starts logged out`() {
        assertEquals(SessionState.LoggedOut, session().state.value)
        assertNull(session().tileCookies())
    }

    @Test
    fun `saved cookies survive a restart and are not stored in clear`() {
        session().save(cookies)
        assertFalse(String(File(dir, "session.bin").readBytes()).contains("CloudFront-Signature=S"))
        val restored = session()
        assertTrue(restored.state.value is SessionState.Active)
        assertEquals(cookies.tileCookies, restored.tileCookies())
    }

    @Test
    fun `rejected cookies are renewed with the strava session`() {
        val s = session().apply { save(cookies) }
        var sentCookies: String? = null
        val renewed =
            s.onUnauthorized { sent ->
                sentCookies = sent
                Headers.headersOf(
                    "Set-Cookie",
                    "CloudFront-Policy=P2; Path=/; Domain=.strava.com",
                    "Set-Cookie",
                    "CloudFront-Signature=S2; Path=/",
                    "Set-Cookie",
                    "CloudFront-Key-Pair-Id=K2",
                )
            }
        assertTrue(renewed)
        assertEquals("_strava4_session=abc", sentCookies)
        assertEquals(
            mapOf("CloudFront-Key-Pair-Id" to "K2", "CloudFront-Policy" to "P2", "CloudFront-Signature" to "S2"),
            parseCookieHeader(checkNotNull(s.tileCookies())),
        )
    }

    @Test
    fun `failed renewal expires the session but keeps it for a new login`() {
        val s = session().apply { save(cookies) }
        assertFalse(s.onUnauthorized { null })
        assertEquals(SessionState.Expired, s.state.value)
        assertNull(s.tileCookies())
    }

    @Test
    fun `renewal is not retried continuously`() {
        val s = session().apply { save(cookies) }
        var attempts = 0
        s.onUnauthorized {
            attempts++
            null
        }
        now += 60_000
        s.onUnauthorized {
            attempts++
            null
        }
        assertEquals(1, attempts)
    }

    @Test
    fun `logout forgets everything`() {
        val s = session().apply { save(cookies) }
        s.logout()
        assertEquals(SessionState.LoggedOut, s.state.value)
        assertFalse(File(dir, "session.bin").exists())
    }

    @Test
    fun `unreadable session file means logged out`() {
        File(dir, "session.bin").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(SessionState.LoggedOut, session().state.value)
    }
}
