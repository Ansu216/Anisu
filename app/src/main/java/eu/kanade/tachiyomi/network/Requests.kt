package eu.kanade.tachiyomi.network

import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit.MINUTES

private val DEFAULT_CACHE_CONTROL = CacheControl.Builder().maxAge(10, MINUTES).build()
private val DEFAULT_HEADERS = Headers.Builder().build()
private val DEFAULT_BODY: RequestBody = FormBody.Builder().build()

@JvmOverloads
fun GET(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = Request.Builder().url(url).headers(headers).cacheControl(cache).build()

@JvmOverloads
fun GET(
    url: HttpUrl,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = Request.Builder().url(url).headers(headers).cacheControl(cache).build()

fun POST(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = Request.Builder().url(url).post(body).headers(headers).cacheControl(cache).build()

fun PUT(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = Request.Builder().url(url).put(body).headers(headers).cacheControl(cache).build()

fun DELETE(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = Request.Builder().url(url).delete(body).headers(headers).cacheControl(cache).build()

/**
 * Suspend shortcut newer extensions call as `client.get(url, headers)`. It must live in this file as a
 * top-level function with default arguments: the extension's bytecode links against the generated
 * `RequestsKt.get$default(OkHttpClient, String, Headers, CacheControl, Continuation, int, Object)`, and a
 * missing method fails with NoSuchMethodError as soon as the extension lists episodes.
 */
suspend fun OkHttpClient.get(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response = newCall(GET(url, headers, cache)).awaitSuccess()

/**
 * The same helper for extensions that pass an [HttpUrl] (for example Anikoto). A separate overload is needed
 * because the extension links against `RequestsKt.get$default(OkHttpClient, HttpUrl, Headers, CacheControl,
 * Continuation, int, Object)`, which is a different method from the `String` one.
 */
suspend fun OkHttpClient.get(
    url: HttpUrl,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response = newCall(GET(url, headers, cache)).awaitSuccess()
