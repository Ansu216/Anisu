package com.ansu.anime.ui.sources

import com.ansu.anime.ui.player.PlayableSource

/**
 * What the source picker hands to the player: every stream it found for the episode, and the one the person chose.
 * The player plays [selected] straight away and shows [sources] in its own Sources panel, so it does not have to
 * ask every extension a second time.
 */
class SourcePick(
    val sources: List<PlayableSource>,
    val selected: PlayableSource,
)
