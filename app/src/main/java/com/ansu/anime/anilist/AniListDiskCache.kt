package com.ansu.anime.anilist

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * The answers AniList already gave, kept on disk so a screen still opens when the phone is offline.
 *
 * The in-memory cache in [AniListApi] only lives as long as the process; this one is what lets Home,
 * search and the details page show their last known content after a restart with no connection. One
 * file per request, named by a hash of the request key (the key can be several kilobytes of GraphQL).
 * Every file holds its own timestamp, so a stale answer is recognised without reading the whole cache
 * and can still be served when the network fails.
 *
 * Nothing here may throw: a cache that breaks the screen it is meant to speed up is worse than none.
 */
class AniListDiskCache(
    private val dir: File?,
    private val maxEntries: Int = MAX_ENTRIES,
    private val maxAgeMs: Long = MAX_AGE_MS,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** One thread for every write and for the start-up prune, so the UI thread never touches the files. */
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "anilist-disk-cache").apply { isDaemon = true }
    }

    class CachedAnswer(val data: JsonObject, val at: Long)

    init {
        if (dir != null) io.execute { prune() }
    }

    /** The stored answer for [key], fresh or not; the caller decides whether its age is acceptable. */
    fun read(key: String): CachedAnswer? = runCatching {
        val file = fileFor(key) ?: return null
        if (!file.exists()) return null
        val root = json.parseToJsonElement(file.readText()).jsonObject
        val at = root["at"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        val data = root["data"]?.jsonObject ?: return null
        CachedAnswer(data, at)
    }.getOrNull()

    fun write(key: String, data: JsonObject, at: Long = System.currentTimeMillis()) {
        val folder = dir ?: return
        val text = "{\"at\":$at,\"data\":$data}"
        io.execute {
            runCatching {
                if (!folder.exists() && !folder.mkdirs()) return@runCatching
                File(folder, "${hash(key)}.json").writeText(text)
            }
        }
    }

    /** Drops answers older than [maxAgeMs] and, above [maxEntries] files, the oldest ones first. */
    fun prune() {
        runCatching {
            val folder = dir ?: return
            val files = folder.listFiles()?.filter { it.isFile } ?: return
            val cutoff = System.currentTimeMillis() - maxAgeMs
            files.filter { it.lastModified() < cutoff }.forEach { it.delete() }
            val left = folder.listFiles()?.filter { it.isFile }.orEmpty()
            if (left.size > maxEntries) {
                left.sortedBy { it.lastModified() }.take(left.size - maxEntries).forEach { it.delete() }
            }
        }
    }

    fun clear() {
        io.execute { runCatching { dir?.listFiles()?.forEach { it.delete() } } }
    }

    private fun fileFor(key: String): File? {
        val folder = dir ?: return null
        if (!folder.exists() && !folder.mkdirs()) return null
        return File(folder, "${hash(key)}.json")
    }

    private fun hash(key: String): String = runCatching {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        digest.joinToString("") { byte -> "%02x".format(byte) }.take(32)
    }.getOrElse { key.hashCode().toString() }

    private companion object {
        const val MAX_ENTRIES = 300
        const val MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
