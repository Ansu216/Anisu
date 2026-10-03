package com.ansu.anime.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ansu.anime.core.diagnostics.Diagnostics
import com.ansu.anime.core.diagnostics.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Downloads a published APK and hands it to the system installer — the same flow
 * [com.ansu.anime.extension.ExtensionRepo] uses for extension APKs, including the `FileProvider`
 * authority. That provider already exposes the whole cache directory, so no extra `file_paths.xml`
 * entry is needed.
 *
 * The download is a two-file "part" flow, so a dropped connection never leaves a half APK that
 * Android would refuse to parse:
 *
 *  1. the bytes stream into `ansu-update-<version>.apk.part`;
 *  2. when the file is complete it is renamed to `ansu-update-<version>.apk` and only then installed.
 *
 * A leftover `.part` from an interrupted run is resumed with an HTTP `Range` request (and thrown away
 * when the server does not support ranges), and stale `.part`/`.apk` files from older versions are
 * cleaned up so the cache cannot grow without bound.
 *
 * Downloading happens off the main thread; [install] must be called on it, because it starts an activity.
 */
class UpdateInstaller(
    context: Context,
    private val client: OkHttpClient,
    private val diagnostics: Diagnostics? = null,
) {
    private val appContext = context.applicationContext
    private val updatesDir = File(appContext.cacheDir, "updates")

    suspend fun download(update: AvailableUpdate, onProgress: (Float) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            updatesDir.mkdirs()
            cleanupOldParts(keep = update.versionName)

            val partialFile = File(updatesDir, "ansu-update-${update.versionName}.apk.part")
            val finalFile = File(updatesDir, "ansu-update-${update.versionName}.apk")

            // Resume from what a previous attempt already wrote, when the server lets us.
            var resumeFrom = partialFile.takeIf { it.exists() }?.length() ?: 0L
            var request = baseRequest(update.apkUrl)
            if (resumeFrom > 0L) request = request.newBuilder().header("Range", "bytes=$resumeFrom-").build()

            var response = client.newCall(request).execute()
            // 206 means the server honoured the range; anything else means start clean.
            if (resumeFrom > 0L && response.code != 206) {
                response.close()
                partialFile.delete()
                resumeFrom = 0L
                response = client.newCall(baseRequest(update.apkUrl)).execute()
            }

            response.use { res ->
                if (!res.isSuccessful) error("Download failed: HTTP ${res.code}")
                val body = res.body
                val length = body.contentLength()
                val total = if (length > 0) resumeFrom + length else -1L

                val append = resumeFrom > 0L && res.code == 206
                body.byteStream().use { input ->
                    java.io.FileOutputStream(partialFile, append).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = resumeFrom
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) onProgress((copied.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                        output.flush()
                    }
                }
            }

            // Only a complete download may become an .apk.
            if (finalFile.exists()) finalFile.delete()
            if (!partialFile.renameTo(finalFile)) {
                partialFile.copyTo(finalFile, overwrite = true)
                partialFile.delete()
            }
            diagnostics?.log(LogCategory.UPDATE, "Update ${update.versionName} downloaded (${finalFile.length() / 1024} KB)")
            finalFile
        }

    /** Opens Android's "install unknown apps" prompt. The user still has to confirm. */
    fun install(apkFile: File) {
        diagnostics?.log(LogCategory.UPDATE, "Handing ${apkFile.name} to the system installer")
        val apkUri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        appContext.startActivity(intent)
    }

    private fun baseRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", "Ansu-Android-Updater")
        .build()

    /** Drops every cached APK and `.part` that is not the build currently being downloaded. */
    private fun cleanupOldParts(keep: String) {
        runCatching {
            val keepNames = setOf("ansu-update-$keep.apk.part", "ansu-update-$keep.apk")
            updatesDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("ansu-update-") && file.name !in keepNames) {
                    file.delete()
                }
            }
        }
    }
}
