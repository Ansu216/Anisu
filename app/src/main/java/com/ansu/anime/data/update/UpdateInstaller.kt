package com.ansu.anime.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Downloads a published APK into the cache and hands it to the system installer
 * — the same flow [com.ansu.anime.extension.ExtensionRepo] uses for extension
 * APKs, including the `FileProvider` authority. That provider already exposes the
 * whole cache directory, so no extra `file_paths.xml` entry is needed.
 *
 * Downloading happens off the main thread; [install] must be called on it,
 * because it starts an activity.
 */
class UpdateInstaller(
    context: Context,
    private val client: OkHttpClient,
) {
    private val appContext = context.applicationContext

    suspend fun download(update: AvailableUpdate, onProgress: (Float) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            val apkFile = File(appContext.cacheDir, "ansu-update-${update.versionName}.apk")
            val request = Request.Builder()
                .url(update.apkUrl)
                .header("User-Agent", "Ansu-Android-Updater")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
                val body = response.body ?: error("Download failed: empty response")
                val total = body.contentLength()

                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) onProgress((copied.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            apkFile
        }

    /** Opens Android's "install unknown apps" prompt. The user still has to confirm. */
    fun install(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        appContext.startActivity(intent)
    }
}
