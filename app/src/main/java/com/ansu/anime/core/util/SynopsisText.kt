package com.ansu.anime.core.util

/** One displayable piece of an anime synopsis. */
sealed class SynopsisBlock {
    /** A run of prose, shown as its own paragraph. */
    data class Paragraph(val text: String) : SynopsisBlock()

    /** A lead-in line ending in a colon, e.g. "This includes the following special episodes:". */
    data class Label(val text: String) : SynopsisBlock()

    /** One entry of a "- item" list. */
    data class Bullet(val text: String) : SynopsisBlock()

    /** A credit line such as "(Source: MAL Rewrite)". */
    data class Source(val text: String) : SynopsisBlock()
}

private val SYN_LINE_BREAK = Regex("<\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE)
private val SYN_PARAGRAPH_END = Regex("<\\s*/\\s*p\\s*>", RegexOption.IGNORE_CASE)
private val SYN_HTML_TAG = Regex("<[^>]*>")
private val SYN_MD_LINK = Regex("""\[([^\]]*)]\([^)\s]*\)""")
private val SYN_SPOILER = Regex("""~!|!~""")
private val SYN_BOLD = Regex("""(__|\*\*)(.+?)\1""")
private val SYN_SPACES = Regex("""[ \t]+""")
private val SYN_BULLET = Regex("""^\s*(?:[-\u2013\u2014\u2022\u00B7]|\*(?=\s))\s+(.+)$""")
private val SYN_SOURCE = Regex("""^\s*[(\[]\s*(?:source|written by|rewrite)\b.*[)\]]\s*$""", RegexOption.IGNORE_CASE)

private const val SYN_SPLIT_ABOVE = 380
private const val SYN_TARGET = 300
private const val SYN_CLOSERS = "\"\u201D')]"
private const val SYN_OPENERS = "\"\u201C'(["
private val SYN_ABBREVIATIONS = setOf("mr", "mrs", "ms", "dr", "st", "vs", "jr", "sr", "no", "vol", "ep")

/**
 * Turns an AniList synopsis into tidy blocks. AniList descriptions carry HTML (`<br>`, `<i>`) and
 * Markdown; each line is its own block (a `<br>` is a real paragraph break there), "- item" lines
 * become bullets, a line ending in ":" becomes a label, "(Source: ...)" becomes a credit line, and
 * a long paragraph is split at sentence ends into short ones so it never reads as one wall of text.
 */
fun parseSynopsis(raw: String?): List<SynopsisBlock> {
    if (raw.isNullOrBlank()) return emptyList()
    var text = raw.replace("\r\n", "\n").replace('\r', '\n')
    text = SYN_PARAGRAPH_END.replace(SYN_LINE_BREAK.replace(text, "\n"), "\n\n")
    text = SYN_HTML_TAG.replace(text, "")

    val blocks = mutableListOf<SynopsisBlock>()
    for (line in text.split('\n')) {
        if (line.isBlank()) continue
        val bullet = SYN_BULLET.matchEntire(line)
        when {
            bullet != null -> synClean(bullet.groupValues[1]).takeIf { it.isNotEmpty() }?.let { blocks += SynopsisBlock.Bullet(it) }
            SYN_SOURCE.matches(line) -> synClean(line).takeIf { it.isNotEmpty() }?.let { blocks += SynopsisBlock.Source(it) }
            else -> {
                // A leading "*" marks a footnote-style line ("*This includes ..."); the star itself is noise.
                val cleaned = synClean(line.trim().trimStart('*'))
                when {
                    cleaned.isEmpty() -> Unit
                    cleaned.endsWith(":") && cleaned.length <= 120 -> blocks += SynopsisBlock.Label(cleaned)
                    else -> synSplitLong(cleaned).forEach { blocks += SynopsisBlock.Paragraph(it) }
                }
            }
        }
    }
    return blocks
}

private fun synClean(input: String): String {
    var t = SYN_MD_LINK.replace(input) { it.groupValues[1] }
    t = SYN_SPOILER.replace(t, "")
    t = SYN_BOLD.replace(t) { it.groupValues[2] }
    t = t.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#039;", "'").replace("&#39;", "'").replace("&nbsp;", " ")
    return SYN_SPACES.replace(t, " ").trim()
}

private fun synSplitLong(text: String): List<String> {
    if (text.length <= SYN_SPLIT_ABOVE) return listOf(text)
    val result = mutableListOf<String>()
    val current = StringBuilder()
    for (sentence in synSentences(text)) {
        if (current.isNotEmpty() && current.length + sentence.length + 1 > SYN_TARGET) {
            result += current.toString()
            current.clear()
        }
        if (current.isNotEmpty()) current.append(' ')
        current.append(sentence)
    }
    if (current.isNotEmpty()) result += current.toString()
    return result
}

/** Splits at ". " / "! " / "? " followed by a capital letter or opening quote, skipping "Mr." and initials like "D.". */
private fun synSentences(text: String): List<String> {
    val sentences = mutableListOf<String>()
    var start = 0
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '.' || c == '!' || c == '?' || c == '\u2026') {
            var end = i + 1
            while (end < text.length && text[end] in SYN_CLOSERS) end++
            if (end < text.length && text[end] == ' ') {
                var next = end
                while (next < text.length && text[next] == ' ') next++
                val startsSentence = next < text.length && (text[next].isUpperCase() || text[next] in SYN_OPENERS)
                if (startsSentence && !synEndsWithAbbreviation(text, i)) {
                    sentences += text.substring(start, end).trim()
                    start = next
                    i = next
                    continue
                }
            }
            i = end
        } else {
            i++
        }
    }
    if (start < text.length) sentences += text.substring(start).trim()
    return sentences.filter { it.isNotEmpty() }
}

private fun synEndsWithAbbreviation(text: String, punctuationIndex: Int): Boolean {
    if (text[punctuationIndex] != '.') return false
    var wordStart = punctuationIndex
    while (wordStart > 0 && !text[wordStart - 1].isWhitespace()) wordStart--
    val word = text.substring(wordStart, punctuationIndex).lowercase()
    return word.length == 1 || word in SYN_ABBREVIATIONS
}
