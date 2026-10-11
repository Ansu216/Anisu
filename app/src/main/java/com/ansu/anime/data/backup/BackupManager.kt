package com.ansu.anime.data.backup

import android.content.Context
import android.net.Uri
import com.ansu.anime.BuildConfig
import com.ansu.anime.data.db.AppDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full on-device backup and restore.
 *
 * Everything Anisu keeps locally ends up in a single archive with the `.anisu` extension: the plain
 * settings (appearance, player and streaming, search history, updates, extension repos and switched-off
 * sources) and the Room database that holds the local library and Continue Watching. The AniList sign-in
 * token is deliberately left out — it is encrypted with a per-device key and would be useless (or
 * dangerous to copy) on another phone — along with installed extension APKs, the on-disk log and the caches.
 *
 * The archive is a plain zip, so an `.anisu` file is also inspectable with any unzip tool.
 */
class BackupManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val dataDir = File(appContext.applicationInfo.dataDir)

    /** Writes a fresh backup into the cache and returns the `.anisu` file, or null when it failed. */
    fun buildBackupFile(): File? = runCatching {
        val exportDir = File(appContext.cacheDir, "backups")
        exportDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(exportDir, "Anisu-backup-$stamp.anisu")
        file.outputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_NAME))
                zip.write(manifest().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                backupFiles().forEach { source ->
                    val relative = source.relativeTo(dataDir).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(relative))
                    source.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        file
    }.getOrNull()

    /**
     * Restores an `.anisu` archive. The Room database must be closed by the caller first (it is
     * overwritten here); the app should be restarted afterwards for every setting to be re-read.
     * Returns the number of files written, or a failure when the archive is not a readable backup.
     */
    fun restoreFrom(uri: Uri): Result<Int> = runCatching {
        val stream = appContext.contentResolver.openInputStream(uri)
            ?: error("Could not open the selected file")
        var written = 0
        stream.use { input ->
            ZipInputStream(input).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                if (entry == null) error("That file is empty")
                while (entry != null) {
                    val name = entry.name
                    if (name != MANIFEST_NAME && isSafeEntry(name)) {
                        val target = File(dataDir, name)
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                        written++
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        written
    }

    /** The files worth carrying: settings and the database, minus the token, extensions and caches. */
    private fun backupFiles(): List<File> {
        val sharedPrefs = File(dataDir, "shared_prefs")
            .listFiles()
            ?.filter { it.isFile && it.name != AUTH_PREFS }
            .orEmpty()
        val database = File(dataDir, "files")
            .listFiles()
            ?.filter { it.isFile && it.name.startsWith(DB_NAME) }
            .orEmpty()
        // A couple of tiny state files (remembered source matches); the extension APKs and the log
        // live in the same folder but are excluded, so the backup stays small.
        val stateFiles = File(dataDir, "files")
            .listFiles()
            ?.filter { it.isFile && it.name in INCLUDED_STATE_FILES }
            .orEmpty()
        return sharedPrefs + database + stateFiles
    }

    private fun isSafeEntry(name: String): Boolean {
        if (name.startsWith("/") || name.contains("..")) return false
        val top = name.substringBefore('/')
        if (top == "cache" || top == "code_cache" || top == "no_backup") return false
        return true
    }

    private fun manifest(): String = buildString {
        append("{\n")
        append("  \"app\": \"Anisu\",\n")
        append("  \"package\": \"").append(BuildConfig.APPLICATION_ID).append("\",\n")
        append("  \"format\": ").append(FORMAT_VERSION).append(",\n")
        append("  \"versionName\": \"").append(BuildConfig.VERSION_NAME).append("\",\n")
        append("  \"versionCode\": ").append(BuildConfig.VERSION_CODE).append(",\n")
        append("  \"createdAt\": \"").append(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date())).append("\"\n")
        append("}\n")
    }

    private companion object {
        const val FORMAT_VERSION = 1
        const val MANIFEST_NAME = "anisu-backup.json"
        const val AUTH_PREFS = "kernel_anilist_auth.xml"
        const val DB_NAME = "ansu.db"
        val INCLUDED_STATE_FILES = setOf("source-matches.json")
    }
}
