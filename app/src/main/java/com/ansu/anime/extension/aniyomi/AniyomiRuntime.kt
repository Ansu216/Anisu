package com.ansu.anime.extension.aniyomi

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sets up what an Aniyomi/Keiyoushi extension expects to find around it: the `eu.kanade.tachiyomi.*`
 * API classes (compiled into Ansu) and an Injekt container holding `Application`, `NetworkHelper`
 * `JavaScriptEngine` and `Json`, which is how those extensions reach them.
 */
object AniyomiRuntime {

    /**
     * First extension API version Ansu can run, and the last one. Version 16 replaced "episode -> videos"
     * with "episode -> hosters -> videos" and version 17 added the combined update calls; all of them are
     * implemented side by side in `eu.kanade.tachiyomi.animesource`.
     */
    const val MIN_LIB_VERSION = 12.0
    const val MAX_LIB_VERSION = 17.0

    @Volatile
    private var installed = false

    /** The client handed to [install]; kept so a retry reuses Ansu's own client. */
    @Volatile
    var sharedClient: OkHttpClient? = null
        private set

    /** Registers what extensions look up through Injekt. Safe to call repeatedly; throws if registration fails. */
    @Synchronized
    fun install(context: Context, client: OkHttpClient) {
        if (installed) return
        sharedClient = client
        val app = context.applicationContext as Application
        Injekt.addSingleton(app)
        Injekt.addSingleton(NetworkHelper(client, app))
        Injekt.addSingleton(JavaScriptEngine(app))
        Injekt.addSingleton(Json { ignoreUnknownKeys = true })
        installed = true
    }
}

/** Waits for the first value of an RxJava 1 observable; the work runs on the calling thread. */
suspend fun <T> Observable<T>.awaitFirst(): T = suspendCancellableCoroutine { continuation ->
    val subscription = first().subscribe(
        { value -> continuation.resume(value) },
        { error -> continuation.resumeWithException(error) },
    )
    continuation.invokeOnCancellation { subscription.unsubscribe() }
}
