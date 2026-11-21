package eu.kanade.tachiyomi.network

import android.content.Context
import app.cash.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Util for evaluating JavaScript in sources; extensions fetch it through Injekt. */
@Suppress("UNUSED", "UNCHECKED_CAST", "UNUSED_PARAMETER")
class JavaScriptEngine(context: Context) {

    /**
     * Evaluates [script] and returns the result as a primitive type (String, Int, ...).
     *
     * @since extensions-lib 1.4
     */
    suspend fun <T> evaluate(script: String): T = withContext(Dispatchers.IO) {
        QuickJs.create().use {
            it.evaluate(script) as T
        }
    }
}
