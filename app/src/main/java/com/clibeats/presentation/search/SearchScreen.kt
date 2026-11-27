@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:multiline-expression-wrapping",
    "MagicNumber",
)

package com.clibeats.presentation.search

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clibeats.domain.model.HomeItem
import com.clibeats.domain.model.Track
import com.clibeats.presentation.component.AddToPlaylistDialog
import com.clibeats.presentation.component.CliConfirmDialog
import com.clibeats.presentation.component.ProviderSwitcher
import com.clibeats.presentation.component.SongActionsSheet
import com.clibeats.presentation.component.SongTableHeader
import com.clibeats.presentation.component.SongTableRow
import com.clibeats.presentation.component.TrackActionsViewModel
import com.clibeats.presentation.component.TrackArtwork
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.component.TuiTextField
import com.clibeats.presentation.home.FeedCard
import com.clibeats.presentation.home.OpenedCollection
import com.clibeats.presentation.home.OpenedCollectionView
import com.clibeats.presentation.home.keepSwipesInRow
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor

// One screen state switch (idle / recent / loading / error / results); split further it reads worse.
@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun SearchScreen(
    focusRequested: Boolean = false,
    onFocusHandled: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
    toggleSourceRequested: Boolean = false,
    onToggleSourceHandled: () -> Unit = {},
) {
    LaunchedEffect(toggleSourceRequested) {
        if (toggleSourceRequested) {
            viewModel.toggleSource()
            onToggleSourceHandled()
        }
    }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Double-tapping the Search tab focuses the box and opens the keyboard (one-shot).
    LaunchedEffect(focusRequested) {
        if (focusRequested) {
            runCatching { searchFocus.requestFocus() }
            keyboard?.show()
            onFocusHandled()
        }
    }
    val trackActions: TrackActionsViewModel = hiltViewModel()
    val downloadedIds by trackActions.downloadedTrackIds.collectAsStateWithLifecycle()
    val playingId by trackActions.playingTrackId.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.searchResults.collectAsStateWithLifecycle()
    val providerName by viewModel.activeProviderName.collectAsStateWithLifecycle()
    val activeProviderId by viewModel.activeProviderId.collectAsStateWithLifecycle()
    var playlistPickerTrack by remember { mutableStateOf<Track?>(null) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val playlists by viewModel.playlistResults.collectAsStateWithLifecycle()
    val opened by viewModel.opened.collectAsStateWithLifecycle()
    // Here (above the early return), so the results are where you left them after opening a playlist.
    val resultsListState = rememberLazyListState()
    val playlistRowState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    var confirmClearRecent by remember { mutableStateOf(false) }
    if (confirmClearRecent) {
        CliConfirmDialog(
            command = "clear --recent-searches",
            message = "Clear all recent searches?",
            confirmLabel = "Clear",
            onConfirm = viewModel::clearRecentSearches,
            onDismiss = { confirmClearRecent = false },
        )
    }

    // A playlist opened from the results shows over Search (same view as Home cards); Back returns here.
    opened?.let { collection ->
        OpenedSearchPlaylist(collection, viewModel, trackActions, playingId, downloadedIds)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CliBeatsBackground)
            .padding(12.dp),
    ) {
        // ── Search input (TUI-styled, focus-activated border) ──────────────
        TuiTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            label = "Search",
            placeholder = "What do you want to play?",
            modifier = Modifier.semantics { contentDescription = "Search music" },
            focusRequester = searchFocus,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // The search key closes the keyboard so the results can be seen (clearing focus alone left it open).
            keyboardActions =
                KeyboardActions(onSearch = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }),
            trailingContent = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            viewModel.clearQuery()
                            focusManager.clearFocus()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = "Clear search",
                            tint = CliBeatsTextSecondary,
                        )
                    }
                }
            },
        )

        Spacer(modifier = Modifier.height(8.dp))
        ProviderSwitcher(
            providers = viewModel.providers,
            activeProviderId = activeProviderId,
            onSelect = viewModel::setActiveProvider,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // ── Results area block ───────────────────────────────────────────
        TuiBlock(title = "Search Results — $providerName", modifier = Modifier.weight(1f)) {
            // Empty box: show the songs picked from earlier searches (or the hint when there are none).
            if (query.isBlank()) {
                if (recentSearches.isEmpty()) {
                    SearchIdleState()
                } else {
                    RecentSearchesList(
                        tracks = recentSearches,
                        downloadedIds = downloadedIds,
                        playingTrackId = playingId,
                        onTrackClick = { index -> viewModel.onPlayRecent(recentSearches, index) },
                        onClear = { confirmClearRecent = true },
                        onAddToQueue = { track ->
                            trackActions.swipeRight(track)
                            viewModel.rememberPick(track)
                        },
                        onFavorite = { track ->
                            trackActions.swipeLeft(track)
                            viewModel.rememberPick(track)
                        },
                        onMore = { menuTrack = it },
                    )
                }
                return@TuiBlock
            }
            when (val currentState = state) {
                is SearchUiState.Idle -> SearchIdleState()
                is SearchUiState.Loading -> SearchLoadingState(providerName)
                is SearchUiState.Error -> SearchErrorState(currentState.message)
                is SearchUiState.Success ->
                    if (currentState.tracks.isEmpty() && playlists.isEmpty()) {
                        SearchNoResultsState()
                    } else {
                        SearchResultsList(
                            tracks = currentState.tracks,
                            playlists = playlists,
                            onPlaylistClick = viewModel::openPlaylist,
                            listState = resultsListState,
                            playlistRowState = playlistRowState,
                            downloadedIds = downloadedIds,
                            playingTrackId = playingId,
                            onTrackClick = { index -> viewModel.onPlay(currentState.tracks, index) },
                            onAddToQueue = { track ->
                                trackActions.swipeRight(track)
                                viewModel.rememberPick(track)
                            },
                            onFavorite = { track ->
                                trackActions.swipeLeft(track)
                                viewModel.rememberPick(track)
                            },
                            onPlayNext = { track ->
                                trackActions.playNext(track)
                                viewModel.rememberPick(track)
                            },
                            onAddToPlaylist = { track ->
                                playlistPickerTrack = track
                                viewModel.rememberPick(track)
                            },
                            onMore = { menuTrack = it },
                        )
                    }
            }
        }
    }

    playlistPickerTrack?.let { track ->
        AddToPlaylistDialog(track = track, onDismiss = { playlistPickerTrack = null })
    }
    menuTrack?.let { track ->
        SongActionsSheet(track = track, onDismiss = { menuTrack = null }, onPicked = viewModel::rememberPick)
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun RecentSearchesList(
    tracks: List<Track>,
    downloadedIds: Set<String> = emptySet(),
    playingTrackId: String? = null,
    onTrackClick: (Int) -> Unit,
    onClear: () -> Unit,
    onAddToQueue: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onMore: (Track) -> Unit,
) {
    val accent = LocalAccentColor.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "RECENT SEARCHES", style = MaterialTheme.typography.labelMedium, color = accent)
                Text(
                    text = "[ CLEAR ]",
                    style = MaterialTheme.typography.labelMedium,
                    color = CliBeatsTextSecondary,
                    modifier = Modifier.clickable(onClick = onClear).padding(8.dp),
                )
            }
        }
        itemsIndexed(items = tracks, key = { _, track -> "recent_${track.id}" }) { index, track ->
            SongTableRow(
                trackTitle = track.title,
                artist = track.artist,
                duration = formatDuration(track.durationMs),
                isDownloaded = track.id in downloadedIds,
                isNowPlaying = track.id == playingTrackId,
                artworkContent = { TrackArtwork(artworkUrl = track.artworkUrl, size = 32) },
                onClick = { onTrackClick(index) },
                onSwipeRight = { onAddToQueue(track) },
                onSwipeLeft = { onFavorite(track) },
                onLongPress = { onMore(track) },
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun SearchIdleState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "> Type a query to search tracks...",
            style = MaterialTheme.typography.bodyMedium,
            color = CliBeatsTextSecondary,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun SearchLoadingState(providerName: String) {
    val accent = LocalAccentColor.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Searching…" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Searching $providerName... [████░░░░]",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun SearchErrorState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "> error --$message",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun SearchNoResultsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "> No playable tracks found.",
            style = MaterialTheme.typography.bodyMedium,
            color = CliBeatsTextSecondary,
        )
    }
}

@Suppress("FunctionNaming", "LongParameterList", "LongMethod")
@Composable
private fun SearchResultsList(
    tracks: List<Track>,
    playlists: List<HomeItem.Collection> = emptyList(),
    onPlaylistClick: (HomeItem.Collection) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    playlistRowState: LazyListState = rememberLazyListState(),
    downloadedIds: Set<String> = emptySet(),
    playingTrackId: String? = null,
    onTrackClick: (Int) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onMore: (Track) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        if (playlists.isNotEmpty()) {
            item(key = "playlists") { PlaylistResultsRow(playlists, onPlaylistClick, playlistRowState) }
        }
        if (tracks.isNotEmpty()) {
            item {
                SongTableHeader()
            }
        }
        itemsIndexed(items = tracks, key = { _, track -> track.id }) { index, track ->
            SongTableRow(
                trackTitle = track.title,
                artist = buildString {
                    append(track.artist)
                    if (track.album.isNotBlank()) append(" • ${track.album}")
                },
                duration = formatDuration(track.durationMs),
                index = index + 1,
                isDownloaded = track.id in downloadedIds,
                isNowPlaying = track.id == playingTrackId,
                artworkContent = { TrackArtwork(artworkUrl = track.artworkUrl, size = 32) },
                onClick = { onTrackClick(index) },
                onSwipeRight = { onAddToQueue(track) },
                onSwipeLeft = { onFavorite(track) },
                onLongPress = { onMore(track) },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onPlayNext(track) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
                                contentDescription = "Play next",
                                tint = CliBeatsTextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(
                            onClick = { onAddToPlaylist(track) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                                contentDescription = "Add to playlist",
                                tint = CliBeatsTextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        // No queue button: swiping the row right adds it to the queue.
                    }
                },
            )
        }
    }
}

/** Format duration milliseconds as "m:ss" or "h:mm:ss". */
internal fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "–"
    val totalSec = durationMs / 1_000L
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** Playlists matching the search, as a sideways row of cards (like Home); tapping one opens it. */
@Suppress("FunctionNaming")
@Composable
private fun PlaylistResultsRow(
    playlists: List<HomeItem.Collection>,
    onClick: (HomeItem.Collection) -> Unit,
    rowState: LazyListState,
) {
    val accent = LocalAccentColor.current
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            text = "PLAYLISTS · ${playlists.size}",
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        LazyRow(
            state = rowState,
            // Swiping the row sideways scrolls the cards instead of flipping to the next tab.
            modifier = Modifier.fillMaxWidth().keepSwipesInRow(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(playlists, key = { it.browseId }) { item ->
                FeedCard(
                    title = item.title,
                    subtitle = item.subtitle,
                    artworkUrl = item.artworkUrl,
                    round = false,
                    playing = false,
                    onClick = { onClick(item) },
                    onLongClick = null,
                )
            }
        }
    }
}

/** A playlist opened from the results, over Search, with Play, Shuffle and Save (the same view as Home cards). */
@Suppress("FunctionNaming")
@Composable
private fun OpenedSearchPlaylist(
    collection: OpenedCollection,
    viewModel: SearchViewModel,
    trackActions: TrackActionsViewModel,
    playingId: String?,
    downloadedIds: Set<String>,
) {
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    Box(modifier = Modifier.fillMaxSize().background(CliBeatsBackground)) {
        OpenedCollectionView(
            opened = collection,
            onBack = viewModel::closePlaylist,
            onPlay = viewModel::playCollection,
            onSave = viewModel::saveCollection,
            trackActions = trackActions,
            playingTrackId = playingId,
            downloadedIds = downloadedIds,
            onSongMenu = { menuTrack = it },
        )
    }
    menuTrack?.let { track -> SongActionsSheet(track = track, onDismiss = { menuTrack = null }) }
}
