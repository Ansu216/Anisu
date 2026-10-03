package eu.kanade.tachiyomi.animesource

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** A source with settings. Ansu has no settings screen for them yet, so a source runs on its defaults. */
interface ConfigurableAnimeSource : AnimeSource {

    /** Library 1.5+: the preferences scoped to this source. Extensions read their own settings from here. */
    fun getSourcePreferences(): SharedPreferences =
        Injekt.get<Application>().getSharedPreferences(preferenceKey(), Context.MODE_PRIVATE)

    fun setupPreferenceScreen(screen: PreferenceScreen)
}

fun ConfigurableAnimeSource.preferenceKey(): String = "source_$id"

// Deliberately not delegating to getSourcePreferences(): an extension compiled against an older library does not
// implement that member, and calling it on such a class would throw AbstractMethodError.
fun ConfigurableAnimeSource.sourcePreferences(): SharedPreferences =
    Injekt.get<Application>().getSharedPreferences(preferenceKey(), Context.MODE_PRIVATE)

/** Older extensions ask for a source's preferences by key. */
fun sourcePreferences(key: String): SharedPreferences =
    Injekt.get<Application>().getSharedPreferences(key, Context.MODE_PRIVATE)
