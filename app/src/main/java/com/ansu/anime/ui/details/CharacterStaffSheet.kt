package com.ansu.anime.ui.details

import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ansu.anime.anilist.AniListCharacter
import com.ansu.anime.anilist.AniListStaffMember
import com.ansu.anime.ui.theme.AnisuSurfaceRaised
import com.ansu.anime.ui.theme.AnisuTextSecondary

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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())) {
            Row {
                AsyncImage(
                    model = person.imageUrl,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(100.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AnisuSurfaceRaised),
                )
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(person.name, style = MaterialTheme.typography.titleMedium)
                    Text(person.subtitle, style = MaterialTheme.typography.bodyMedium, color = AnisuTextSecondary, modifier = Modifier.padding(top = 4.dp))
                }
            }
            person.bio
                ?.replace(Regex("<[^>]*>"), "")
                ?.takeIf { it.isNotBlank() }
                ?.let { bio ->
                    Text(
                        bio,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                    )
                } ?: run {
                Text(
                    "No further bio available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnisuTextSecondary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                )
            }
        }
    }
}
