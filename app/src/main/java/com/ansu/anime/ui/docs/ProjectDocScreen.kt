@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.docs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ansu.anime.data.docs.ProjectDoc
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.theme.AnsuColors

/**
 * The changelog and the licence, read inside the app the way the Contributors screen reads its list:
 * live from the project's repository on GitHub (see [com.ansu.anime.data.docs.ProjectDocsRepository]),
 * with the copy from the last launch when there is no connection and a refresh button for a forced
 * re-read.
 *
 * `CHANGELOG.md` is Markdown and is drawn with the handful of rules the file actually uses —
 * headings, bullets and `**bold**` — instead of a full Markdown engine, which would be a dependency
 * for one screen. The licence is plain text wrapped at 70-odd columns, so its lines are joined back
 * into paragraphs and the screen does the wrapping: left edge straight, no short line per block.
 */
@Composable
fun ProjectDocScreen(doc: ProjectDoc, container: AppContainer, navController: NavHostController) {
    var text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(doc, reload) {
        loading = true
        text = container.projectDocs.load(doc, force = reload > 0)
        loading = false
    }

    val blocks = remember(text) { parseDoc(text, markdown = doc == ProjectDoc.CHANGELOG) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = AnsuColors.TextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { reload++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = if (doc == ProjectDoc.CHANGELOG) {
                        "Read live from the project's repository, newest first."
                    } else {
                        "Anisu is free software, released under the GNU General Public License v3. The full text follows."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnsuColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            if (loading && blocks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = AnsuColors.Accent)
                    }
                }
            }

            items(blocks) { block -> DocBlockView(block) }

            if (!loading && blocks.isEmpty()) {
                item {
                    Text(
                        text = "Couldn't load the ${doc.title.lowercase()} right now. Tap the refresh button to try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AnsuColors.TextSecondary,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }
    }
}

/** One parsed piece of a document; the whole file is turned into these once per load. */
private sealed interface DocBlock {
    data class Heading(val text: String, val level: Int) : DocBlock
    data class Bullet(val text: String, val nested: Boolean) : DocBlock
    data class Paragraph(val text: String) : DocBlock
}

@Composable
private fun DocBlockView(block: DocBlock) {
    when (block) {
        is DocBlock.Heading -> Text(
            text = block.text,
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            },
            color = if (block.level <= 2) AnsuColors.TextPrimary else AnsuColors.Accent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = if (block.level <= 2) 12.dp else 4.dp),
        )

        is DocBlock.Bullet -> Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (block.nested) "◦" else "•",
                style = MaterialTheme.typography.bodyMedium,
                color = AnsuColors.Accent,
                modifier = Modifier.padding(start = if (block.nested) 16.dp else 2.dp, end = 8.dp),
            )
            Text(
                text = emphasis(block.text),
                style = MaterialTheme.typography.bodyMedium,
                color = AnsuColors.TextSecondary,
            )
        }

        is DocBlock.Paragraph -> Text(
            text = emphasis(block.text),
            style = MaterialTheme.typography.bodyMedium,
            color = AnsuColors.TextSecondary,
        )
    }
}

/**
 * Turns the file into blocks. Markdown mode understands the syntax `CHANGELOG.md` is written in;
 * plain mode takes every blank-line-separated block as one paragraph, which is what the licence is.
 */
private fun parseDoc(text: String, markdown: Boolean): List<DocBlock> {
    if (text.isBlank()) return emptyList()
    val blocks = mutableListOf<DocBlock>()
    val paragraph = StringBuilder()
    // The bullet an indented line continues, or -1 once the line above was not a bullet.
    var bullet = -1

    fun flush() {
        if (paragraph.isNotEmpty()) {
            blocks += DocBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
        bullet = -1
    }

    text.lineSequence().forEach { raw ->
        val line = raw.trimEnd()
        if (line.isBlank()) {
            flush()
            return@forEach
        }
        if (!markdown) {
            // Plain text (the licence) is hard-wrapped at about 70 columns in the file, so its lines
            // are joined back into paragraphs at the blank lines: one block per paragraph, laid out
            // by the screen, instead of one short block per line with its own indentation.
            if (paragraph.isNotEmpty()) paragraph.append(' ')
            paragraph.append(line.trim())
            return@forEach
        }
        when {
            line.startsWith("#") -> {
                flush()
                val level = line.takeWhile { it == '#' }.length
                blocks += DocBlock.Heading(line.drop(level).trim(), level)
            }
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                flush()
                val nested = line.startsWith("  ")
                blocks += DocBlock.Bullet(line.trimStart().drop(2).trim(), nested)
                bullet = blocks.lastIndex
            }
            line.startsWith("|") || line.startsWith("```") || line.startsWith("<!--") -> flush()
            // An indented line under a bullet is the rest of that bullet, not a paragraph of its own.
            line.startsWith(" ") && bullet >= 0 -> {
                val previous = blocks[bullet] as DocBlock.Bullet
                blocks[bullet] = previous.copy(text = previous.text + " " + line.trim())
            }
            else -> {
                bullet = -1
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line.trim())
            }
        }
    }
    flush()
    return blocks
}

/** `**bold**` into a real bold span, backticks dropped; anything else is left as it reads. */
private fun emphasis(text: String): AnnotatedString = buildAnnotatedString {
    val plain = text.replace("`", "")
    var index = 0
    while (index < plain.length) {
        val start = plain.indexOf("**", index)
        val end = if (start < 0) -1 else plain.indexOf("**", start + 2)
        if (start < 0 || end < 0) {
            append(plain.substring(index))
            break
        }
        append(plain.substring(index, start))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(plain.substring(start + 2, end))
        }
        index = end + 2
    }
}
