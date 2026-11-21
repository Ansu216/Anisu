package eu.kanade.tachiyomi.animesource.model

import fi.iki.elonen.NanoHTTPD

/** Library 17: a local server a source starts so the player can reach a stream that needs a proxy. */
open class HttpServer : NanoHTTPD(0) {

    val url: String
        get() = "http://localhost:$listeningPort"

    fun isRunning(): Boolean = isRunning

    @Volatile
    private var isRunning = false

    override fun start() {
        try {
            super.start()
            isRunning = true
        } catch (e: Exception) {
            android.util.Log.d("HttpServer", "Failed to start http server", e)
        }
    }

    override fun stop() {
        super.stop()
        isRunning = false
    }

    companion object {
        const val PLACEHOLDER_URL = "http://localhost:1"
    }
}
