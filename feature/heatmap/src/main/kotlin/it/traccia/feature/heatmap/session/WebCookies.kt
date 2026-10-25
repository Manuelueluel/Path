package it.traccia.feature.heatmap.session

import android.webkit.CookieManager
import javax.inject.Inject

/** Cookies of the login WebView (M3.1, M3.13). */
interface WebCookies {
    /** `Cookie` header the WebView would send to [url], or null. */
    fun get(url: String): String?

    fun clear()
}

internal class AndroidWebCookies
    @Inject
    constructor() : WebCookies {
        override fun get(url: String): String? = CookieManager.getInstance().getCookie(url)

        override fun clear() {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }
