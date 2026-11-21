package eu.kanade.tachiyomi.animesource

/** An extension class that creates several sources at once (for example one per language). */
interface AnimeSourceFactory {
    fun createSources(): List<AnimeSource>
}
