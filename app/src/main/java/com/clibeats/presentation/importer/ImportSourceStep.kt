@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "MagicNumber")

package com.clibeats.presentation.importer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsDestructive
import com.clibeats.presentation.theme.CliBeatsDivider
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary

private const val EXPORTIFY_URL = "https://exportify.net"
private const val TUNEMYMUSIC_URL = "https://www.tunemymusic.com"

private enum class ImportSource(
    val label: String,
) {
    SPOTIFY("Spotify"),
    YOUTUBE_MUSIC("YouTube Music"),
    OTHER("Others"),
}

@Composable
internal fun ImportTitleBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "←",
            style = MaterialTheme.typography.titleLarge,
            color = CliBeatsAccent,
            modifier =
                Modifier
                    .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Text(
            text = "Import Playlists",
            style = MaterialTheme.typography.titleMedium,
            color = CliBeatsTextPrimary,
            modifier = Modifier.semantics { heading() },
        )
    }
    HorizontalDivider(thickness = 1.dp, color = CliBeatsDivider)
}

@Suppress("LongMethod")
@Composable
internal fun ImportSourceStep(
    reading: Boolean,
    error: String?,
    onChooseFile: () -> Unit,
) {
    val context = LocalContext.current
    var source by rememberSaveable { mutableStateOf(ImportSource.SPOTIFY) }
    val open: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text =
                    "Export your playlists as a file, then choose it here. CLIBeats finds each song on " +
                        "YouTube Music.",
                style = MaterialTheme.typography.bodyMedium,
                color = CliBeatsTextSecondary,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImportSource.entries.forEach { option ->
                    SourceTab(option.label, selected = option == source, modifier = Modifier.weight(1f)) {
                        source = option
                    }
                }
            }
        }
        when (source) {
            ImportSource.SPOTIFY -> {
                item { Step(1, "Open Exportify", "A free website. Log in with your Spotify account there.") }
                item { CliButton(text = "Open exportify.net ↗", primary = false) { open(EXPORTIFY_URL) } }
                item {
                    Step(
                        2,
                        "Export your playlists",
                        "Tap Export next to one playlist for a .csv file, or Export All at the top for a .zip " +
                            "with all of them.",
                    )
                }
                item { Step(3, "Choose the file here", "Pick the .csv or .zip from your Downloads.") }
                item {
                    Hint(
                        "Also works: a CSV from TuneMyMusic with Spotify as the source (see the YouTube Music " +
                            "steps, picking Spotify instead).",
                    )
                }
            }
            ImportSource.YOUTUBE_MUSIC, ImportSource.OTHER -> {
                val service =
                    if (source == ImportSource.OTHER) {
                        "your service (Apple Music, Amazon Music, Deezer, Tidal and more)"
                    } else {
                        "YouTube Music"
                    }
                item {
                    Step(1, "Open TuneMyMusic", "A free website. Choose $service as the source and sign in.")
                }
                item { CliButton(text = "Open tunemymusic.com ↗", primary = false) { open(TUNEMYMUSIC_URL) } }
                item {
                    Step(
                        2,
                        "Export to a file",
                        "Select your playlists, choose Export to file as the destination, and pick CSV. One file " +
                            "can hold all your playlists.",
                    )
                }
                item { Step(3, "Choose the file here", "Pick the .csv from your Downloads.") }
            }
        }
        item {
            if (reading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = CliBeatsAccent,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Reading the file…", style = MaterialTheme.typography.bodyMedium, color = CliBeatsTextPrimary)
                }
            } else {
                CliButton(text = "Choose File", primary = true, onClick = onChooseFile)
            }
        }
        if (error != null) {
            item { Text(text = "! $error", style = MaterialTheme.typography.bodyMedium, color = CliBeatsDestructive) }
        }
        item {
            Hint(
                "Songs are matched by title, artist, album and length. Any that aren't found are listed at the " +
                    "end, and you can pick them yourself.",
            )
        }
    }
}

@Composable
private fun SourceTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier =
            modifier
                .height(40.dp)
                .then(if (selected) Modifier.background(CliBeatsAccent, shape) else Modifier)
                .border(1.dp, if (selected) CliBeatsAccent else CliBeatsDivider, shape)
                .semantics { this.selected = selected }
                .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) CliBeatsBackground else CliBeatsTextPrimary,
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = CliBeatsTextSecondary)
}

@Composable
private fun Step(
    number: Int,
    title: String,
    body: String,
) {
    Row {
        Text(
            text = "$number",
            style = MaterialTheme.typography.titleMedium,
            color = CliBeatsAccent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp),
        )
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = CliBeatsTextPrimary)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = CliBeatsTextSecondary)
        }
    }
}

@Composable
internal fun CliButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val color = if (enabled) CliBeatsAccent else CliBeatsTextSecondary
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .then(if (primary && enabled) Modifier.background(CliBeatsAccent, shape) else Modifier)
                .border(1.dp, color, shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary && enabled) CliBeatsBackground else color,
        )
    }
}
