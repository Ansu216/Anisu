package eu.kanade.tachiyomi.animesource.model

/** Library 17: the tiles a source offers as seek-bar thumbnails. */
open class ThumbnailInfo(
    val tileInfo: List<TileInfo>,
    val imageTileUrls: List<String>,
)

/** One tile of a thumbnail sprite sheet. */
data class TileInfo(
    val imageIndex: Int,
    val timeMs: Long,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)
