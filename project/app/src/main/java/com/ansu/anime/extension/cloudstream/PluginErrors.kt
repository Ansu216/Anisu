package com.ansu.anime.extension.cloudstream

/**
 * Turns an error a CloudStream plugin raised into one line that says what is missing. A plugin written against the
 * CloudStream app (not just its library) can call classes Anisu does not carry; Android then reports
 * "Failed resolution of: Lcom/lagradost/cloudstream3/Something;", which is cut off on screen. The class (or
 * method) name is what is needed to add it, so it is put first.
 */
internal fun describePluginError(t: Throwable): String {
    val root = generateSequence(t) { it.cause?.takeIf { c -> c !== it } }.last()
    val raw = root.message?.takeIf { it.isNotBlank() }
    return when (root) {
        is ClassNotFoundException, is NoClassDefFoundError -> {
            val name = raw?.let { CLASS_IN_MESSAGE.find(it)?.groupValues?.get(1) ?: it }?.replace('/', '.')?.trim()
            "Missing class ${name ?: "(unknown)"} (the plugin needs a CloudStream app class Anisu does not have)"
        }
        is NoSuchMethodError, is NoSuchFieldError, is IncompatibleClassChangeError ->
            "${root::class.java.simpleName}: ${raw ?: "a CloudStream method the plugin calls is missing"}"
        else -> "${root::class.java.simpleName}: ${raw ?: "plugin is not compatible with Anisu"}"
    }
}

private val CLASS_IN_MESSAGE = Regex("L([\\w/$.]+);")
