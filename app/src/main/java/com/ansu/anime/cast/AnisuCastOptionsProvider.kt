package com.ansu.anime.cast

import android.content.Context
import com.ansu.anime.MainActivity
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions

/**
 * Read by the Cast framework through the `OPTIONS_PROVIDER_CLASS_NAME` entry in the manifest. Anisu uses Google's
 * Default Media Receiver, so no receiver app has to be registered with Google.
 *
 * Like Cloudstream's CastOptionsProvider this also supplies [CastMediaOptions]: the framework then owns the media
 * session / notification for the cast, which keeps the session alive when the phone screen turns off.
 */
class AnisuCastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions {
        val notificationOptions = NotificationOptions.Builder()
            .setTargetActivityClassName(MainActivity::class.java.name)
            .build()
        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .build()
        return CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setStopReceiverApplicationWhenEndingSession(true)
            .setResumeSavedSession(true)
            .setCastMediaOptions(mediaOptions)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
