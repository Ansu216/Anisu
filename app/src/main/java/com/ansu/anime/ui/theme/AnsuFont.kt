package com.ansu.anime.ui.theme

import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

/**
 * The fonts the person added in Appearance, held as state so every screen picks a change up at once (the same
 * way [AnsuColors] holds the accent). Null means "nothing added": the app keeps its own font.
 */
object AnsuFont {
    /** The font for the whole app, or null. */
    var appFamily: FontFamily? by mutableStateOf(null)
        private set

    /** The font the subtitles are drawn in, or null for the player's default subtitle font. */
    var subtitleTypeface: Typeface? by mutableStateOf(null)
        private set

    /**
     * [appFile] is the app font, [subtitleFile] the subtitle font (null when subtitles should keep the default or,
     * with no separate subtitle font chosen, follow the app font: the caller decides which file that is).
     * A file that cannot be read is ignored, so a damaged font never stops the app from starting.
     */
    fun apply(appFile: File?, subtitleFile: File?) {
        appFamily = appFile?.let { file ->
            runCatching {
                // A single-weight font: Normal and Medium use the file as it is, and the heavier weights the app asks
                // for (titles) get the platform's synthetic bold, so headings still stand out from body text.
                FontFamily(
                    Font(file, FontWeight.Normal),
                    Font(file, FontWeight.Medium),
                )
            }.getOrNull()
        }
        subtitleTypeface = subtitleFile?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
    }
}
