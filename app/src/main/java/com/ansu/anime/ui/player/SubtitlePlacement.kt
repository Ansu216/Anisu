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

/**
 * Gives every text cue a background with rounded corners, one rounded bar behind each line of text.
 *
 * The player's own subtitle view can only paint a square background behind the text. So the cue's text is reduced to
 * plain words (the person's colours and size are applied by the view, see the player screen) and a span that draws
 * the rounded bar is put over it. Picture cues (bitmap subtitles) are left as they are. With a fully transparent
 * background no bar is added, so the outline-only look still works.
 *
 * @param backgroundArgb the background colour with the person's opacity already in it.
 * @param textSizePx the subtitle text size in pixels, which sets the padding and the corner radius.
 */
internal fun roundCueBackgrounds(cues: List<Cue>, backgroundArgb: Int, textSizePx: Float, rounded: Boolean = true): List<Cue> {
    val visible = android.graphics.Color.alpha(backgroundArgb) != 0
    return cues.map { cue ->
        val text = cue.text
        if (cue.bitmap != null || text.isNullOrEmpty()) return@map cue
        // Always reduced to plain words with no window colour, so the file's own styling never gets through; the bar is
        // only added when the person's background is not fully transparent.
        val plain = android.text.SpannableString(text.toString())
        if (visible && !rounded) {
            // Plain look: one ordinary rectangle behind the whole cue, drawn by the view from the cue's window colour.
            return@map cue.buildUpon().setText(plain).setWindowColor(backgroundArgb).build()
        }
        if (visible) {
            plain.setSpan(
                RoundedLineBackgroundSpan(backgroundArgb, textSizePx, cue.textAlignment),
                0,
                plain.length,
                android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE,
            )
        }
        cue.buildUpon().setText(plain).clearWindowColor().build()
    }
}

/**
 * Draws a rounded bar behind one line of text, as wide as that line plus a little padding, so a two-line cue gets two
 * bars of their own widths. The line is positioned the way the cue aligns its text (centred unless the cue says otherwise).
 */
private class RoundedLineBackgroundSpan(
    private val color: Int,
    private val textSizePx: Float,
    private val alignment: android.text.Layout.Alignment?,
) : android.text.style.LineBackgroundSpan {

    private val rect = android.graphics.RectF()

    override fun drawBackground(
        canvas: android.graphics.Canvas,
        paint: android.graphics.Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int,
    ) {
        // The line break that ends a line has no width worth a bar.
        var lineEnd = end
        while (lineEnd > start && text[lineEnd - 1].isWhitespace()) lineEnd--
        if (lineEnd <= start) return

        val textWidth = paint.measureText(text, start, lineEnd)
        val padX = textSizePx * 0.5f
        val barWidth = textWidth + padX * 2f
        val barLeft = when (alignment) {
            android.text.Layout.Alignment.ALIGN_NORMAL -> left.toFloat()
            android.text.Layout.Alignment.ALIGN_OPPOSITE -> right - barWidth
            else -> (left + right) / 2f - barWidth / 2f
        }
        // A hair of overlap, so the bars of a two-line cue touch instead of leaving a gap between them.
        rect.set(barLeft, top.toFloat(), barLeft + barWidth, bottom.toFloat() + 1f)

        val previousColor = paint.color
        val previousStyle = paint.style
        paint.color = color
        paint.style = android.graphics.Paint.Style.FILL
        // Gently rounded corners (a soft rectangle rather than a pill), as in the design sketch.
        val radius = (bottom - top) * 0.16f
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.color = previousColor
        paint.style = previousStyle
    }
}
