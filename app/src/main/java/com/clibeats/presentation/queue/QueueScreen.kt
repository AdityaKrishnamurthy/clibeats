@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:multiline-expression-wrapping",
    "MagicNumber",
)

package com.clibeats.presentation.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clibeats.domain.model.Track
import com.clibeats.presentation.component.ReorderableList
import com.clibeats.presentation.component.SongTableRow
import com.clibeats.presentation.component.TrackArtwork
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.search.formatDuration
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import kotlinx.coroutines.launch

@Suppress("FunctionNaming", "LongMethod")
@Composable
fun QueueScreen(
    viewModel: QueueViewModel = hiltViewModel(),
    onClose: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Removing has no ✕ any more (swipe right or the TalkBack action), so every removal offers UNDO.
    val removeWithUndo: (Int, Track) -> Unit = { index, track ->
        viewModel.onRemove(index)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result =
                snackbar.showSnackbar(
                    message = "Removed \u201c${track.title}\u201d",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove(index, track)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(CliBeatsBackground),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(CliBeatsSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onClose != null) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(32.dp).padding(end = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Close queue",
                                tint = CliBeatsTextPrimary,
                            )
                        }
                    }
                    Text(
                        text = "PLAYBACK QUEUE",
                        style = MaterialTheme.typography.titleMedium,
                        color = CliBeatsTextPrimary,
                    )
                }

                val autoplay by viewModel.autoplayEnabled.collectAsState()
                Text(
                    text = if (autoplay) "[ ∞ AUTOPLAY: ON ]" else "[ ∞ AUTOPLAY: OFF ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (autoplay) CliBeatsAccent else CliBeatsTextSecondary,
                    modifier =
                        Modifier
                            .clickable { viewModel.toggleAutoplay() }
                            .padding(4.dp),
                )
            }

            when (val currentState = state) {
                is QueueUiState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Queue is empty",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CliBeatsTextSecondary,
                        )
                    }
                }

                is QueueUiState.Success -> SplitQueueView(currentState, viewModel, removeWithUndo)
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = CliBeatsSurface,
                contentColor = CliBeatsTextPrimary,
                actionColor = CliBeatsAccent,
            )
        }
    }
}

@Suppress("FunctionNaming", "LongMethod")
@Composable
private fun SplitQueueView(
    state: QueueUiState.Success,
    viewModel: QueueViewModel,
    onRemove: (Int, Track) -> Unit,
) {
    val tracks = state.tracks
    // Real position first: with a song queued twice, matching by id picks the wrong copy.
    val currentIndex =
        state.currentIndex.takeIf { tracks.getOrNull(it)?.id == state.currentTrackId }
            ?: tracks.indexOfFirst { it.id == state.currentTrackId }
    val nowPlayingTrack = if (currentIndex in tracks.indices) tracks[currentIndex] else tracks.firstOrNull()
    val upcomingStartIndex = if (currentIndex >= 0) currentIndex + 1 else 0
    val upcoming = if (currentIndex >= 0) tracks.drop(currentIndex + 1) else emptyList()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp),
    ) {
        if (nowPlayingTrack != null) {
            TuiBlock(
                title = "Now Playing",
                isActive = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                SongTableRow(
                    trackTitle = nowPlayingTrack.title,
                    artist = nowPlayingTrack.artist,
                    duration = formatDuration(nowPlayingTrack.durationMs),
                    // Numbered from what plays now: the playing song is 1, Up Next starts at 2.
                    index = 1,
                    isNowPlaying = true,
                    onClick = {
                        if (currentIndex >= 0) {
                            viewModel.onTrackClick(nowPlayingTrack, currentIndex)
                        }
                    },
                )
            }
        }

        TuiBlock(
            title = "Up Next (${upcoming.size})",
            isActive = false,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "[ SHUFFLE ]",
                        style = MaterialTheme.typography.labelSmall,
                        color = CliBeatsAccent,
                        modifier =
                            Modifier
                                .clickable { viewModel.onShuffle() }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "[ CLEAR ]",
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (upcoming.isNotEmpty()) {
                                CliBeatsTextSecondary
                            } else {
                                CliBeatsTextSecondary.copy(alpha = 0.5f)
                            },
                        modifier =
                            Modifier
                                .clickable(enabled = upcoming.isNotEmpty()) { viewModel.onClear() }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }

                if (upcoming.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No upcoming tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = CliBeatsTextSecondary,
                        )
                    }
                } else {
                    ReorderableUpcomingList(
                        upcoming = upcoming,
                        startIndex = upcomingStartIndex,
                        viewModel = viewModel,
                        onRemove = onRemove,
                    )
                }
            }
        }
    }
}

/**
 * Up Next. Drag a song's ≡ handle, or hold its cover and drag, to move it; the playing song (shown above)
 * stays put. The move is sent to the player once, when the finger lifts, so playback isn't disturbed.
 */
@Suppress("FunctionNaming")
@Composable
private fun ReorderableUpcomingList(
    upcoming: List<Track>,
    startIndex: Int,
    viewModel: QueueViewModel,
    onRemove: (Int, Track) -> Unit,
) {
    ReorderableList(
        items = upcoming,
        itemId = { it.id },
        onReorder = { from, to, _ -> viewModel.onMove(from + startIndex, to + startIndex) },
    ) { track, index ->
        val queueIndex = index + startIndex
        SongTableRow(
            trackTitle = track.title,
            artist = track.artist,
            duration = formatDuration(track.durationMs),
            index = index + 2,
            artworkContent = {
                TrackArtwork(
                    artworkUrl = track.artworkUrl,
                    size = 32,
                    modifier = Modifier.artworkHandle { viewModel.onTrackClick(track, queueIndex) },
                )
            },
            onClick = { if (!isAnyDragging) viewModel.onTrackClick(track, queueIndex) },
            onSwipeRight = { onRemove(queueIndex, track) },
            swipeRightLabel = "✕ REMOVE",
            // Position counted from now (playing = 1), in front of the cover.
            leading = {
                Text(
                    text = "${index + 2}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CliBeatsTextSecondary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(24.dp).padding(end = 6.dp),
                )
            },
            actions = { DragHandle() },
            // Swipe can't be done with TalkBack: offer removal as a spoken action instead.
            accessibilityActions =
                listOf(
                    CustomAccessibilityAction("Remove from queue") {
                        onRemove(queueIndex, track)
                        true
                    },
                ),
        )
    }
}
