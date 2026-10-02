package eu.kanade.tachiyomi.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * The network access an extension source gets through `network.client`. Ansu builds it from its
 * own OkHttp client. **Not implemented:** Cloudflare challenge solving, so `cloudflareClient` is
 * the plain client and a site behind a challenge will answer 403/503.
 */
class NetworkHelper(baseClient: OkHttpClient) {

    val cookieJar = AndroidCookieJar()

    val client: OkHttpClient = baseClient.newBuilder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .addInterceptor(DefaultUserAgentInterceptor())
        .build()

    val cloudflareClient: OkHttpClient
        get() = client

    fun defaultUserAgentProvider(): String = DEFAULT_USER_AGENT

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
