package com.ansu.anime.ui.player

import androidx.media3.common.text.Cue

/**
 * Places the cues on screen so two that are showing at the same moment never sit on top of each other.
 *
 * Every ordinary (bottom) text cue used to be forced to the same line, so when a character's dialogue and a
 * translated on-screen sign were on screen together they were drawn on the very same spot. Now:
 *  - a cue the subtitle file deliberately placed away from the bottom (a sign at the top or middle of the picture)
 *    keeps its own position;
 *  - the remaining cues are stacked upward from the chosen subtitle height, the first one lowest, each one
 *    clear of the one beneath it.
 *
 * @param heightPercent the person's subtitle height setting (bottom edge, percent of the video height).
 * @param textSizePx the subtitle text size in pixels, used to estimate how tall a cue is.
 * @param areaHeightPx the height of the subtitle area in pixels (0 while it is not measured yet).
 */
internal fun placeCues(
    cues: List<Cue>,
    heightPercent: Int,
    textSizePx: Float,
    areaHeightPx: Int,
): List<Cue> {
    val baseBottom = 1f - heightPercent / 100f
    val stacked = cues.count { it.bitmap == null && !it.hasOwnPlacement() }
    // Estimated height of one text line (with padding), as a fraction of the video height.
    val lineFraction = if (areaHeightPx > 0) textSizePx * 1.5f / areaHeightPx else 0.07f
    val gap = lineFraction * 0.25f
    var bottom = baseBottom
    return cues.map { cue ->
        when {
            cue.bitmap != null -> cue
            cue.hasOwnPlacement() -> cue
            else -> {
                val lines = (cue.text?.count { it == '\n' } ?: 0) + 1
                val placed = cue.buildUpon()
                    .setLine(bottom.coerceAtLeast(0.12f), Cue.LINE_TYPE_FRACTION)
                    .setLineAnchor(Cue.ANCHOR_TYPE_END)
                    .build()
                // Only a second cue needs room above the first, so a lone cue is placed exactly as before.
                if (stacked > 1) bottom -= lines * lineFraction + gap
                placed
            }
        }
    }
}

/** True when the subtitle file placed this cue itself somewhere other than the usual bottom spot. */
private fun Cue.hasOwnPlacement(): Boolean = when (lineType) {
    Cue.LINE_TYPE_FRACTION -> line != Cue.DIMEN_UNSET && line < 0.5f
    Cue.LINE_TYPE_NUMBER -> line != Cue.DIMEN_UNSET && line >= 0f
    else -> false
}
