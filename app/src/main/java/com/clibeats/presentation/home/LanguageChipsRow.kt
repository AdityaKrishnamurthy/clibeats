@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "ForbiddenImport", "MagicNumber")

package com.clibeats.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clibeats.data.home.HomeLanguage
import com.clibeats.domain.model.HomeMood
import com.clibeats.presentation.theme.CliBeatsBorderInactive
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor

@Composable
internal fun LanguageChipsRow(
    moods: List<HomeMood>,
    selected: HomeMood?,
    actions: FeedActions,
    languagesLabel: String?,
) {
    val accent = LocalAccentColor.current
    val onSelect = actions.selectMood
    LazyRow(
        modifier = Modifier.fillMaxWidth().keepSwipesInRow(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        if (languagesLabel != null) {
            item(key = "languages") {
                Text(
                    text = "\u270e " + languagesLabel.ifBlank { "All languages" },
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .widthIn(max = LANGUAGE_CHIP_MAX)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, accent, RoundedCornerShape(4.dp))
                            .clickable(onClickLabel = "Choose languages", onClick = actions.language.edit)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        items(moods, key = { it.title }) { mood ->
            val on = mood == selected
            Text(
                text = mood.title,
                style = MaterialTheme.typography.labelMedium,
                color = if (on) MaterialTheme.colorScheme.background else accent,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (on) accent else CliBeatsSurface)
                        .border(1.dp, if (on) accent else CliBeatsBorderInactive, RoundedCornerShape(4.dp))
                        .clickable { onSelect(mood) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
internal fun LanguageHintCard(actions: FeedActions) {
    val accent = LocalAccentColor.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .background(CliBeatsSurface)
                .border(1.dp, accent, RoundedCornerShape(6.dp))
                .padding(12.dp),
    ) {
        Text("> home --languages", style = MaterialTheme.typography.labelSmall, color = accent)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text =
                "Pick the languages you listen to: Home hides the rest and adds songs, playlists and " +
                    "charts in yours.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = "[ PICK LANGUAGES ]",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                modifier = Modifier.clickable(onClick = actions.language.edit).padding(vertical = 8.dp),
            )
            Text(
                text = "[ NOT NOW ]",
                style = MaterialTheme.typography.labelMedium,
                color = CliBeatsTextSecondary,
                modifier = Modifier.clickable(onClick = actions.language.skip).padding(vertical = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LanguagePickerDialog(
    current: Set<HomeLanguage>,
    onDone: (Set<HomeLanguage>) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalAccentColor.current
    var picked by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, accent),
        title = { Text("> home --languages") },
        text = {
            Column {
                Text(
                    text =
                        "Home shows songs, playlists and charts in these languages and hides others. " +
                            "Pick none to see everything.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CliBeatsTextSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HomeLanguage.ordered.forEach { language ->
                        val on = language in picked
                        Text(
                            text = if (on) "\u2713 ${language.label}" else language.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) MaterialTheme.colorScheme.background else accent,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (on) accent else CliBeatsSurface)
                                    .border(1.dp, if (on) accent else CliBeatsBorderInactive, RoundedCornerShape(4.dp))
                                    .clickable { picked = if (on) picked - language else picked + language }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDone(picked)
                onDismiss()
            }) { Text(if (picked.isEmpty()) "Show all" else "Done", color = accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = CliBeatsTextSecondary) }
        },
    )
}

private val LANGUAGE_CHIP_MAX: Dp = 220.dp
