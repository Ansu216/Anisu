package com.ansu.anime.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ansu.anime.R

/**
 * The app's own logo, as the bitmap a notification shows.
 *
 * Android draws a notification's header with the app icon the system has cached for the package, and
 * with Material You theming that icon is recoloured into the wallpaper palette — which is why a
 * notification could show a red badge with a dark A while the app itself shows the dark logo. Setting
 * the logo explicitly as the notification's large icon makes every notification carry the same mark
 * as the app, whatever the system's icon theme is doing.
 */
object NotificationIcons {

    /** The dark rounded-square logo with the white A, or null when the resources could not be read. */
    fun largeIcon(context: Context): Bitmap? = runCatching {
        BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_logo)
    }.getOrNull()
}
