package com.ansu.anime.anilist

import com.ansu.anime.ui.details.EpisodeItem
import com.ansu.anime.ui.details.PersonCard
import com.ansu.anime.ui.details.RelatedCard

fun AniListMedia.toEpisodeItems(): List<EpisodeItem> {
    val blurb = plainDescription.substringBefore("\n").take(140)
    return streamingEpisodes.mapIndexed { index, ep ->
        EpisodeItem(
            number = index + 1,
            title = ep.title ?: "Episode ${index + 1}",
            description = blurb,
            thumbnailUrl = ep.thumbnail,
        )
    }
}

fun AniListMedia.toRelatedCards(): List<RelatedCard> = relations?.edges
    ?.filter { it.node.type == "ANIME" || it.node.type == "MANGA" }
    ?.map { edge ->
        RelatedCard(
            id = edge.node.id,
            title = edge.node.title.english ?: edge.node.title.romaji ?: "Untitled",
            imageUrl = edge.node.coverImage?.extraLarge ?: edge.node.coverImage?.large,
            badge = when (edge.relationType) {
                "SEQUEL" -> "SEASON 2"
                "PREQUEL" -> "PREQUEL"
                "SIDE_STORY" -> "SIDE STORY"
                else -> null
            },
        )
    } ?: emptyList()

fun AniListMedia.toRecommendationCards(): List<RelatedCard> = recommendations?.nodes
    ?.mapNotNull { it.mediaRecommendation }
    ?.map { node ->
        RelatedCard(
            id = node.id,
            title = node.title.english ?: node.title.romaji ?: "Untitled",
            imageUrl = node.coverImage?.extraLarge ?: node.coverImage?.large,
            badge = null,
        )
    } ?: emptyList()

fun AniListMedia.toCharacterCards(): List<PersonCard> = characters?.edges?.map { edge ->
    PersonCard(
        id = edge.node.id,
        name = edge.node.name.full ?: "Unknown",
        role = edge.role ?: "",
        imageUrl = edge.node.image?.large ?: edge.node.image?.medium,
    )
} ?: emptyList()

fun AniListMedia.toStaffCards(): List<PersonCard> = staff?.edges?.map { edge ->
    PersonCard(
        id = edge.node.id,
        name = edge.node.name.full ?: "Unknown",
        role = edge.role ?: "",
        imageUrl = edge.node.image?.large ?: edge.node.image?.medium,
    )
} ?: emptyList()
