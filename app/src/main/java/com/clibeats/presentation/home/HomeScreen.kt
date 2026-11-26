@file:Suppress(
    "ktlint:standard:function-naming",
    "FunctionNaming",
    "LongMethod",
)

package com.clibeats.presentation.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clibeats.domain.model.Track
import com.clibeats.presentation.component.CliConfirmDialog
import com.clibeats.presentation.component.ProviderSwitcher
import com.clibeats.presentation.component.SongActionsSheet
import com.clibeats.presentation.component.SongTableHeader
import com.clibeats.presentation.component.SongTableRow
import com.clibeats.presentation.component.TrackActionsViewModel
import com.clibeats.presentation.component.TrackArtwork
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.playlist.PlaylistViewMode
import com.clibeats.presentation.playlist.ViewModeBar
import com.clibeats.presentation.search.formatDuration
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsDivider
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor

/**
 * Home dashboard in the spotify-tui style.
 *
 * Shows a system status block, quick navigation shortcuts, and a live
 * trending tracks feed (YouTube Music) so songs are listed on launch.
 */
@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    toggleSourceRequested: Boolean = false,
    onToggleSourceHandled: () -> Unit = {},
) {
    // Tapping the Home tab while already on Home switches the source Online <-> Local.
    LaunchedEffect(toggleSourceRequested) {
        if (toggleSourceRequested) {
            homeViewModel.toggleSource()
            onToggleSourceHandled()
        }
    }
    val trackActions: TrackActionsViewModel = hiltViewModel()
    val downloadedIds by trackActions.downloadedTrackIds.collectAsStateWithLifecycle()
    val playingId by trackActions.playingTrackId.collectAsStateWithLifecycle()
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by homeViewModel.isRefreshing.collectAsStateWithLifecycle()
    val providerName by homeViewModel.activeProviderName.collectAsStateWithLifecycle()
    val activeProviderId by homeViewModel.activeProviderId.collectAsStateWithLifecycle()
    val recentlyPlayed by homeViewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val isOnline by homeViewModel.isOnline.collectAsStateWithLifecycle()
    val viewMode by homeViewModel.viewMode.collectAsStateWithLifecycle()
    val feed by homeViewModel.feed.collectAsStateWithLifecycle()
    val homeListState = rememberLazyListState()
    // Each sideways row's position, by row name; kept here so it survives opening a card.
    val rowStates = remember { HashMap<String, LazyListState>() }
    val moods by homeViewModel.moods.collectAsStateWithLifecycle()
    val selectedMood by homeViewModel.selectedMood.collectAsStateWithLifecycle()
    val opened by homeViewModel.opened.collectAsStateWithLifecycle()
    val localHome by homeViewModel.local.collectAsStateWithLifecycle()
    val languages by homeViewModel.languages.collectAsStateWithLifecycle()
    val localSetup by homeViewModel.localSetup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The same folder picker as Settings -> Local Music; read access is kept across reboots.
    val pickMusicFolder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                homeViewModel.addLocalFolder(uri.toString())
            }
        }
    val languagesAsked by homeViewModel.languagesAsked.collectAsStateWithLifecycle()
    var pickLanguages by remember { mutableStateOf(false) }
    if (pickLanguages) {
        LanguagePickerDialog(
            current = languages,
            onDone = homeViewModel::setLanguages,
            onDismiss = { pickLanguages = false },
        )
    }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    addToPlaylistTrack?.let { track ->
        SongActionsSheet(track = track, onDismiss = { addToPlaylistTrack = null })
    }

    // A playlist or album opened from a card shows over Home; Back returns to the same spot.
    opened?.let { collection ->
        OpenedCollectionView(
            opened = collection,
            onBack = homeViewModel::closeCollection,
            onPlay = homeViewModel::playCollection,
            onSave = homeViewModel::saveCollection,
            trackActions = trackActions,
            playingTrackId = playingId,
            downloadedIds = downloadedIds,
            onSongMenu = { addToPlaylistTrack = it },
        )
        return
    }
    val feedActions =
        remember(homeViewModel) {
            FeedActions(
                playSong = homeViewModel::playFromRow,
                songMenu = { addToPlaylistTrack = it },
                open = homeViewModel::openCollection,
                selectMood = homeViewModel::selectMood,
                language =
                    LanguageActions(
                        edit = { pickLanguages = true },
                        skip = { homeViewModel.setLanguages(emptySet()) },
                    ),
                rowState = { name -> rowStates.getOrPut(name) { LazyListState() } },
            )
        }

    var confirmClearRecent by remember { mutableStateOf(false) }
    if (confirmClearRecent) {
        CliConfirmDialog(
            command = "clear --recently-played",
            message = "Clear the Recently Played list? Favorites and playlists aren't touched.",
            confirmLabel = "Clear",
            onConfirm = homeViewModel::clearRecentlyPlayed,
            onDismiss = { confirmClearRecent = false },
        )
    }
    LazyColumn(
        state = homeListState,
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp),
    ) {
        // 1. System Provider Status Block
        item {
            // Local files don't need the internet, so Local mode shows [ LOCAL ] instead of online/offline.
            val isLocal = activeProviderId == "local"
            val isSystemOnline = isLocal || isOnline
            val statusLabel =
                when {
                    isLocal -> "[ LOCAL ]"
                    isOnline -> "[ ONLINE ]"
                    else -> "[ OFFLINE ]"
                }
            TuiBlock(title = "System Status", isActive = true) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "● ACTIVE PROVIDER: ${providerName.uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSystemOnline) CliBeatsAccent else MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSystemOnline) CliBeatsAccent else MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ProviderSwitcher(
                        providers = homeViewModel.providers,
                        activeProviderId = activeProviderId,
                        onSelect = homeViewModel::setActiveProvider,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            ViewModeBar(label = "View", selected = viewMode, onSelect = homeViewModel::setViewMode)
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 3. Recently played (from history) with clear
        if (recentlyPlayed.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recently Played",
                    actionLabel = "[ CLEAR ]",
                    onAction = { confirmClearRecent = true },
                )
            }
            homeTracks(
                keyPrefix = "recent",
                tracks = recentlyPlayed,
                mode = viewMode,
                downloadedIds = downloadedIds,
                playingTrackId = playingId,
                onPlay = { index -> homeViewModel.playFromList(recentlyPlayed, index) },
                onQueue = trackActions::addToQueue,
                onFavorite = trackActions::favorite,
                onLongPress = { addToPlaylistTrack = it },
            )
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        // 4. Online: YouTube Music's home page (mood chips, then its rows). Trending is the fallback.
        val isLocalHome = activeProviderId == "local"
        feed?.let { state ->
            item(key = "feed_header") {
                val busy = isRefreshing || state is FeedState.Loading
                SectionHeader(
                    title = "Home / $providerName",
                    actionLabel = if (busy) "[ LOADING… ]" else "[ ↻ REFRESH ]",
                    onAction = homeViewModel::refreshTrending,
                    actionEnabled = !busy,
                    actionDescription = "Refresh home",
                )
                localHome?.takeIf { it.allSongs.isEmpty() }?.let {
                    LocalSetupCard(setup = localSetup, onChooseFolder = { pickMusicFolder.launch(null) })
                }
                localHome?.takeIf { it.allSongs.isNotEmpty() }?.let { page ->
                    LocalQuickActions(
                        songCount = page.allSongs.size,
                        albumCount = page.albums.size,
                        onShuffleAll = homeViewModel::shuffleAll,
                        onSurpriseAlbum = homeViewModel::surpriseAlbum,
                    )
                }
            }
            homeFeed(
                state = state,
                // Moods and languages belong to the online page only.
                moods = if (isLocalHome) emptyList() else moods,
                selectedMood = if (isLocalHome) null else selectedMood,
                playingTrackId = playingId,
                actions = feedActions,
                languagesLabel = if (!isLocalHome) languages.joinToString(", ") { it.label } else null,
                suggestLanguages = !isLocalHome && !languagesAsked && state is FeedState.Loaded,
                // Local's empty library has its own set-up card above.
                emptyText = if (localHome != null) null else "Nothing to show yet.",
            )
            return@LazyColumn
        }

        // 4. Trending Tracks Block Header
        item {
            // Trending loads once when the app opens; after that only this button reloads it.
            val busy = isRefreshing || uiState is HomeUiState.Loading
            SectionHeader(
                title = "Trending / $providerName",
                actionLabel = if (busy) "[ LOADING… ]" else "[ ↻ REFRESH ]",
                onAction = homeViewModel::refreshTrending,
                actionEnabled = !busy,
                actionDescription = "Refresh trending",
            )
            if (!viewMode.isGrid) SongTableHeader()
        }

        // 4. Track List Content
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                item {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            color = CliBeatsAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Fetching trending tracks...",
                            style = MaterialTheme.typography.bodySmall,
                            color = CliBeatsTextSecondary,
                        )
                    }
                }
            }
            is HomeUiState.Success -> {
                if (state.tracks.isEmpty()) {
                    item {
                        EmptyHomeState()
                    }
                } else {
                    homeTracks(
                        keyPrefix = "trend",
                        tracks = state.tracks,
                        mode = viewMode,
                        downloadedIds = downloadedIds,
                        playingTrackId = playingId,
                        onPlay = { index -> homeViewModel.playFromList(state.tracks, index) },
                        onQueue = trackActions::addToQueue,
                        onFavorite = trackActions::favorite,
                        onLongPress = { addToPlaylistTrack = it },
                    )
                }
            }
            is HomeUiState.Error -> {
                item {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(CliBeatsSurface)
                                .border(1.dp, CliBeatsDivider)
                                .padding(16.dp),
                    ) {
                        Text(
                            text = "> error --provider_offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = CliBeatsTextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    actionEnabled: Boolean = true,
    actionDescription: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = CliBeatsAccent)
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = CliBeatsTextSecondary.copy(alpha = if (actionEnabled) 1f else DISABLED_ALPHA),
                modifier =
                    Modifier
                        .clickable(enabled = actionEnabled, role = Role.Button, onClick = onAction)
                        .then(
                            if (actionDescription != null) {
                                Modifier.semantics { contentDescription = actionDescription }
                            } else {
                                Modifier
                            },
                        ).padding(8.dp),
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun EmptyHomeState() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(CliBeatsSurface)
                .border(1.dp, CliBeatsDivider)
                .padding(16.dp),
    ) {
        Text(
            text = "> home --welcome",
            style = MaterialTheme.typography.labelSmall,
            color = CliBeatsAccent,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text =
                "Welcome to CLIBeats Terminal Player.\n" +
                    "Use [Search] to discover music or browse your local [Library].",
            style = MaterialTheme.typography.bodySmall,
            color = CliBeatsTextSecondary,
        )
    }
}

private const val DISABLED_ALPHA = 0.5f

/**
 * Home song lists in the chosen view: List (compact rows), Details (taller rows, bigger covers), or a
 * small / large grid of covers. Tap plays; hold adds to a playlist; in the row views, swipe right queues
 * and swipe left adds to favorites.
 */
@Suppress("LongParameterList")
private fun LazyListScope.homeTracks(
    keyPrefix: String,
    tracks: List<Track>,
    mode: PlaylistViewMode,
    downloadedIds: Set<String> = emptySet(),
    playingTrackId: String? = null,
    onPlay: (Int) -> Unit,
    onQueue: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onLongPress: (Track) -> Unit,
) {
    if (!mode.isGrid) {
        val large = mode == PlaylistViewMode.DETAILS
        itemsIndexed(tracks, key = { _, t -> "${keyPrefix}_${t.id}" }) { index, track ->
            SongTableRow(
                trackTitle = track.title,
                artist = track.artist,
                duration = formatDuration(track.durationMs),
                artworkContent = {
                    TrackArtwork(artworkUrl = track.artworkUrl, size = if (large) DETAILS_ART_DP else LIST_ART_DP)
                },
                large = large,
                isDownloaded = track.id in downloadedIds,
                isNowPlaying = track.id == playingTrackId,
                onClick = { onPlay(index) },
                onSwipeRight = { onQueue(track) },
                onSwipeLeft = { onFavorite(track) },
                onLongPress = { onLongPress(track) },
            )
        }
        return
    }
    val rows = tracks.withIndex().chunked(mode.columns)
    items(rows.size, key = { "${keyPrefix}_grid_${mode.columns}_$it" }) { row ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows[row].forEach { (index, track) ->
                SongTile(
                    track = track,
                    playing = track.id == playingTrackId,
                    onClick = { onPlay(index) },
                    onLongClick = { onLongPress(track) },
                    modifier = Modifier.weight(1f),
                )
            }
            // Keep the last row's tiles the same width as the rest.
            repeat(mode.columns - rows[row].size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

@Suppress("FunctionNaming")
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongTile(
    track: Track,
    playing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .semantics(mergeDescendants = true) {}
                .combinedClickable(
                    onClickLabel = "Play",
                    onLongClickLabel = "Add to playlist",
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    ) {
        val side = maxWidth.value.toInt()
        val accent = LocalAccentColor.current
        Column {
            // The song playing now: framed in the accent colour, title in accent with a ▶.
            TrackArtwork(
                artworkUrl = track.artworkUrl,
                size = side,
                modifier = if (playing) Modifier.border(2.dp, accent) else Modifier,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (playing) "▶ ${track.title}" else track.title,
                style = MaterialTheme.typography.labelMedium,
                color = if (playing) accent else MaterialTheme.colorScheme.onBackground,
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
    }
}

private const val LIST_ART_DP = 32
private const val DETAILS_ART_DP = 52
