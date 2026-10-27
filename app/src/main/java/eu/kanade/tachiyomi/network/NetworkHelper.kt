package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * The network access an extension source gets through `network.client`. Ansu builds it from its
 * own OkHttp client. When a [context] is given, a Cloudflare challenge (403/503 from Cloudflare) is solved
 * in a hidden WebView and the request repeated, like Aniyomi; `cloudflareClient` is the same client.
 */
class NetworkHelper(
    baseClient: OkHttpClient,
    context: Context? = null,
    /** Gets one line for every extension request that failed (HTTP error or no connection), for the diagnostics log. */
    private val onFailure: ((String) -> Unit)? = null,
) {

    val cookieJar = AndroidCookieJar()

    val client: OkHttpClient = baseClient.newBuilder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .addInterceptor(DefaultUserAgentInterceptor())
        .apply { if (onFailure != null) addInterceptor(FailureLogInterceptor(onFailure)) }
        .apply {
            if (context != null) addInterceptor(CloudflareInterceptor(context, cookieJar, ::defaultUserAgentProvider))
        }
        .build()

    val cloudflareClient: OkHttpClient
        get() = client

    fun defaultUserAgentProvider(): String = DEFAULT_USER_AGENT

    /**
     * Extensions often swallow their own errors and just return nothing. This writes down what actually went
     * wrong on the wire (status code or exception, host and path only, never the query) so a log can say why.
     */
    private class FailureLogInterceptor(private val report: (String) -> Unit) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val where = request.url.host + request.url.encodedPath.take(80)
            val response = try {
                chain.proceed(request)
            } catch (e: java.io.IOException) {
                report("  request failed: ${request.method} $where -> ${e::class.java.simpleName}: ${e.message?.take(100)}")
                throw e
            }
            if (!response.isSuccessful && response.code != 304) {
                report("  request failed: ${request.method} $where -> HTTP ${response.code}")
            }
            return response
        }
    }

    private class DefaultUserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.header("User-Agent") != null) return chain.proceed(request)
            return chain.proceed(request.newBuilder().header("User-Agent", DEFAULT_USER_AGENT).build())
        }
    }

    private companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
