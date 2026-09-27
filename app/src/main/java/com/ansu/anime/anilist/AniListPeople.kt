package com.ansu.anime.anilist

/** Display model for a single character row on the details sheet. */
data class AniListCharacter(
    val id: Int,
    val name: String,
    val imageUrl: String?,
    val role: String,
    val voiceActorName: String? = null,
    val voiceActorImageUrl: String? = null,
)

/** Display model for a single staff row on the details sheet. */
data class AniListStaffMember(
    val id: Int,
    val name: String,
    val imageUrl: String?,
    val role: String,
)
