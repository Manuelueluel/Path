package it.traccia.feature.heatmap.session

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.Base64

/**
 * Session cookies read from the login WebView. They are the only thing the app keeps:
 * never the user's credentials.
 */
@Serializable
data class StravaCookies(
    /** `Cookie` header for the tile host (CloudFront signed cookies). */
    val tileCookies: String,
    /** `Cookie` header for strava.com, used to renew the tile cookies while the session lasts. */
    val sessionCookies: String,
    val savedAtMillis: Long,
)

/** Parses a `Cookie` header (`a=1; b=2`) into a map. */
fun parseCookieHeader(header: String): Map<String, String> =
    header
        .split(';')
        .map { it.trim() }
        .filter { it.contains('=') }
        .associate { it.substringBefore('=') to it.substringAfter('=') }

/** True when every [required] cookie is present and not empty. */
fun hasRequiredCookies(
    header: String?,
    required: List<String>,
): Boolean {
    if (header.isNullOrBlank()) return false
    val cookies = parseCookieHeader(header)
    return required.all { !cookies[it].isNullOrEmpty() }
}

/**
 * Expiry of CloudFront signed cookies, read from the policy (base64 JSON, CloudFront-safe alphabet:
 * `-` for `+`, `_` for `=`, `~` for `/`). Null when the policy cannot be read: the app then relies on
 * the server answering 403.
 */
fun cloudFrontExpiryMillis(policyCookie: String?): Long? {
    if (policyCookie.isNullOrBlank()) return null
    return try {
        val base64 = policyCookie.replace('-', '+').replace('_', '=').replace('~', '/')
        val policy = Json.parseToJsonElement(String(Base64.getDecoder().decode(base64)))
        val epochSeconds =
            policy.jsonObject["Statement"]
                ?.let { (it as? JsonArray)?.firstOrNull() }
                ?.jsonObject
                ?.get("Condition")
                ?.jsonObject
                ?.get("DateLessThan")
                ?.jsonObject
                ?.get("AWS:EpochTime")
                ?.jsonPrimitive
                ?.long
        epochSeconds?.let { it * MILLIS_PER_SECOND }
    } catch (_: IllegalArgumentException) {
        // Not base64 or not JSON (kotlinx serialization errors are IllegalArgumentException too).
        null
    }
}

private const val MILLIS_PER_SECOND = 1000L
