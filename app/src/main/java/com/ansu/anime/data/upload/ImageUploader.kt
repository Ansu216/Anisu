package com.ansu.anime.data.upload

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Puts a picture the person chose on the phone onto a public image host and returns its link. AniList has no upload of
 * its own: a picture in a comment is only a link written as `img(link)`, so a picture has to be hosted somewhere first.
 * Catbox takes an anonymous upload with no account or key. Returns null when the file is not a picture, is too big,
 * or the host did not answer with a link.
 */
class ImageUploader(context: Context, client: OkHttpClient) {

    private val resolver = context.applicationContext.contentResolver
    private val client = client.newBuilder()
        .callTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun upload(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val mime = resolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: return@runCatching null
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@runCatching null
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("reqtype", "fileupload")
                .addFormDataPart("fileToUpload", "comment.$extension", bytes.toRequestBody(mime.toMediaType()))
                .build()
            val request = Request.Builder().url(HOST).post(body).build()
            client.newCall(request).execute().use { response ->
                val link = response.body?.string()?.trim().orEmpty()
                if (response.isSuccessful && link.startsWith("https://")) link else null
            }
        }.getOrNull()
    }

    private companion object {
        const val HOST = "https://catbox.moe/user/api.php"
        const val MAX_BYTES = 20 * 1024 * 1024
    }
}
