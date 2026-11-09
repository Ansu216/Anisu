package eu.kanade.tachiyomi.animesource.utils

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Gets the preference key for the source with [id]. */
fun preferencesKey(id: Long) = "source_$id"

/** Gets the preference key for a source. */
fun AnimeSource.preferencesKey(): String = preferencesKey(id)

/** Gets the [SharedPreferences] scoped to a source key. */
fun sourcePreferences(key: String): SharedPreferences =
    Injekt.get<Application>().getSharedPreferences(key, Context.MODE_PRIVATE)

/** Gets the [SharedPreferences] scoped to a source (extensions-lib 16). */
fun AnimeSource.sourcePreferences(): SharedPreferences = sourcePreferences(preferencesKey())

/** Gets the [SharedPreferences] scoped to a source id (extensions-lib 16). */
fun sourcePreferences(id: Long): SharedPreferences = sourcePreferences(preferencesKey(id))
