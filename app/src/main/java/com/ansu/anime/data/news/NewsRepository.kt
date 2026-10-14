package com.ansu.anime.data.news

import android.text.Html
import android.util.Xml
import com.ansu.anime.ui.schedule.NewsArticle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The anime news behind the "News" toggle on the Schedule tab, read live from RSS feeds: Anime News Network first,
 * MyAnimeList's news as the fallback when ANN cannot be reached. AniList has no news feed of its own.
 *
 * The last good answer is kept for [FRESH_MS]; a refresh that fails gives back the older copy rather than an empty
 * list, so the tab does not go blank when the connection drops.
 */
class NewsRepository(private val client: OkHttpClient) {

    private class Cached(val articles: List<NewsArticle>, val at: Long)

    @Volatile
    private var cached: Cached? = null

    /** True when [getNews] would answer from memory without asking the network. */
    fun isFresh(): Boolean = cached?.let { System.currentTimeMillis() - it.at <= FRESH_MS } == true

    /**
     * The newest articles, newest first. [force] skips the in-memory copy. Returns the stored copy if the network
     * fails, and an empty list only when nothing was ever loaded.
     */
    suspend fun getNews(force: Boolean = false): List<NewsArticle> {
        val hit = cached
        if (!force && hit != null && System.currentTimeMillis() - hit.at <= FRESH_MS) return hit.articles
        for (feed in FEEDS) {
            val articles = try {
                fetch(feed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (articles.isNotEmpty()) {
                cached = Cached(articles, System.currentTimeMillis())
                return articles
            }
        }
        return hit?.articles.orEmpty()
    }

    private suspend fun fetch(feed: Feed): List<NewsArticle> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(feed.url).header("Accept", "application/rss+xml, application/xml, text/xml").build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("News feed HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        parse(body, feed.defaultCategory)
    }

    private fun parse(xml: String, defaultCategory: String): List<NewsArticle> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }
        val items = mutableListOf<RawItem>()
        var current: RawItem? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> current = RawItem()
                    "title" -> current?.title = parser.nextText()
                    "link" -> current?.link = parser.nextText()
                    "guid" -> current?.guid = parser.nextText()
                    "description" -> current?.description = parser.nextText()
                    "pubDate" -> current?.pubDate = parser.nextText()
                    "category" -> if (current?.category == null) current?.category = parser.nextText()
                }
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    current?.let { items += it }
                    current = null
                }
            }
            event = parser.next()
        }
        return items
            .mapNotNull { it.toArticle(defaultCategory) }
            .sortedByDescending { it.publishedAt }
            .take(MAX_ARTICLES)
    }

    private class RawItem {
        var title: String? = null
        var link: String? = null
        var guid: String? = null
        var description: String? = null
        var pubDate: String? = null
        var category: String? = null
    }

    private fun RawItem.toArticle(defaultCategory: String): NewsArticle? {
        val cleanTitle = title?.let(::plain)?.takeIf { it.isNotBlank() } ?: return null
        val published = pubDate?.trim()?.let { runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME) }.getOrNull() }
        return NewsArticle(
            id = guid?.takeIf { it.isNotBlank() } ?: link ?: cleanTitle,
            title = cleanTitle,
            snippet = description?.let(::plain).orEmpty(),
            category = category?.let(::plain)?.takeIf { it.isNotBlank() } ?: defaultCategory,
            dateDisplay = published?.withZoneSameInstant(ZoneId.systemDefault())?.format(DISPLAY_DATE).orEmpty(),
            url = link?.trim()?.takeIf { it.startsWith("http") },
            publishedAt = published?.toInstant()?.toEpochMilli() ?: 0L,
        )
    }

    /** Feed text arrives with HTML tags and entities; the cards show plain text. */
    private fun plain(text: String): String =
        Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT).toString().replace('\uFFFC', ' ').replace(Regex("\\s+"), " ").trim()

    private class Feed(val url: String, val defaultCategory: String)

    private companion object {
        const val FRESH_MS = 10 * 60_000L
        const val MAX_ARTICLES = 40
        val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
        val FEEDS = listOf(
            Feed("https://www.animenewsnetwork.com/all/rss.xml?ann-edition=w", "Anime"),
            Feed("https://myanimelist.net/rss/news.xml", "News"),
        )
    }
}
