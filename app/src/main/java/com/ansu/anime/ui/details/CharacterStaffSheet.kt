@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ansu.anime.anilist.AniListCharacter
import com.ansu.anime.anilist.AniListStaffMember
import com.ansu.anime.core.util.BioBlock
import com.ansu.anime.core.util.parseBio
import com.ansu.anime.ui.theme.AnsuColors

/** What CharacterStaffSheet renders - either kind of person, normalized to the same shape. */
sealed class PersonDetail {
    abstract val name: String
    abstract val imageUrl: String?
    abstract val subtitle: String
    abstract val bio: String?

    data class Character(val source: AniListCharacter) : PersonDetail() {
        override val name get() = source.name
        override val imageUrl get() = source.imageUrl
        override val subtitle get() = listOfNotNull(source.role.lowercase().replaceFirstChar { it.uppercase() }, source.voiceActorName?.let { "VA: $it" })
            .joinToString(" · ")
        override val bio get() = source.description
    }

    data class Staff(val source: AniListStaffMember) : PersonDetail() {
        override val name get() = source.name
        override val imageUrl get() = source.imageUrl
        override val subtitle get() = source.role
        override val bio get() = source.description
    }
}

@Composable
fun CharacterStaffSheet(person: PersonDetail, onDismiss: () -> Unit) {
    val blocks = remember(person) { parseBio(person.bio) }
    val facts = blocks.filterIsInstance<BioBlock.Fact>()
    val paragraphs = blocks.filterIsInstance<BioBlock.Paragraph>()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).verticalScroll(rememberScrollState())) {
            Row {
                AsyncImage(
                    model = person.imageUrl,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(100.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AnsuColors.BackgroundElevated),
                )
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(person.name, style = MaterialTheme.typography.titleMedium)
                    Text(person.subtitle, style = MaterialTheme.typography.bodyMedium, color = AnsuColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
                }
            }

            if (blocks.isEmpty()) {
                Text(
                    "No further bio available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnsuColors.TextSecondary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                )
            } else {
                if (facts.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AnsuColors.BackgroundElevated)
                            .padding(14.dp),
                    ) {
                        facts.forEach { fact ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = fact.label,
                                    color = AnsuColors.TextSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(0.34f),
                                )
                                Text(
                                    text = fact.value,
                                    color = AnsuColors.TextPrimary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.weight(0.66f),
                                )
                            }
                        }
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 24.dp),
                ) {
                    paragraphs.forEach { paragraph ->
                        Text(
                            text = paragraph.text,
                            color = AnsuColors.TextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                        )
                    }
                }
            }
        }
    }
}
