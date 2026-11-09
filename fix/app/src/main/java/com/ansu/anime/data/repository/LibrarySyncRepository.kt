package com.ansu.anime.data.repository

import com.ansu.anime.anilist.AniListRepository
import kotlinx.coroutines.delay

/**
 * Pushes the library saved on this device up to the signed-in AniList account.
 * It only fills gaps: a show that already has a list entry on AniList keeps it
 * (AniList wins), and a show that is already hearted is not toggled again.
 * Local data is left in place afterwards.
 */
class LibrarySyncRepository(
    private val local: LocalListRepository,
    private val aniList: AniListRepository,
) {
    data class Result(
        val checked: Int,
        val favouritesAdded: Int,
        val listsAdded: Int,
        val failed: Int,
    ) {
        val changed: Int get() = favouritesAdded + listsAdded
    }

    /** Returns null when not signed in. */
    suspend fun syncToAniList(): Result? {
        if (!aniList.isLoggedIn.value) return null
        val entries = local.snapshot()
        var favourites = 0
        var lists = 0
        var failed = 0
        for (entry in entries) {
            val remote = aniList.getUserState(entry.anilistId)
            throttle()
            if (remote == null) { failed++; continue }

            if (entry.isFavourite && !remote.isFavourite) {
                if (aniList.toggleFavourite(entry.anilistId)) favourites++ else failed++
                throttle()
            }
            if (entry.status != null && remote.listStatus == null) {
                val progress = entry.progress.takeIf { it > 0 }
                if (aniList.setListStatus(entry.anilistId, entry.status, progress)) lists++ else failed++
                throttle()
            }
        }
        return Result(checked = entries.size, favouritesAdded = favourites, listsAdded = lists, failed = failed)
    }

    /** AniList allows about 90 requests a minute; stay comfortably under it. */
    private suspend fun throttle() = delay(700)
}
