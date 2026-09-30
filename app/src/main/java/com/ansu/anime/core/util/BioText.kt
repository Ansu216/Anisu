package com.ansu.anime.core.util

/** One displayable piece of a character or staff biography. */
sealed class BioBlock {
    /** A "Height: 188 cm" style line, shown as a label/value row. */
    data class Fact(val label: String, val value: String) : BioBlock()

    /** A run of prose, shown as its own paragraph. */
    data class Paragraph(val text: String) : BioBlock()
}

private val LINE_BREAK_TAG = Regex("<\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE)
private val HTML_TAG = Regex("<[^>]*>")
private val MD_LINK = Regex("""\[([^\]]*)]\([^)\s]*\)""")
private val BARE_URL = Regex("""https?://\S+""")
private val SPOILER_MARK = Regex("""~!|!~""")
private val STRIKE_MARK = Regex("""~~""")
private val BOLD = Regex("""(__|\*\*)(.+?)\1""")
private val ITALIC = Regex("""(?<![\w*_])([_*])(?=\S)(.+?)(?<=\S)\1(?![\w*_])""")
private val HEADING_MARK = Regex("""^\s*#{1,6}\s*""")
private val SPACES = Regex("""[ \t]+""")

/** `__Height:__ 188 cm` or `**Height:** 188 cm`: a bold label followed by its value. */
private val FACT_LINE = Regex("""^\s*(?:__|\*\*)\s*([^_*:\n]{1,40}?)\s*:?\s*(?:__|\*\*)\s*:?\s*(.*)$""")

private const val PARAGRAPH_SPLIT_ABOVE = 420
private const val PARAGRAPH_TARGET = 320
private const val SENTENCE_CLOSERS = "\"\u201D')]"
private const val SENTENCE_OPENERS = "\"\u201C'(["
private val ABBREVIATIONS = setOf("mr", "mrs", "ms", "dr", "st", "vs", "jr", "sr", "no", "vol", "ep")

/**
 * Turns an AniList biography into clean blocks. AniList descriptions carry Markdown and HTML
 * (`__Label:__`, `~!spoiler!~`, `[name](https://anilist.co/...)`, `<br>`), which would otherwise be
 * printed literally. This removes the markup and the links (keeping the link text), turns bold
 * "Label: value" lines into [BioBlock.Fact] rows, and lays the prose out as short paragraphs: blank
 * lines separate paragraphs, and a paragraph over ~400 characters is split at sentence ends.
 */
fun parseBio(raw: String?): List<BioBlock> {
    if (raw.isNullOrBlank()) return emptyList()
    val text = HTML_TAG.replace(LINE_BREAK_TAG.replace(raw.replace("\r\n", "\n").replace('\r', '\n'), "\n"), "")

    val blocks = mutableListOf<BioBlock>()
    val paragraph = StringBuilder()
    fun flushParagraph() {
        val cleaned = cleanInline(paragraph.toString())
        paragraph.clear()
        if (cleaned.isNotEmpty()) splitLongParagraph(cleaned).forEach { blocks += BioBlock.Paragraph(it) }
    }

    for (line in text.split('\n')) {
        val fact = FACT_LINE.matchEntire(line)
        when {
            fact != null -> {
                flushParagraph()
                val label = cleanInline(fact.groupValues[1])
                val value = cleanInline(fact.groupValues[2])
                if (label.isNotEmpty() && value.isNotEmpty()) blocks += BioBlock.Fact(label, value)
            }
            line.isBlank() -> flushParagraph()
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line.trim())
            }
        }
    }
    flushParagraph()
    return blocks
}

/** Strips Markdown/HTML decoration from one run of text and tidies its whitespace. */
private fun cleanInline(input: String): String {
    var t = HEADING_MARK.replace(input, "")
    t = MD_LINK.replace(t) { it.groupValues[1] }
    t = BARE_URL.replace(t, "")
    t = SPOILER_MARK.replace(t, "")
    t = STRIKE_MARK.replace(t, "")
    t = BOLD.replace(t) { it.groupValues[2] }
    t = ITALIC.replace(t) { it.groupValues[2] }
    t = t.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#039;", "'").replace("&#39;", "'").replace("&nbsp;", " ")
    return SPACES.replace(t, " ").trim()
}

private fun splitLongParagraph(text: String): List<String> {
    if (text.length <= PARAGRAPH_SPLIT_ABOVE) return listOf(text)
    val result = mutableListOf<String>()
    val current = StringBuilder()
    for (sentence in splitSentences(text)) {
        if (current.isNotEmpty() && current.length + sentence.length + 1 > PARAGRAPH_TARGET) {
            result += current.toString()
            current.clear()
        }
        if (current.isNotEmpty()) current.append(' ')
        current.append(sentence)
    }
    if (current.isNotEmpty()) result += current.toString()
    return result
}

/** Splits at ". " / "! " / "? " followed by a capital letter or opening quote, skipping "Mr." and initials. */
private fun splitSentences(text: String): List<String> {
    val sentences = mutableListOf<String>()
    var start = 0
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '.' || c == '!' || c == '?' || c == '\u2026') {
            var end = i + 1
            while (end < text.length && text[end] in SENTENCE_CLOSERS) end++
            if (end < text.length && text[end] == ' ') {
                var next = end
                while (next < text.length && text[next] == ' ') next++
                val startsSentence = next < text.length && (text[next].isUpperCase() || text[next] in SENTENCE_OPENERS)
                if (startsSentence && !endsWithAbbreviation(text, i)) {
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

private fun endsWithAbbreviation(text: String, punctuationIndex: Int): Boolean {
    if (text[punctuationIndex] != '.') return false
    var wordStart = punctuationIndex
    while (wordStart > 0 && !text[wordStart - 1].isWhitespace()) wordStart--
    val word = text.substring(wordStart, punctuationIndex).lowercase()
    return word.length == 1 || word in ABBREVIATIONS
}
