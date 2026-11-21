package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/** Delays requests so that no more than [permits] go out per [period]; optionally only for one [host]. */
class RateLimitInterceptor(
    private val host: String?,
    private val permits: Int,
    period: Long,
    unit: TimeUnit,
) : Interceptor {

    private val periodMs = unit.toMillis(period)
    private val timestamps = java.util.ArrayDeque<Long>()
    private val lock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (host != null && request.url.host != host) return chain.proceed(request)
        if (permits > 0 && periodMs > 0) acquire()
        return chain.proceed(request)
    }

    private fun acquire() {
        synchronized(lock) {
            while (true) {
                val now = System.currentTimeMillis()
                while (timestamps.isNotEmpty() && now - timestamps.peekFirst()!! >= periodMs) timestamps.pollFirst()
                if (timestamps.size < permits) {
                    timestamps.addLast(now)
                    return
                }
                val wait = periodMs - (now - timestamps.peekFirst()!!)
                try {
                    Thread.sleep(wait.coerceAtLeast(1))
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Interrupted while waiting for the rate limit", e)
                }
            }
        }
    }
}

fun OkHttpClient.Builder.rateLimit(permits: Int, period: Long = 1, unit: TimeUnit = TimeUnit.SECONDS): OkHttpClient.Builder =
    addInterceptor(RateLimitInterceptor(null, permits, period, unit))

fun OkHttpClient.Builder.rateLimit(permits: Int, period: Duration): OkHttpClient.Builder =
    addInterceptor(RateLimitInterceptor(null, permits, period.toJavaDuration().toMillis(), TimeUnit.MILLISECONDS))
