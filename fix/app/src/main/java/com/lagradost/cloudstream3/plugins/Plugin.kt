package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources

/**
 * The base class every CloudStream plugin extends. In CloudStream this lives in the app module, not in the plugin
 * library (which only has [BasePlugin]), so Anisu provides the same class under the same name: a `.cs3` file refers
 * to it by that name and finds this one in the host class loader.
 */
abstract class Plugin : BasePlugin() {
    /** Resources packed inside the plugin, when its manifest says `requiresResources`. */
    var resources: Resources? = null

    /** Set by a plugin that has a settings screen. Anisu has no CloudStream settings UI, so this is only stored. */
    var openSettings: ((context: Context) -> Unit)? = null

    @Throws(Throwable::class)
    open fun load(context: Context) {
        // Not abstract: plugins implement either this or the context-less load() of BasePlugin.
        load()
    }
}
