package com.ansu.anime.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansu.anime.anilist.AniListCharacter
import com.ansu.anime.anilist.AniListStaffMember
import com.ansu.anime.ui.components.PersonCard
import com.ansu.anime.ui.theme.StreamHubColors

@Composable
fun CharacterStaffSheet(
    characters: List<AniListCharacter>,
    staff: List<AniListStaffMember>,
    onCharacterClick: (Int) -> Unit = {},
    onStaffClick: (Int) -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            if (characters.isNotEmpty()) {
                Text(
                    text = "Characters",
                    color = StreamHubColors.TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                CharacterRow(characters = characters, onClick = onCharacterClick)
            }
            HorizontalDivider(
                color = StreamHubColors.TextTertiary.copy(alpha = 0.25f),
                modifier = Modifier.padding(vertical = 12.dp),
            )
            if (staff.isNotEmpty()) {
                Text(
                    text = "Staff",
                    color = StreamHubColors.TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                StaffRow(staff = staff, onClick = onStaffClick)
            }
        }
    }
}

@Composable
private fun CharacterRow(characters: List<AniListCharacter>, onClick: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(characters, key = { it.id }) { character ->
            PersonCard(
                imageUrl = character.imageUrl,
                name = character.name,
                role = character.role,
                onClick = { onClick(character.id) },
            )
        }
    }
}

@Composable
private fun StaffRow(staff: List<AniListStaffMember>, onClick: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(staff, key = { it.id }) { member ->
            PersonCard(
                imageUrl = member.imageUrl,
                name = member.name,
                role = member.role,
                onClick = { onClick(member.id) },
            )
        }
    }
}
