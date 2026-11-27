@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "ForbiddenImport", "MagicNumber")

package com.clibeats.presentation.importer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clibeats.data.importer.ImportState
import com.clibeats.data.importer.ImportedSong
import com.clibeats.presentation.component.TrackArtwork
import com.clibeats.presentation.search.formatDuration
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsDestructive
import com.clibeats.presentation.theme.CliBeatsDivider
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary

@Suppress("LongMethod")
@Composable
internal fun ChoosePlaylistsStep(
    setup: ImportSetup,
    onToggle: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onAllowClosest: (Boolean) -> Unit,
    onImport: () -> Unit,
) {
    val found = setup.found.orEmpty()
    val songs = found.filterIndexed { i, _ -> i in setup.selected }.sumOf { it.songs.size }
    val allSelected = setup.selected.size == found.size
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (found.size == 1) "1 playlist in this file" else "${found.size} playlists in this file",
                style = MaterialTheme.typography.labelMedium,
                color = CliBeatsAccent,
                modifier = Modifier.weight(1f),
            )
            if (found.size > 1) {
                TextButton(onClick = { onSelectAll(!allSelected) }) {
                    Text(if (allSelected) "Select none" else "Select all", color = CliBeatsAccent)
                }
            }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(found.size, key = { it }) { i ->
                val playlist = found[i]
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Checkbox) { onToggle(i) }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = i in setup.selected,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = CliBeatsAccent),
                        modifier = Modifier.padding(12.dp),
                    )
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CliBeatsTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = songCount(playlist.songs.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = CliBeatsTextSecondary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = CliBeatsDivider)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Checkbox) { onAllowClosest(!setup.allowClosest) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = setup.allowClosest,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = CliBeatsAccent),
                modifier = Modifier.padding(12.dp),
            )
            Column {
                Text(
                    text = "Use the closest version",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CliBeatsTextPrimary,
                )
                Text(
                    text = "When YouTube Music doesn't have the exact one (e.g. a remix), add the original instead.",
                    style = MaterialTheme.typography.labelSmall,
                    color = CliBeatsTextSecondary,
                )
            }
        }
        Column(modifier = Modifier.padding(16.dp)) {
            CliButton(
                text =
                    if (setup.selected.isEmpty()) {
                        "Choose at least one playlist"
                    } else {
                        "Import ${playlistCount(setup.selected.size)} · ${songCount(songs)}"
                    },
                primary = true,
                enabled = setup.selected.isNotEmpty(),
                onClick = onImport,
            )
        }
    }
}

@Composable
internal fun ConflictDialog(
    names: List<String>,
    onReplace: () -> Unit,
    onKeepBoth: () -> Unit,
    onDismiss: () -> Unit,
) {
    val list = names.joinToString(", ")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (names.size == 1) "Playlist already exists" else "Playlists already exist") },
        text = {
            Text(
                "You already have $list. Replace ${if (names.size == 1) "it" else "them"} with the import, " +
                    "or keep both (the import gets a new name like \"${names.first()} (2)\")?",
            )
        },
        confirmButton = { TextButton(onClick = onReplace) { Text("REPLACE", color = CliBeatsDestructive) } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("CANCEL") }
                TextButton(onClick = onKeepBoth) { Text("KEEP BOTH") }
            }
        },
    )
}

@Suppress("LongMethod")
@Composable
internal fun ImportMatchingStep(
    state: ImportState.Running,
    onCancel: () -> Unit,
) {
    val playlist = state.playlists[state.current]
    val missed = playlist.missed
    val matched = playlist.matched.sortedByDescending { it.index }
    var confirmCancel by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (state.playlists.size > 1) {
                Text(
                    text = "Playlist ${state.current + 1} of ${state.playlists.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = CliBeatsTextSecondary,
                )
            }
            Text(
                text = "${playlist.name} · ${playlist.done} / ${playlist.total}",
                style = MaterialTheme.typography.titleMedium,
                color = CliBeatsTextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (playlist.total == 0) 0f else playlist.done.toFloat() / playlist.total },
                color = CliBeatsAccent,
                trackColor = CliBeatsDivider,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "✓ ${playlist.matched.size} found   ✗ ${missed.size} not matched",
                style = MaterialTheme.typography.labelMedium,
                color = CliBeatsTextSecondary,
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (missed.isNotEmpty()) {
                importSectionHeader("missed_h", "✗ NOT MATCHED · ${missed.size}", good = false)
                items(missed, key = { "m${it.index}" }) { SpotifyRow(it) }
            }
            if (matched.isNotEmpty()) {
                importSectionHeader("matched_h", "✓ MATCHED · ${matched.size}", good = true)
                items(matched, key = { "ok${it.index}" }) { MatchedRow(it) }
            }
        }
        Column(modifier = Modifier.padding(16.dp)) {
            CliButton(text = "Cancel import", primary = false) { confirmCancel = true }
        }
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Stop the import?") },
            text = { Text("Songs found so far in ${playlist.name} are still saved. Playlists after it are skipped.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    onCancel()
                }) { Text("STOP", color = CliBeatsDestructive) }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("KEEP GOING") } },
        )
    }
}

internal fun LazyListScope.importSectionHeader(
    key: String,
    title: String,
    good: Boolean,
    action: Pair<String, () -> Unit>? = null,
) {
    item(key = key) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = if (good) CliBeatsAccent else CliBeatsDestructive,
                modifier = Modifier.weight(1f),
            )
            if (action != null) {
                Text(
                    text = action.first,
                    style = MaterialTheme.typography.labelMedium,
                    color = CliBeatsAccent,
                    modifier =
                        Modifier
                            .clickable(role = Role.Button, onClick = action.second)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
internal fun SpotifyRow(
    song: ImportedSong,
    onFind: (() -> Unit)? = null,
    /** Picked with Find: a green tick before Find. */
    found: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = CliBeatsTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    listOfNotNull(
                        song.song.artistLine.ifEmpty { null },
                        song.song.durationMs.takeIf { it > 0 }?.let(::formatDuration),
                        if (song.searchFailed) "no connection" else null,
                    ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = CliBeatsTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (found) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.titleMedium,
                color = CliBeatsAccent,
                modifier = Modifier.padding(horizontal = 4.dp).semantics { contentDescription = "Found" },
            )
        }
        if (onFind != null) {
            Text(
                text = "[ Find ]",
                style = MaterialTheme.typography.labelMedium,
                color = CliBeatsAccent,
                modifier =
                    Modifier
                        .clickable(role = Role.Button, onClickLabel = "Find ${song.song.title}", onClick = onFind)
                        .padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
internal fun MatchedRow(song: ImportedSong) {
    val track = song.match ?: return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArtwork(artworkUrl = track.artworkUrl, size = 36)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = CliBeatsTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val renamed = !track.title.equals(song.song.title, ignoreCase = true)
            val spotify = if (renamed) " · Spotify: ${song.song.title}" else ""
            Text(
                text =
                    track.artist + spotify +
                        (if (song.manual) " · picked by you" else "") +
                        (if (song.closest) " · closest version" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = CliBeatsTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun songCount(n: Int) = if (n == 1) "1 song" else "$n songs"

private fun playlistCount(n: Int) = if (n == 1) "1 playlist" else "$n playlists"
