package com.ansu.anime.anilist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ansu.anime.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * AniList uses OAuth2's implicit grant for client-only apps: we open the
 * authorize URL in a Custom Tab, the user logs in on anilist.co, and AniList
 * redirects back to our registered `ansu://anilist-auth` scheme with the
 * access token in the URI fragment — no client secret or backend needed.
 *
 * Register a client at https://anilist.co/settings/developer with redirect
 * URI `ansu://anilist-auth` and put the client id in [BuildConfig.ANILIST_CLIENT_ID].
 */
class AniListAuthManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "kernel_anilist_auth",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _accessToken = MutableStateFlow(prefs.getString(KEY_TOKEN, null))
    val accessToken: StateFlow<String?> = _accessToken
    val isLoggedIn: Boolean get() = _accessToken.value != null

    fun launchLogin() {
        if (BuildConfig.ANILIST_CLIENT_ID.isBlank() || BuildConfig.ANILIST_CLIENT_ID.startsWith("YOUR_")) {
            Log.e("AniListAuth", "ANILIST_CLIENT_ID is still the placeholder; set it via -PANILIST_CLIENT_ID=<id> or app/build.gradle.kts")
        }
        val authorizeUrl = Uri.parse("https://anilist.co/api/v2/oauth/authorize")
            .buildUpon()
            .appendQueryParameter("client_id", BuildConfig.ANILIST_CLIENT_ID)
            // Do NOT send redirect_uri: AniList's implicit grant rejects it with
            // "unsupported_grant_type". The redirect comes from the URL registered
            // in the AniList developer settings (must be ansu://anilist-auth).
            .appendQueryParameter("response_type", "token")
            .build()

        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(context, authorizeUrl)
    }

    /** Call from [android.app.Activity.onNewIntent] / onCreate when handling the `ansu://anilist-auth` redirect. */
    fun handleRedirect(uri: Uri): Boolean {
        if (uri.scheme != "ansu" || uri.host != "anilist-auth") return false
        // AniList returns the token in the URI *fragment* (#access_token=...),
        // which Android's Uri parses into getFragment() rather than query params.
        val fragment = uri.fragment ?: return false
        val token = fragment.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == "access_token" }
            ?.get(1)
            ?.let { Uri.decode(it) }
            ?.takeIf { it.isNotBlank() }
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
        private const val KEY_TOKEN = "access_token"
    }
}
