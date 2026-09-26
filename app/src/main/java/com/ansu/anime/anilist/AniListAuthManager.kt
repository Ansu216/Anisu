package com.ansu.anime.anilist

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * AniList uses OAuth2's implicit grant for client-only apps: we open the
 * authorize URL in a Custom Tab, the user logs in on anilist.co, and AniList
 * redirects back to our registered `anisu://anilist-auth` scheme with the
 * access token in the URI fragment — no client secret or backend needed.
 *
 * Register a client at https://anilist.co/settings/developer with redirect
 * URI `anisu://anilist-auth` and put the client id in [ANILIST_CLIENT_ID].
 */
class AniListAuthManager(private val context: Context) {

    private val prefs: SharedPreferences = createPrefs(context)

    private val _accessToken = MutableStateFlow(prefs.getString(KEY_TOKEN, null))
    val accessToken: StateFlow<String?> = _accessToken
    val isLoggedIn: Boolean get() = _accessToken.value != null

    fun launchLogin() {
        val authorizeUrl = Uri.parse("https://anilist.co/api/v2/oauth/authorize")
            .buildUpon()
            .appendQueryParameter("client_id", ANILIST_CLIENT_ID)
            .appendQueryParameter("redirect_uri", "anisu://anilist-auth")
            .appendQueryParameter("response_type", "token")
            .build()

        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(context, authorizeUrl)
    }

    /** Call from [android.app.Activity.onNewIntent] / onCreate when handling the `anisu://anilist-auth` redirect. */
    fun handleRedirect(uri: Uri): Boolean {
        if (uri.scheme != "anisu" || uri.host != "anilist-auth") return false
        // AniList returns the token in the URI *fragment* (#access_token=...),
        // which Android's Uri parses into getFragment() rather than query params.
        val fragment = uri.fragment ?: return false
        val token = fragment.split("&")
            .map { it.split("=") }
            .firstOrNull { it.size == 2 && it[0] == "access_token" }
            ?.get(1)
            ?: return false

        prefs.edit().putString(KEY_TOKEN, token).apply()
        _accessToken.value = token
        return true
    }

    fun logout() {
        prefs.edit().remove(KEY_TOKEN).apply()
        _accessToken.value = null
    }

    companion object {
        /** Fill in from https://anilist.co/settings/developer */
        const val ANILIST_CLIENT_ID = "YOUR_ANILIST_CLIENT_ID"

        private const val KEY_TOKEN = "access_token"
        private const val PREFS_NAME = "anisu_anilist_auth"

        /**
         * [EncryptedSharedPreferences] needs a working Android Keystore. On a
         * handful of devices/ROMs (and after a backup restore) creating it
         * throws — and because this class is built during
         * `Application.onCreate`, that exception would kill the app before the
         * first frame is ever drawn. We therefore degrade to plain
         * preferences instead of crashing the whole app on launch.
         */
        private fun createPrefs(context: Context): SharedPreferences {
            return runCatching {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrElse {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
    }
}
