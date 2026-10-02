package eu.kanade.tachiyomi.network.interceptor

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.toJavaDuration

fun OkHttpClient.Builder.rateLimitHost(
    httpUrl: HttpUrl,
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS,
): OkHttpClient.Builder = addInterceptor(RateLimitInterceptor(httpUrl.host, permits, period, unit))

fun OkHttpClient.Builder.rateLimitHost(
    url: String,
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS,
): OkHttpClient.Builder = rateLimitHost(url.toHttpUrl(), permits, period, unit)

fun OkHttpClient.Builder.rateLimitHost(httpUrl: HttpUrl, permits: Int, period: Duration): OkHttpClient.Builder =
    addInterceptor(RateLimitInterceptor(httpUrl.host, permits, period.toJavaDuration().toMillis(), TimeUnit.MILLISECONDS))
