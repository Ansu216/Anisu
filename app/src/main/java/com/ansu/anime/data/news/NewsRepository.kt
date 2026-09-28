package com.ansu.anime.data.news

import com.ansu.anime.ui.schedule.NewsArticle

/**
 * News source for the "News" toggle on the Schedule tab.
 *
 * AniList's GraphQL schema has no news feed (unlike [com.ansu.anime.anilist.AniListApi],
 * which has a real `airingSchedules` query backing the Schedule side), so this repository
 * is a thin seam rather than a live client: [getNews] returns fixture data today, and can
 * be swapped for a real HTTP call — an RSS/JSON feed, or your own backend — without any
 * change to ScheduleScreen or [NewsArticle] itself.
 */
object NewsRepository {

    suspend fun getNews(): List<NewsArticle> = sampleNews

    private val sampleNews = listOf(
        NewsArticle(
            id = "1",
            title = "Long-Running Mecha Series Confirms Fourth Season",
            snippet = "Studio reveals a 2027 premiere window alongside a new key visual and returning cast.",
            category = "Anime",
            dateDisplay = "Sun, 27 Sep 2026",
        ),
        NewsArticle(
            id = "2",
            title = "Fantasy Adventure Manga Wraps Up After Eight Years",
            snippet = "Creator says the final chapter was planned from the series' very first arc.",
            category = "Manga",
            dateDisplay = "Sat, 26 Sep 2026",
        ),
        NewsArticle(
            id = "3",
            title = "Streaming Platform Adds Same-Day Dubs for Fall Lineup",
            snippet = "Ten titles will get English dub episodes released within a day of the Japanese broadcast.",
            category = "Anime",
            dateDisplay = "Sat, 26 Sep 2026",
        ),
        NewsArticle(
            id = "4",
            title = "Veteran Voice Actor Announces Retirement From Anime Roles",
            snippet = "A four-decade career included lead roles across several long-running franchises.",
            category = "People",
            dateDisplay = "Fri, 25 Sep 2026",
        ),
        NewsArticle(
            id = "5",
            title = "Original Anime Short Reveals Cast and Staff Ahead of Spring Screening",
            snippet = "The short pairs with a remastered theatrical cut of the studio's earlier film.",
            category = "Anime",
            dateDisplay = "Fri, 25 Sep 2026",
        ),
    )
}
