package com.ansu.anime.cast

import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import java.util.concurrent.Executors

/**
 * Starts the Cast framework once, from MainActivity.onCreate.
 *
 * The framework watches the app's activities to know when the app is in the foreground and only then runs its own
 * device discovery and route handling. Starting it later (the player screen used to do it, asynchronously, after
 * the activity was already resumed) means it never sees that "entered foreground" event, so the TV list stays
 * empty or the connect tap does nothing. Cloudstream starts it in MainActivity.onCreate for the same reason.
 */
object CastBootstrap {
    private const val TAG = "AnisuCast"
    private val executor by lazy { Executors.newSingleThreadExecutor() }

    /** True when Google Play services is present and up to date enough for Cast. */
    fun isAvailable(context: Context): Boolean = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context.applicationContext) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    fun start(context: Context) {
        if (!isAvailable(context)) {
            Log.w(TAG, "Google Play services unavailable: Chromecast disabled")
            return
        }
        runCatching {
            com.google.android.gms.cast.framework.CastContext
                .getSharedInstance(context.applicationContext, executor)
                .addOnFailureListener { Log.w(TAG, "CastContext failed to start", it) }
        }.onFailure { Log.w(TAG, "CastContext start threw", it) }
    }
}
