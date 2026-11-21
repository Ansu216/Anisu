package eu.kanade.tachiyomi.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Cookie jar shared with the system WebView, like Aniyomi's. */
class AndroidCookieJar : CookieJar {

    private val manager: CookieManager? by lazy { runCatching { CookieManager.getInstance() }.getOrNull() }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val target = manager ?: return
        val urlString = url.toString()
        cookies.forEach { target.setCookie(urlString, it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = get(url)

    fun get(url: HttpUrl): List<Cookie> {
        val raw = manager?.getCookie(url.toString())
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(";").mapNotNull { Cookie.parse(url, it) }
    }

    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = -1): Int {
        val target = manager ?: return 0
        val urlString = url.toString()
        val names = get(url).map { it.name }.filter { cookieNames == null || it in cookieNames }
        names.forEach { target.setCookie(urlString, "$it=;Max-Age=$maxAge") }
        return names.size
    }

    fun removeAll() {
        manager?.removeAllCookies(null)
    }
}
