@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "ForbiddenImport", "MagicNumber")

package com.clibeats.presentation.importer

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clibeats.data.importer.ImportState
import com.clibeats.data.importer.ImportedSong
import com.clibeats.data.importer.PlaylistImport
import com.clibeats.domain.model.Track
import com.clibeats.presentation.component.TrackArtwork
import com.clibeats.presentation.search.formatDuration
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsDestructive
import com.clibeats.presentation.theme.CliBeatsDivider
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary

@Composable
internal fun ImportReviewStep(
    state: ImportState.Finished,
    onFind: (Int, ImportedSong) -> Unit,
    onDone: () -> Unit,
) {
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
        importSectionHeader(
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
        importSectionHeader("p${playlistIndex}_ok", "✓ MATCHED · ${matched.size}", good = true)
        items(matched, key = { "p${playlistIndex}_ok${it.index}" }) { MatchedRow(it) }
    }
}

private fun songList(songs: List<ImportedSong>): String =
    songs.sortedBy { it.index }.joinToString("\n") { s ->
        if (s.song.artistLine.isEmpty()) s.song.title else "${s.song.title} — ${s.song.artistLine}"
    }

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

@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FindMatchSheet(
    pick: ManualPick,
    playingId: String?,
    onPreview: (Track) -> Unit,
    onSearch: (String) -> Unit,
    onPick: (Track) -> Unit,
    onDismiss: () -> Unit,
    seek: PreviewSeek? = null,
    onConfirm: () -> Unit = {},
) {
    var query by rememberSaveable(pick.songIndex, pick.playlistIndex) { mutableStateOf(pick.query) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
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
            Text(
                text = "Tap a song to play it, [ + ] to choose it, then Mark as found.",
                style = MaterialTheme.typography.bodySmall,
                color = CliBeatsTextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Search YouTube Music") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(onSearch = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        onSearch(query)
                    }),
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
                            val playing = track.id == playingId
                            val chosen = pick.chosen?.id == track.id
                            Column(
                                modifier =
                                    Modifier.background(
                                        if (chosen) CliBeatsAccent.copy(alpha = CHOSEN_ALPHA) else Color.Transparent,
                                    ),
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                role = Role.Button,
                                                onClickLabel = if (playing) "Pause" else "Play ${track.title}",
                                            ) { onPreview(track) }
                                            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TrackArtwork(artworkUrl = track.artworkUrl, size = 44)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (playing) "▶ ${track.title}" else track.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (playing) CliBeatsAccent else CliBeatsTextPrimary,
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
                                    Text(
                                        text = if (chosen) "[ ✓ ]" else "[ + ]",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = CliBeatsAccent,
                                        modifier =
                                            Modifier
                                                .clickable(
                                                    role = Role.Button,
                                                    onClickLabel = if (chosen) "Unchoose" else "Choose ${track.title}",
                                                ) {
                                                    onPick(track)
                                                }.padding(horizontal = 12.dp, vertical = 10.dp),
                                    )
                                }
                                // The song being previewed: a seek bar right under it.
                                if (seek != null && track.id == seek.loadedId && seek.durationMs > 0) {
                                    PreviewSeekBar(seek = seek, playing = playing)
                                }
                            }
                        }
                    }
            }
            Box(modifier = Modifier.padding(16.dp)) {
                CliButton(
                    text = pick.chosen?.let { "Mark as found: ${it.title}" } ?: "Choose a song with [ + ]",
                    primary = pick.chosen != null,
                    onClick = { if (pick.chosen != null) onConfirm() },
                )
            }
        }
    }
}

/** The Find sheet's preview: which song is loaded, its length, its live position, and seeking. */
internal class PreviewSeek(
    val loadedId: String?,
    val durationMs: Long,
    val position: () -> Long,
    val onSeek: (Long) -> Unit,
)

/** A slim seek bar under the previewed result: follows the song smoothly; drag or tap to jump. */
@Composable
private fun PreviewSeekBar(
    seek: PreviewSeek,
    playing: Boolean,
) {
    var position by remember { mutableLongStateOf(seek.position()) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(playing, seek.loadedId) {
        position = seek.position()
        while (playing) {
            withFrameNanos { if (dragging == null) position = seek.position() }
        }
    }
    val duration = seek.durationMs.coerceAtLeast(1L)
    val fraction = dragging ?: (position.toFloat() / duration).coerceIn(0f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatDuration((fraction * duration).toLong()),
            style = MaterialTheme.typography.labelSmall,
            color = CliBeatsTextSecondary,
        )
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let {
                    val target = (it * duration).toLong()
                    seek.onSeek(target)
                    position = target
                }
                dragging = null
            },
            colors =
                SliderDefaults.colors(
                    thumbColor = CliBeatsAccent,
                    activeTrackColor = CliBeatsAccent,
                    inactiveTrackColor = CliBeatsDivider,
                ),
            modifier = Modifier.weight(1f).height(28.dp).padding(horizontal = 8.dp),
        )
        Text(
            text = formatDuration(seek.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = CliBeatsTextSecondary,
        )
    }
}

private const val CHOSEN_ALPHA = 0.14f
