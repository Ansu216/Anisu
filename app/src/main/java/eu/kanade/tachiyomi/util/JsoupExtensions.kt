package eu.kanade.tachiyomi.util

import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Parses the response body as HTML, with the request URL as the base for relative links. */
fun Response.asJsoup(html: String? = null): Document = Jsoup.parse(html ?: body.string(), request.url.toString())

fun Element.selectText(css: String, defaultValue: String? = null): String? =
    select(css).first()?.text() ?: defaultValue

fun Element.selectInt(css: String, defaultValue: Int = 0): Int =
    select(css).first()?.text()?.toIntOrNull() ?: defaultValue

fun Element.attrOrText(css: String): String = if (css != "text") attr(css) else text()
