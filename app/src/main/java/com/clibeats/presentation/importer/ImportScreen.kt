@file:Suppress(
    "ktlint:standard:function-naming",
    "FunctionNaming",
    "LongMethod",
    "TooManyFunctions",
    "ForbiddenImport",
    "MagicNumber",
)

package com.clibeats.presentation.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clibeats.data.importer.ImportState
import com.clibeats.data.importer.ImportedSong
import com.clibeats.data.importer.PlaylistImport
import com.clibeats.domain.model.Track
import com.clibeats.presentation.component.TrackArtwork
import com.clibeats.presentation.search.formatDuration
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsDestructive
import com.clibeats.presentation.theme.CliBeatsDivider
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary

private const val EXPORTIFY_URL = "https://exportify.net"

/**
 * Import from Spotify: instructions → pick a file → choose playlists → live progress → summary with
 * the songs that couldn't be matched (each can be found by hand). [onClose] leaves the screen; a
 * running import keeps going in the background.
 */
@Composable
fun ImportScreen(
    onClose: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val importState by viewModel.importState.collectAsState()
    val setup by viewModel.setup.collectAsState()
    val manual by viewModel.manual.collectAsState()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::onFilePicked)
        }
    val choosingPlaylists = setup.found != null && importState is ImportState.Idle
    // Back steps out of the playlist list first, then closes the screen.
    BackHandler { if (choosingPlaylists) viewModel.reset() else onClose() }

    Column(modifier = Modifier.fillMaxSize().background(CliBeatsBackground)) {
        TitleBar(onBack = { if (choosingPlaylists) viewModel.reset() else onClose() })
        when (val s = importState) {
            is ImportState.Running -> ProgressView(s, onCancel = viewModel::cancel)
            is ImportState.Finished ->
                SummaryView(
                    state = s,
                    onFind = { p, song ->
                        val query = "${song.song.title} ${song.song.artists.firstOrNull().orEmpty()}".trim()
                        viewModel.openManual(p, song.index, query)
                    },
                    onDone = {
                        viewModel.done()
                        onClose()
                    },
                )
            ImportState.Idle ->
                if (setup.found != null) {
                    ChooseView(
                        setup = setup,
                        onToggle = viewModel::toggle,
                        onSelectAll = viewModel::selectAll,
                        onImport = viewModel::onImportClick,
                    )
                } else {
                    InstructionsView(
                        reading = setup.reading,
                        error = setup.error,
                        // Some file managers label .csv as text/plain or octet-stream, so accept anything and
                        // check the content instead.
                        onChooseFile = { picker.launch(arrayOf("*/*")) },
                    )
                }
        }
    }

    if (setup.conflicts.isNotEmpty()) {
        ConflictDialog(
            names = setup.conflicts,
            onReplace = { viewModel.start(replaceExisting = true) },
            onKeepBoth = { viewModel.start(replaceExisting = false) },
            onDismiss = viewModel::dismissConflicts,
        )
    }
    manual?.let { pick ->
        FindSheet(
            pick = pick,
            onSearch = viewModel::search,
            onPick = viewModel::pick,
            onDismiss = viewModel::closeManual,
        )
    }
}

@Composable
private fun TitleBar(onBack: () -> Unit) {
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
            text = "Import from Spotify",
            style = MaterialTheme.typography.titleMedium,
            color = CliBeatsTextPrimary,
            modifier = Modifier.semantics { heading() },
        )
    }
    HorizontalDivider(thickness = 1.dp, color = CliBeatsDivider)
}

// ── Step 1: instructions ────────────────────────────────────────────────────────

@Composable
private fun InstructionsView(
    reading: Boolean,
    error: String?,
    onChooseFile: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text =
                    "Bring your Spotify playlists into CLIBeats. Exportify saves them as a file, " +
                        "and CLIBeats finds each song on YouTube Music.",
                style = MaterialTheme.typography.bodyMedium,
                color = CliBeatsTextSecondary,
            )
        }
        item { Step(1, "Open Exportify", "It's a free website. Log in with your Spotify account there.") }
        item {
            CliButton(text = "Open exportify.net ↗", primary = false) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(EXPORTIFY_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        item {
            Step(
                2,
                "Export your playlists",
                "Tap Export next to one playlist to get a .csv file, or Export All at the top to get a " +
                    ".zip with all of them.",
            )
        }
        item { Step(3, "Choose the file here", "Pick the .csv or .zip from your Downloads.") }
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
            Text(
                text =
                    "Songs are matched by title, artist and length. A few may not be found or may be a " +
                        "different version; you'll see the ones that weren't found at the end and can " +
                        "pick them yourself.",
                style = MaterialTheme.typography.bodySmall,
                color = CliBeatsTextSecondary,
            )
        }
    }
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
private fun CliButton(
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

// ── Step 2: choose playlists ────────────────────────────────────────────────────

@Composable
private fun ChooseView(
    setup: ImportSetup,
    onToggle: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
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
private fun ConflictDialog(
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

// ── Step 3: progress ────────────────────────────────────────────────────────────

@Composable
private fun ProgressView(
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
                section("missed_h", "✗ NOT MATCHED · ${missed.size}", good = false)
                items(missed, key = { "m${it.index}" }) { SpotifyRow(it) }
            }
            if (matched.isNotEmpty()) {
                section("matched_h", "✓ MATCHED · ${matched.size}", good = true)
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

// ── Step 4: summary ─────────────────────────────────────────────────────────────

@Composable
private fun SummaryView(
    state: ImportState.Finished,
    onFind: (Int, ImportedSong) -> Unit,
    onDone: () -> Unit,
) {
    // Which playlist's lists are open; the first one with misses starts open.
    var open by rememberSaveable { mutableStateOf(state.playlists.indexOfFirst { it.missed.isNotEmpty() }) }
    val copy = rememberCopyAction()
    val withMisses = state.playlists.filter { it.missed.isNotEmpty() }
    val missedTotal = withMisses.sumOf { it.missed.size }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = if (state.cancelled) "Import stopped" else "Import finished",
            style = MaterialTheme.typography.titleMedium,
            color = CliBeatsTextPrimary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        Text(
            text =
                "Saved under Library → Playlists → Your Playlists. " +
                    "Tap Find on a song that wasn't matched to pick it yourself.",
            style = MaterialTheme.typography.bodySmall,
            color = CliBeatsTextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            state.playlists.forEachIndexed { i, playlist ->
                item(key = "p$i") {
                    SummaryHeader(playlist = playlist, expanded = open == i) { open = if (open == i) -1 else i }
                }
                if (open == i && !playlist.skipped) {
                    playlistDetails(i, playlist, onFind, onCopy = { copy(songList(it)) })
                }
            }
        }
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (missedTotal > 0) {
                // Every song that wasn't matched, as "Title — Artist" lines, to search for or keep a note of.
                CliButton(text = "Copy ${songCount(missedTotal)} not matched", primary = false) {
                    copy(
                        if (withMisses.size == 1) {
                            songList(withMisses.single().missed)
                        } else {
                            withMisses.joinToString("\n\n") { "${it.name}\n${songList(it.missed)}" }
                        },
                    )
                }
            }
            CliButton(text = "Done", primary = true, onClick = onDone)
        }
    }
}

@Composable
private fun SummaryHeader(
    playlist: PlaylistImport,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val missed = playlist.missed.size
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !playlist.skipped, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = CliBeatsTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    when {
                        playlist.skipped -> "Not imported (stopped before it)"
                        playlist.done < playlist.total ->
                            "${playlist.matched.size} found · stopped after ${playlist.done} of ${playlist.total}"
                        missed == 0 -> "All ${playlist.total} songs found"
                        else -> "${playlist.matched.size} of ${playlist.total} found · $missed not matched"
                    },
                style = MaterialTheme.typography.labelMedium,
                color = if (missed > 0 && !playlist.skipped) CliBeatsDestructive else CliBeatsTextSecondary,
            )
        }
        if (!playlist.skipped) {
            Text(
                text = if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.titleMedium,
                color = CliBeatsAccent,
            )
        }
    }
    HorizontalDivider(thickness = 1.dp, color = CliBeatsDivider)
}

private fun LazyListScope.playlistDetails(
    playlistIndex: Int,
    playlist: PlaylistImport,
    onFind: (Int, ImportedSong) -> Unit,
    onCopy: (List<ImportedSong>) -> Unit,
) {
    val missed = playlist.missed
    if (missed.isNotEmpty()) {
        section(
            "p${playlistIndex}_missed",
            "✗ COULDN'T MATCH · ${missed.size}",
            good = false,
            action = "[ Copy ]" to { onCopy(missed) },
        )
        items(missed, key = { "p${playlistIndex}_m${it.index}" }) { song ->
            SpotifyRow(song, onFind = { onFind(playlistIndex, song) })
        }
    }
    val matched = playlist.matched.sortedBy { it.index }
    if (matched.isNotEmpty()) {
        section("p${playlistIndex}_ok", "✓ MATCHED · ${matched.size}", good = true)
        items(matched, key = { "p${playlistIndex}_ok${it.index}" }) { MatchedRow(it) }
    }
}

// ── Rows ────────────────────────────────────────────────────────────────────────

private fun LazyListScope.section(
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
                // Read inside the item: the accent colour comes from the theme, which needs composition.
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

/** "the song — the artist" per line: plain text, ready to paste into a search box or a note. */
private fun songList(songs: List<ImportedSong>): String =
    songs.sortedBy { it.index }.joinToString("\n") { s ->
        if (s.song.artistLine.isEmpty()) s.song.title else "${s.song.title} — ${s.song.artistLine}"
    }

/** Copies text to the clipboard and confirms it (Android 13+ shows its own confirmation). */
@Composable
private fun rememberCopyAction(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    return remember(clipboard, context) { { text -> copyText(clipboard, context, text) } }
}

private fun copyText(
    clipboard: ClipboardManager,
    context: Context,
    text: String,
) {
    clipboard.setText(AnnotatedString(text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}

/** A Spotify song that wasn't matched: what Spotify had, why, and (in the summary) a Find button. */
@Composable
private fun SpotifyRow(
    song: ImportedSong,
    onFind: (() -> Unit)? = null,
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

/** A matched song: the YouTube Music result, with the Spotify title under it when they differ. */
@Composable
private fun MatchedRow(song: ImportedSong) {
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
                text = "${track.artist}$spotify${if (song.manual) " · picked by you" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = CliBeatsTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Find a song by hand ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FindSheet(
    pick: ManualPick,
    onSearch: (String) -> Unit,
    onPick: (Track) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable(pick.songIndex, pick.playlistIndex) { mutableStateOf(pick.query) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CliBeatsSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            Text(
                text = "Pick the right song",
                style = MaterialTheme.typography.titleMedium,
                color = CliBeatsTextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Search YouTube Music") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CliBeatsAccent,
                        focusedLabelColor = CliBeatsAccent,
                        cursorColor = CliBeatsAccent,
                    ),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            when {
                pick.searching ->
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CliBeatsAccent)
                    }
                pick.results.isEmpty() ->
                    Text(
                        text = "No results. Try fewer words, or the song's English name.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CliBeatsTextSecondary,
                        modifier = Modifier.padding(16.dp),
                    )
                else ->
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                        items(pick.results, key = { it.id }) { track ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = "Add ${track.title}",
                                        ) { onPick(track) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TrackArtwork(artworkUrl = track.artworkUrl, size = 44)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CliBeatsTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CliBeatsTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (track.durationMs > 0) {
                                    Text(
                                        text = formatDuration(track.durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CliBeatsTextSecondary,
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }
}

private fun songCount(n: Int) = if (n == 1) "1 song" else "$n songs"

private fun playlistCount(n: Int) = if (n == 1) "1 playlist" else "$n playlists"
