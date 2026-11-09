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
        if (visible) {
            // Rounded: the lines' bars are joined into one stepped, rounded shape. Plain (rounded off): one ordinary
            // rectangle hugging the widest line. Both are drawn by the span, so the box is only as wide as the text
            // (the view's own window colour would stretch across the whole video width).
            plain.setSpan(
                RoundedLineBackgroundSpan(backgroundArgb, textSizePx, cue.textAlignment, rounded),
                0,
                plain.length,
                android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE,
            )
        }
        cue.buildUpon().setText(plain).clearWindowColor().build()
    }
}

/**
 * Draws the background of a (possibly multi-line) cue as ONE shape that hugs the text:
 * - rounded: every line gets a bar as wide as that line plus padding; where two neighbouring bars meet, their shared
 *   corners are squared off so the lines join into a single stepped shape, and only the outer corners are rounded;
 * - plain: one rectangle as wide as the widest line, no rounding.
 * The bars are positioned the way the cue aligns its text (centred unless the cue says otherwise), and never reach
 * beyond the text, so a subtitle on a wide video no longer gets a bar across the whole screen.
 */
private class RoundedLineBackgroundSpan(
    private val color: Int,
    private val textSizePx: Float,
    private val alignment: android.text.Layout.Alignment?,
    private val rounded: Boolean,
) : android.text.style.LineBackgroundSpan {

    private val rect = android.graphics.RectF()
    private val path = android.graphics.Path()
    private val radii = FloatArray(8)

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

        val padX = textSizePx * 0.5f
        val spanned = text as? android.text.Spanned
        val spanStart = spanned?.getSpanStart(this)?.takeIf { it >= 0 } ?: start
        val spanEnd = spanned?.getSpanEnd(this)?.takeIf { it >= 0 } ?: end

        // The cue's lines (split at its line breaks) with their bar widths, to know the neighbours of this line.
        val widths = ArrayList<Float>()
        var index = -1
        var from = spanStart
        while (from <= spanEnd) {
            var to = from
            while (to < spanEnd && text[to] != '\n') to++
            var trimmed = to
            while (trimmed > from && text[trimmed - 1].isWhitespace()) trimmed--
            if (trimmed > from) {
                if (start in from..to) index = widths.size
                widths += paint.measureText(text, from, trimmed) + padX * 2f
            }
            if (to >= spanEnd) break
            from = to + 1
        }

        val own = paint.measureText(text, start, lineEnd) + padX * 2f
        val barWidth = if (!rounded) (widths.maxOrNull() ?: own) else own
        val barLeft = when (alignment) {
            android.text.Layout.Alignment.ALIGN_NORMAL -> left.toFloat()
            android.text.Layout.Alignment.ALIGN_OPPOSITE -> right - barWidth
            else -> (left + right) / 2f - barWidth / 2f
        }
        // A hair of overlap, so the bars of a two-line cue touch instead of leaving a gap between them.
        rect.set(barLeft, top.toFloat(), barLeft + barWidth, bottom.toFloat() + 1f)

        val radius = if (rounded) (bottom - top) * 0.16f else 0f
        // A corner is rounded only when it sticks out past the neighbouring line; a corner against a wider (or equal)
        // neighbour is square, which is what joins the lines into one shape.
        val outerTop = index <= 0 || barWidth > widths[index - 1] + 0.5f
        val outerBottom = index < 0 || index >= widths.lastIndex || barWidth > widths[index + 1] + 0.5f
        val top2 = if (outerTop) radius else 0f
        val bottom2 = if (outerBottom) radius else 0f
        radii[0] = top2; radii[1] = top2; radii[2] = top2; radii[3] = top2
        radii[4] = bottom2; radii[5] = bottom2; radii[6] = bottom2; radii[7] = bottom2

        path.rewind()
        path.addRoundRect(rect, radii, android.graphics.Path.Direction.CW)

        val previousColor = paint.color
        val previousStyle = paint.style
        paint.color = color
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawPath(path, paint)
        paint.color = previousColor
        paint.style = previousStyle
    }
}
