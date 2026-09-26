package com.kernel.anime.extension

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** One row of a repo's `index.min.json`, in the shape Keiyoushi repos publish. */
@Serializable
data class ExtensionRepoEntry(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val version: String,
    val code: Int = 0,
    val nsfw: Int = 0,
    val icon: String? = null,
)

/**
 * Fetches a repo index and downloads+installs the APKs a person picks, the
 * same flow Keiyoushi-compatible apps use since Play Store policy blocks
 * shipping arbitrary third-party source code inside the main app.
 */
class ExtensionRepo(
    private val context: Context,
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchIndex(repoIndexUrl: String): Result<List<ExtensionRepoEntry>> = runCatching {
        val request = Request.Builder().url(repoIndexUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Repo returned HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            json.decodeFromString<List<ExtensionRepoEntry>>(body)
        }
    }

    /**
     * Downloads the extension APK to cache and launches the system installer.
     * The user still has to tap through Android's "install unknown apps"
     * confirmation, exactly as in Aniyomi/Mihon.
     */
    suspend fun downloadAndInstall(repoBaseUrl: String, entry: ExtensionRepoEntry): Result<Unit> = runCatching {
        val apkUrl = if (entry.apk.startsWith("http")) entry.apk else "$repoBaseUrl/${entry.apk}"
        val request = Request.Builder().url(apkUrl).build()
        val apkFile = File(context.cacheDir, "${entry.pkg}-${entry.version}.apk")

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
            response.body?.byteStream()?.use { input ->
                apkFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Empty response body")
        }

        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }
}
