package it.traccia.feature.heatmap.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class StravaCookiesTest {
    private val required = listOf("CloudFront-Key-Pair-Id", "CloudFront-Policy", "CloudFront-Signature")

    /** Encodes a policy the way CloudFront does (base64 with -, _ and ~). */
    private fun policy(epochSeconds: Long): String {
        val json = """{"Statement":[{"Resource":"https://*.strava.com/*","Condition":{"DateLessThan":{"AWS:EpochTime":$epochSeconds}}}]}"""
        return Base64
            .getEncoder()
            .encodeToString(json.toByteArray())
            .replace('+', '-')
            .replace('=', '_')
            .replace('/', '~')
    }

    @Test
    fun `parses a cookie header`() {
        assertEquals(mapOf("a" to "1", "b" to "x=y"), parseCookieHeader("a=1; b=x=y; broken"))
    }

    @Test
    fun `login is complete only with all CloudFront cookies`() {
        assertTrue(hasRequiredCookies("CloudFront-Key-Pair-Id=K; CloudFront-Policy=P; CloudFront-Signature=S; other=1", required))
        assertFalse(hasRequiredCookies("CloudFront-Key-Pair-Id=K; CloudFront-Policy=P", required))
        assertFalse(hasRequiredCookies("CloudFront-Key-Pair-Id=; CloudFront-Policy=P; CloudFront-Signature=S", required))
        assertFalse(hasRequiredCookies(null, required))
    }

    @Test
    fun `reads the expiry from the CloudFront policy`() {
        assertEquals(1_793_000_000_000L, cloudFrontExpiryMillis(policy(1_793_000_000)))
    }

    @Test
    fun `unreadable policy gives no expiry`() {
        assertNull(cloudFrontExpiryMillis("not-base64-!!"))
        assertNull(cloudFrontExpiryMillis(Base64.getEncoder().encodeToString("{}".toByteArray())))
        assertNull(cloudFrontExpiryMillis(null))
    }
}
