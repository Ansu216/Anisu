package com.ansu.anime.data.docs

import android.content.Context
import com.ansu.anime.core.net.ApiErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** The project documents About reads straight out of the repository. */
enum class ProjectDoc(val fileName: String, val title: String) {
    CHANGELOG("CHANGELOG.md", "Changelog"),
    LICENSE("LICENSE", "License"),
}

/**
 * Reads `CHANGELOG.md` and `LICENSE` live from the project's repository on GitHub, the way
 * [com.ansu.anime.data.contributors.ContributorsRepository] reads the contributor list, so the text a
 * user sees is the text the project publishes — a release note added on GitHub shows up in the app
 * without a new APK.
 *
 * Three layers, in order: the answer already fetched this session, a fresh request, and the copy kept
 * on disk from an earlier launch. The last one is what makes the changelog readable with no
 * connection; a first launch offline shows an empty document and the screen offers a retry.
 */
class ProjectDocsRepository(
    context: Context,
    private val client: OkHttpClient,
    private val errors: ApiErrorHandler,
) {
    private val cacheDir = File(context.applicationContext.filesDir, "project-docs")
    private val cached = mutableMapOf<ProjectDoc, String>()

    suspend fun load(doc: ProjectDoc, force: Boolean = false): String {
        if (!force) cached[doc]?.let { return it }
        val live = runCatching { fetchRemote(doc) }.getOrElse { error ->
            errors.report("Loading ${doc.fileName}", error, quiet = true)
            null
        }
        val text = live?.takeIf { it.isNotBlank() } ?: stored(doc)
        if (text.isNotBlank()) {
            cached[doc] = text
            if (live != null) store(doc, text)
        }
        return text
    }

    private suspend fun fetchRemote(doc: ProjectDoc): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$RAW_BASE/${doc.fileName}")
            .header("User-Agent", "Ansu-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("${doc.fileName}: HTTP ${response.code}")
            response.body.string()
        }
    }

    private suspend fun stored(doc: ProjectDoc): String = withContext(Dispatchers.IO) {
        runCatching { File(cacheDir, doc.fileName).readText() }.getOrDefault("")
    }

    private suspend fun store(doc: ProjectDoc, text: String) = withContext(Dispatchers.IO) {
        runCatching {
            cacheDir.mkdirs()
            File(cacheDir, doc.fileName).writeText(text)
        }
        Unit
    }

    companion object {
        /** The official Ansu repository; both files sit at the root of `main`. */
        private const val RAW_BASE = "https://raw.githubusercontent.com/Ansu216/Anisu/main"
    }
}
