package eu.kanade.tachiyomi.animesource

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** A source with settings. Ansu has no settings screen for them yet, so a source runs on its defaults. */
interface ConfigurableAnimeSource : AnimeSource {
    fun setupPreferenceScreen(screen: PreferenceScreen)
}

fun ConfigurableAnimeSource.preferenceKey(): String = "source_$id"

fun ConfigurableAnimeSource.sourcePreferences(): SharedPreferences =
    Injekt.get<Application>().getSharedPreferences(preferenceKey(), Context.MODE_PRIVATE)
