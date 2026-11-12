package com.ansu.anime.extension.cloudstream

import com.lagradost.cloudstream3.app
import okhttp3.OkHttpClient

/**
 * Sets up what a CloudStream plugin expects to find around it. Plugins make their requests through the
 * library's global `app` (nicehttp) client, so Anisu's own OkHttp client is installed there: plugin traffic then
 * shares Anisu's DNS, cache and cookie settings like every other source does.
 */
object CloudStreamRuntime {

    @Volatile
    private var installed = false

    @Synchronized
    fun install(client: OkHttpClient) {
        if (installed) return
        app.baseClient = client
        installed = true
    }
}
