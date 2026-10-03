package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.AndroidCookieJar
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Solves Cloudflare's anti-bot challenge the way Aniyomi does: when a response is a 403/503 served by
 * Cloudflare, the page is opened in a hidden WebView until it earns a fresh `cf_clearance` cookie (the
 * cookie jar is shared with the WebView), then the original request is repeated.
 */
class CloudflareInterceptor(
    context: Context,
    private val cookieJar: AndroidCookieJar,
    private val defaultUserAgentProvider: () -> String,
) : Interceptor {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code !in ERROR_CODES || response.header("Server") !in SERVER_CHECK) return response

        // Already on the main thread (nothing in Ansu does network I/O there): blocking it would deadlock.
        if (Looper.myLooper() == Looper.getMainLooper()) return response

        return try {
            response.close()
            cookieJar.remove(request.url, COOKIE_NAMES, 0)
            val oldCookie = cookieJar.get(request.url).firstOrNull { it.name == CLEARANCE }
            resolveWithWebView(request, oldCookie)
            chain.proceed(request)
        } catch (e: CloudflareBypassException) {
            throw IOException("Cloudflare protection could not be bypassed; open the site in a browser and retry", e)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(originalRequest: Request, oldCookie: Cookie?) {
        val latch = CountDownLatch(1)
        var webView: WebView? = null
        var challengeFound = false
        var bypassed = false
        val origUrl = originalRequest.url.toString()
        val headers = safeHeaders(originalRequest.headers)

        mainHandler.post {
            try {
                webView = WebView(appContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                    settings.userAgentString = originalRequest.header("User-Agent") ?: defaultUserAgentProvider()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            val cleared = cookieJar.get(originalRequest.url)
                                .firstOrNull { it.name == CLEARANCE }
                                .let { it != null && it != oldCookie }
                            if (cleared) {
                                bypassed = true
                                latch.countDown()
                            }
                            // The first load did not hit a challenge at all: nothing to wait for.
                            if (url == origUrl && !challengeFound) latch.countDown()
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (request.isForMainFrame) {
                                if (error.errorCode in ERROR_CODES) challengeFound = true else latch.countDown()
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            errorResponse: android.webkit.WebResourceResponse,
                        ) {
                            if (request.isForMainFrame && errorResponse.statusCode in ERROR_CODES) challengeFound = true
                        }
                    }
                    loadUrl(origUrl, headers)
                }
            } catch (e: Throwable) {
                // No usable WebView (being updated, missing): give up instead of hanging for 30 seconds.
                latch.countDown()
            }
        }

        latch.await(30, TimeUnit.SECONDS)
        mainHandler.post {
            webView?.run {
                stopLoading()
                destroy()
            }
        }

        if (!bypassed) throw CloudflareBypassException()
    }

    /** Headers the WebView accepts; an unsafe one makes it fail with ERR_INVALID_ARGUMENT. */
    private fun safeHeaders(headers: Headers): Map<String, String> = headers
        .filter { (name, value) -> isSafe(name, value) }
        .groupBy(keySelector = { (name, _) -> name }) { (_, value) -> value }
        .mapValues { it.value.firstOrNull().orEmpty() }

    private fun isSafe(rawName: String, rawValue: String): Boolean {
        val name = rawName.lowercase(Locale.ENGLISH)
        val value = rawValue.lowercase(Locale.ENGLISH)
        if (name in UNSAFE_HEADERS || name.startsWith("proxy-")) return false
        if (name == "connection" && value == "upgrade") return false
        return true
    }

    private class CloudflareBypassException : Exception()

    private companion object {
        const val CLEARANCE = "cf_clearance"
        val ERROR_CODES = listOf(403, 503)
        val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
        val COOKIE_NAMES = listOf(CLEARANCE)
        val UNSAFE_HEADERS = listOf(
            "content-length", "host", "trailer", "te", "upgrade", "cookie2", "keep-alive", "transfer-encoding",
            "set-cookie",
        )
    }
}
