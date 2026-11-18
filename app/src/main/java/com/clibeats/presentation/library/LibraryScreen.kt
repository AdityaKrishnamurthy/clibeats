@file:Suppress(
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clibeats.domain.model.Track
import com.clibeats.presentation.component.AddToPlaylistDialog
import com.clibeats.presentation.component.FavoritesViewModel
import com.clibeats.presentation.component.SongTableHeader
import com.clibeats.presentation.component.SongTableRow
import com.clibeats.presentation.component.TrackArtwork
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.search.formatDuration
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsDivider
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary

@Suppress("FunctionNaming", "LongMethod")
@Composable
fun LibraryScreen(viewModel: LibraryViewModel = hiltViewModel()) {
    val favorites: FavoritesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val localFolderChosen by viewModel.localFolderChosen.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    addToPlaylistTrack?.let { track ->
        AddToPlaylistDialog(track = track, onDismiss = { addToPlaylistTrack = null })
    }
    val tabs = listOf("Tracks", "Artists", "Albums")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CliBeatsBackground)
            .padding(12.dp),
    ) {
        LibraryModeToggle(mode = mode, onSelect = viewModel::setMode)
        Spacer(modifier = Modifier.height(8.dp))

        val modeLabel = if (mode == LibraryMode.ONLINE) "Online · played" else "Offline · device"
        TuiBlock(
            title = "Library — $modeLabel (${tabs[selectedTabIndex]})",
            isActive = true,
            modifier = Modifier.weight(1f),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = CliBeatsSurface,
                    contentColor = CliBeatsAccent,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = CliBeatsAccent,
                        )
                    },
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selectedTabIndex == index) CliBeatsAccent else CliBeatsTextSecondary,
                                )
                            },
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
                                    onTrackClick = { track, index ->
                                        viewModel.onTrackClick(track, currentState.tracks, index)
                                    },
                                    onSwipeRight = viewModel::onSwipeAddToQueue,
                                    onSwipeLeft = favorites::add,
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

@Suppress("FunctionNaming")
@Composable
private fun TracksList(
    tracks: List<Track>,
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LibraryMode.entries.forEach { option ->
            val selected = option == mode
            Text(
                text = if (selected) "[● ${option.name}]" else "[ ${option.name} ]",
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) CliBeatsAccent else CliBeatsTextSecondary,
                modifier = Modifier
                    .semantics { this.selected = selected }
                    .border(1.dp, if (selected) CliBeatsAccent else CliBeatsDivider)
                    .clickable(role = Role.Tab) { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ArtistsList(artists: List<ArtistGroup>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(items = artists, key = { _, artist -> artist.name }) { _, artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CliBeatsTextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${artist.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = CliBeatsTextSecondary,
                )
            }
            HorizontalDivider(color = CliBeatsDivider, thickness = 1.dp)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AlbumsList(albums: List<AlbumGroup>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(items = albums, key = { index, album -> "${album.title}_$index" }) { _, album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CliBeatsTextPrimary,
                    )
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = CliBeatsTextSecondary,
                    )
                }
                Text(
                    text = "${album.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = CliBeatsTextSecondary,
                )
            }
            HorizontalDivider(color = CliBeatsDivider, thickness = 1.dp)
        }
    }
}
