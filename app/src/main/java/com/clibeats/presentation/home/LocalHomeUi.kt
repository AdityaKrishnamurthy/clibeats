@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "ForbiddenImport", "MagicNumber")

package com.clibeats.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clibeats.data.home.LocalSetup
import com.clibeats.presentation.theme.CliBeatsBorderInactive
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor

@Composable
internal fun LocalSetupCard(
    setup: LocalSetup,
    onChooseFolder: () -> Unit,
) {
    val accent = LocalAccentColor.current
    val scanning = setup.scanning
    val copy = setupCopy(setup)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(CliBeatsSurface)
                .border(1.dp, accent, RoundedCornerShape(6.dp))
                .padding(16.dp),
    ) {
        Text(copy.command, style = MaterialTheme.typography.labelSmall, color = accent)
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (scanning != null) {
                CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = copy.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(copy.body, style = MaterialTheme.typography.bodySmall, color = CliBeatsTextSecondary)
        if (copy.button.isNotEmpty()) {
            Text(
                text = copy.button,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                modifier = Modifier.padding(top = 8.dp).clickable(onClick = onChooseFolder).padding(vertical = 8.dp),
            )
        }
    }
}

private data class SetupCopy(
    val command: String,
    val title: String,
    val body: String,
    val button: String,
)

private fun setupCopy(setup: LocalSetup): SetupCopy {
    val scanning = setup.scanning
    return when {
        scanning != null ->
            SetupCopy(
                "> local --scan",
                "Scanning your music…",
                if (scanning.total > 0) "${scanning.processed} of ${scanning.total} files" else "Looking for songs",
                "",
            )
        setup.failure != null ->
            SetupCopy(
                "> local --error",
                "Couldn't read your music folder",
                "${setup.failure} Choose the folder again to give CLIBeats access.",
                "[ CHOOSE FOLDER AGAIN ]",
            )
        setup.folderCount > 0 ->
            SetupCopy(
                "> local --empty",
                "No music found in your folder",
                "CLIBeats looked for audio files (MP3, FLAC, M4A, OGG, WAV…) in it and its subfolders " +
                    "and found none. Pick the folder that has your songs.",
                "[ CHOOSE ANOTHER FOLDER ]",
            )
        else ->
            SetupCopy(
                "> local --setup",
                "No local music yet",
                "Pick the folder with your music. CLIBeats reads the songs in it (and its subfolders), and " +
                    "Home fills with mixes, albums and artists from your library. Works offline.",
                "[ CHOOSE MUSIC FOLDER ]",
            )
    }
}

@Composable
internal fun LocalQuickActions(
    songCount: Int,
    albumCount: Int,
    onShuffleAll: () -> Unit,
    onSurpriseAlbum: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickAction("⇄ SHUFFLE ALL", "$songCount songs", enabled = songCount > 0, onClick = onShuffleAll)
        QuickAction("✦ SURPRISE ALBUM", "$albumCount albums", enabled = albumCount > 0, onClick = onSurpriseAlbum)
    }
}

@Composable
private fun RowScope.QuickAction(
    label: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val accent = LocalAccentColor.current
    Column(
        modifier =
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(CliBeatsSurface)
                .border(1.dp, if (enabled) accent else CliBeatsBorderInactive, RoundedCornerShape(6.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) accent else CliBeatsTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = detail, style = MaterialTheme.typography.labelSmall, color = CliBeatsTextSecondary, maxLines = 1)
    }
}
