package eu.kanade.tachiyomi.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Response
import rx.Observable
import rx.subscriptions.Subscriptions
import uy.kohesive.injekt.injectLazy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val json: Json by injectLazy()

/**
 * Runs the call with the blocking `execute()` on the IO dispatcher instead of `enqueue()`.
 * With `enqueue`, an Error raised inside an interceptor (NoSuchMethodError, NoClassDefFoundError from a
 * mismatched extension) is rethrown by OkHttp on its dispatcher thread, which nobody catches and which
 * kills the whole app. Here it comes back as an ordinary failure of the caller.
 */
suspend fun Call.await(): Response = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { runCatching { cancel() } }
        try {
            continuation.resume(execute())
        } catch (t: Throwable) {
            if (!isCanceled()) continuation.resumeWithException(t)
        }
    }
}

suspend fun Call.awaitSuccess(): Response {
    val response = await()
    if (!response.isSuccessful) {
        response.close()
        throw HttpException(response.code)
    }
    return response
}

/** Runs the call when subscribed to, on the subscribing thread (Ansu subscribes from a background dispatcher). */
fun Call.asObservable(): Observable<Response> = Observable.unsafeCreate { subscriber ->
    val call = clone()
    subscriber.add(Subscriptions.create { call.cancel() })
    try {
        val response = call.execute()
        if (!subscriber.isUnsubscribed) {
            subscriber.onNext(response)
            subscriber.onCompleted()
        } else {
            response.close()
        }
    } catch (e: Exception) {
        if (!subscriber.isUnsubscribed) subscriber.onError(e)
    }
}

fun Call.asObservableSuccess(): Observable<Response> = asObservable().doOnNext { response ->
    if (!response.isSuccessful) {
        response.close()
        throw HttpException(response.code)
    }
}

inline fun <reified T> Response.parseAs(): T = decodeFromJsonResponse(serializer(), this)

fun <T> decodeFromJsonResponse(deserializer: DeserializationStrategy<T>, response: Response): T =
    response.use { json.decodeFromString(deserializer, it.body!!.string()) }
