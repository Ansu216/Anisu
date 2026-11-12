package com.ansu.anime.data.translate

import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Translates subtitle lines through Google's free web translation. Nothing is bundled in the APK (an on-device
 * model library added tens of megabytes), so it needs a connection while it translates. Every translated line is
 * cached, so a line that shows again (a seek back, a repeated "Yes.") costs nothing, and a line that cannot be
 * translated is shown as it was, so a subtitle is never lost because translating broke.
 */
class SubtitleTranslator {

    sealed interface Status {
        /** Translation is off, or nothing was asked for yet. */
        data object Idle : Status

        /** Translating from [source] to [target]. */
        data class Ready(val source: String, val target: String) : Status

        /** Translation cannot run: [message] says why (unsupported language, no connection). */
        data class Failed(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status

    private val cache = LruCache<String, String>(CACHE_SIZE)

    @Volatile
    private var pair: Pair<String, String>? = null

    /** True once [prepare] has a usable pair, so callers can skip the coroutine hop when it is off. */
    val active: Boolean get() = pair != null

    /**
     * Gets [sourceTag] to [targetTag] ready. Both are language tags such as "en" or "pt-BR"; only the language part
     * matters. Returns whether translating is possible; the reason it is not is in [status].
     */
    fun prepare(sourceTag: String, targetTag: String): Boolean {
        val src = toLanguageCode(sourceTag)
        val tgt = toLanguageCode(targetTag)
        return when {
            src == null -> fail("This subtitle language can't be translated")
            tgt == null -> fail("This target language isn't supported")
            src == tgt -> fail("Subtitles are already in this language")
            else -> {
                pair = src to tgt
                _status.value = Status.Ready(src, tgt)
                true
            }
        }
    }

    /**
     * Translates [text] with the pair [prepare] made ready. Returns [text] unchanged when nothing is ready, when it
     * is blank, or when the translation fails.
     */
    suspend fun translate(text: String): String {
        val current = pair ?: return text
        if (text.isBlank()) return text
        val key = "${current.first}>${current.second}|$text"
        cache.get(key)?.let { return it }
        return try {
            val out = withContext(Dispatchers.IO) { request(current.first, current.second, text) }
            if (out.isNotBlank()) {
                cache.put(key, out)
                _status.value = Status.Ready(current.first, current.second)
                out
            } else {
                text
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = Status.Failed("Couldn't reach the translation service. Check your connection.")
            text
        }
    }

    /** Turns translating off. */
    fun release() {
        pair = null
        _status.value = Status.Idle
    }

    /** Called when the view model goes away; nothing is held open. */
    fun close() {
        pair = null
    }

    private fun fail(message: String): Boolean {
        pair = null
        _status.value = Status.Failed(message)
        return false
    }

    private fun request(source: String, target: String, text: String): String {
        val url = URL(
            "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=$source&tl=${apiCode(target)}" +
                "&q=${URLEncoder.encode(text, "UTF-8")}",
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            // The answer is [[["translated part","original part",...], ...], ...]: the parts joined are the line.
            val parts = JSONArray(body).getJSONArray(0)
            return buildString {
                for (i in 0 until parts.length()) append(parts.getJSONArray(i).optString(0, ""))
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val CACHE_SIZE = 3000
        private const val TIMEOUT_MS = 6000

        /** The languages offered as targets (and recognised as sources), as language codes. */
        private val LANGUAGES = listOf(
            "af", "ar", "be", "bg", "bn", "ca", "cs", "cy", "da", "de", "el", "en", "es", "et", "fa", "fi", "fr",
            "ga", "gl", "gu", "he", "hi", "hr", "hu", "id", "is", "it", "ja", "ka", "kn", "ko", "lt", "lv", "mk",
            "mr", "ms", "mt", "nl", "no", "pl", "pt", "ro", "ru", "sk", "sl", "sq", "sv", "sw", "ta", "te", "th",
            "tl", "tr", "uk", "ur", "vi", "zh",
        )

        fun supportedLanguages(): List<String> = LANGUAGES

        /** A language tag ("pt-BR", "ja") as one of the supported language codes, or null when it is not offered. */
        fun toLanguageCode(tag: String): String? {
            val language = tag.trim().substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
            return language.takeIf { it in LANGUAGES }
        }

        /** The code the web service expects: it wants the Chinese variant spelled out. */
        private fun apiCode(code: String): String = if (code == "zh") "zh-CN" else code

        /**
         * The language code for a subtitle track's label as the player shows it ("English", "Spanish - Latin
         * America"), or null when the label names no supported language. Extension subtitle labels carry the
         * language's English name, which is what is matched.
         */
        fun fromTrackLabel(label: String): String? {
            val text = label.trim()
            if (text.isEmpty()) return null
            if (text.length <= 3) toLanguageCode(text)?.let { return it }
            val lower = text.lowercase(Locale.ROOT)
            return LANGUAGES.firstOrNull { code ->
                val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT)
                name.isNotEmpty() && lower.contains(name)
            }
        }

        /** The name of a language code in the person's own language, for the settings list. */
        fun displayName(code: String): String =
            Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
                .replaceFirstChar { it.titlecase(Locale.getDefault()) }
                .ifBlank { code }
    }
}
