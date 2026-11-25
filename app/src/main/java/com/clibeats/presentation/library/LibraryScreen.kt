@file:Suppress(
    "FunctionNaming",
    "ktlint:standard:function-naming",
    "ktlint:standard:multiline-expression-wrapping",
    "MagicNumber",
)

package com.clibeats.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clibeats.domain.model.Track
import com.clibeats.presentation.component.CliConfirmDialog
import com.clibeats.presentation.component.SongActionsSheet
import com.clibeats.presentation.component.SongTableHeader
import com.clibeats.presentation.component.SongTableRow
import com.clibeats.presentation.component.TrackActionsViewModel
import com.clibeats.presentation.component.TrackArtwork
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.playlist.PlaylistScreen
import com.clibeats.presentation.playlist.PlaylistViewModel
import com.clibeats.presentation.search.formatDuration
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsDivider
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary

@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    toggleModeRequested: Boolean = false,
    onToggleModeHandled: () -> Unit = {},
) {
    val trackActions: TrackActionsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val downloadedIds by trackActions.downloadedTrackIds.collectAsState()
    // Same instance PlaylistScreen uses below (same owner), so we can see whether a playlist is open.
    val playlistViewModel: PlaylistViewModel = hiltViewModel()
    // Tapping the Library tab while already on it: inside a playlist, back to the playlist list first;
    // otherwise the next section (Playlists → Online → Local).
    LaunchedEffect(toggleModeRequested) {
        if (toggleModeRequested) {
            if (mode == LibraryMode.PLAYLISTS && playlistViewModel.isPlaylistOpen) {
                playlistViewModel.selectPlaylist(null)
            } else {
                viewModel.nextMode()
            }
            onToggleModeHandled()
        }
    }
    if (mode == LibraryMode.PLAYLISTS) {
        Column(modifier = Modifier.fillMaxSize().background(CliBeatsBackground)) {
            Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
                LibraryModeToggle(mode = mode, onSelect = viewModel::setMode)
            }
            Box(modifier = Modifier.weight(1f)) { PlaylistScreen() }
        }
        return
    }
    val localFolderChosen by viewModel.localFolderChosen.collectAsState()
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    addToPlaylistTrack?.let { track ->
        SongActionsSheet(track = track, onDismiss = { addToPlaylistTrack = null })
    }
    val tabs = listOf("Tracks", "Artists", "Albums")
    var confirmClearHistory by remember { mutableStateOf(false) }
    if (confirmClearHistory) {
        CliConfirmDialog(
            command = "clear --history",
            message = "Clear the songs you've played online from this list? Favorites and playlists aren't touched.",
            confirmLabel = "Clear",
            onConfirm = viewModel::clearPlayedHistory,
            onDismiss = { confirmClearHistory = false },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CliBeatsBackground)
                .padding(12.dp),
    ) {
        LibraryModeToggle(mode = mode, onSelect = viewModel::setMode)
        if (mode == LibraryMode.ONLINE && state is LibraryUiState.Success) {
            Text(
                text = "[ CLEAR ]",
                style = MaterialTheme.typography.labelMedium,
                color = CliBeatsTextSecondary,
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .clickable(role = Role.Button) { confirmClearHistory = true }
                        .padding(8.dp),
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        val trackCount = (state as? LibraryUiState.Success)?.tracks?.size ?: 0
        val modeLabel =
            if (mode == LibraryMode.ONLINE) {
                "Online · played"
            } else {
                "Local · $trackCount ${if (trackCount == 1) "track" else "tracks"}"
            }
        val titleText = "Library — $modeLabel (${tabs[selectedTabIndex]})"
        TuiBlock(
            title = titleText,
            isActive = true,
            modifier = Modifier.weight(1f),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tabs.forEachIndexed { index, title ->
                        val selected = selectedTabIndex == index
                        val label = if (selected) "[● ${title.uppercase()}]" else "[ ${title.uppercase()} ]"
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) CliBeatsAccent else CliBeatsTextSecondary,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .border(1.dp, if (selected) CliBeatsAccent else CliBeatsDivider)
                                    .clickable(role = Role.Tab) { selectedTabIndex = index }
                                    .padding(vertical = 8.dp),
                        )
                    }
                }

                when (val currentState = state) {
                    is LibraryUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = CliBeatsAccent)
                        }
                    }

                    is LibraryUiState.Empty -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text =
                                    when {
                                        mode == LibraryMode.ONLINE -> NOTHING_PLAYED
                                        !localFolderChosen -> NO_FOLDER
                                        else -> NO_FILES
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = CliBeatsTextSecondary,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }

                    is LibraryUiState.Success -> {
                        when (selectedTabIndex) {
                            0 ->
                                TracksList(
                                    tracks = currentState.tracks,
                                    downloadedIds = downloadedIds,
                                    onTrackClick = { track, index ->
                                        viewModel.onTrackClick(track, currentState.tracks, index)
                                    },
                                    onSwipeRight = trackActions::addToQueue,
                                    onSwipeLeft = trackActions::favorite,
                                    onLongPress = { addToPlaylistTrack = it },
                                )
                            1 -> ArtistsList(artists = currentState.artists)
                            2 -> AlbumsList(albums = currentState.albums)
                        }
                    }
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun TracksList(
    tracks: List<Track>,
    downloadedIds: Set<String> = emptySet(),
    onTrackClick: (Track, Int) -> Unit,
    onSwipeRight: (Track) -> Unit,
    onLongPress: (Track) -> Unit,
    onSwipeLeft: (Track) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SongTableHeader()
        }
        itemsIndexed(items = tracks, key = { _, track -> track.id }) { index, track ->
            SongTableRow(
                trackTitle = track.title,
                artist = track.artist,
                duration = formatDuration(track.durationMs),
                index = index + 1,
                isDownloaded = track.id in downloadedIds,
                artworkContent = { TrackArtwork(artworkUrl = track.artworkUrl, size = 32) },
                onClick = { onTrackClick(track, index) },
                onSwipeRight = { onSwipeRight(track) },
                onLongPress = { onLongPress(track) },
                onSwipeLeft = { onSwipeLeft(track) },
            )
        }
    }
}

private const val NOTHING_PLAYED = "> Nothing played yet. Songs you play show up here."
private const val NO_FOLDER = "> No local folder. Settings → Local Music → Choose folder"
private const val NO_FILES = "> No audio files found. Settings → Local Music → Rescan"

@Suppress("FunctionNaming")
@Composable
private fun LibraryModeToggle(
    mode: LibraryMode,
    onSelect: (LibraryMode) -> Unit,
) {
    // Equal-width segments, matching the Online / Local switch on Home and Search.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LibraryMode.entries.forEach { option ->
            val selected = option == mode
            Text(
                text = if (selected) "[● ${option.name}]" else "[ ${option.name} ]",
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) CliBeatsAccent else CliBeatsTextSecondary,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { this.selected = selected }
                        .border(1.dp, if (selected) CliBeatsAccent else CliBeatsDivider)
                        .clickable(role = Role.Tab) { onSelect(option) }
                        .padding(horizontal = 4.dp, vertical = 10.dp),
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ArtistsList(artists: List<ArtistGroup>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(items = artists, key = { _, item -> item.name }) { index, artist ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}. ${artist.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CliBeatsTextPrimary,
                    maxLines = 1,
                )
                Text(
                    text = "${artist.trackCount} ${if (artist.trackCount == 1) "track" else "tracks"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CliBeatsTextSecondary,
                )
            }
            HorizontalDivider(thickness = 1.dp, color = CliBeatsSurface)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AlbumsList(albums: List<AlbumGroup>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(items = albums, key = { _, item -> "${item.artist}_${item.title}" }) { index, album ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${index + 1}. ${album.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CliBeatsTextPrimary,
                        maxLines = 1,
                    )
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = CliBeatsTextSecondary,
                        maxLines = 1,
                    )
                }
                Text(
                    text = "${album.trackCount} ${if (album.trackCount == 1) "track" else "tracks"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CliBeatsTextSecondary,
                )
            }
            HorizontalDivider(thickness = 1.dp, color = CliBeatsSurface)
        }
    }
}
