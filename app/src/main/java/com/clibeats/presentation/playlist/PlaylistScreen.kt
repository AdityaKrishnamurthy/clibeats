@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:multiline-expression-wrapping",
    "MagicNumber",
    "LongMethod",
    "FunctionNaming",
    "LongParameterList",
    "MatchingDeclarationName",
)

package com.clibeats.presentation.playlist

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clibeats.domain.model.Playlist
import com.clibeats.domain.model.Track
import com.clibeats.presentation.component.AddToPlaylistDialog
import com.clibeats.presentation.component.CliConfirmDialog
import com.clibeats.presentation.component.SongActionsSheet
import com.clibeats.presentation.component.TrackActionsViewModel
import com.clibeats.presentation.component.isLocalFilePlaylist
import com.clibeats.presentation.importer.ImportScreen
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import java.text.Collator

enum class PlaylistFilter { ALL, LOCAL_M3U }

@Suppress("CyclomaticComplexMethod")
@Composable
fun PlaylistScreen(
    showGridRequested: Boolean = false,
    onShowGridHandled: () -> Unit = {},
    filter: PlaylistFilter = PlaylistFilter.ALL,
    triggerCreateDialog: Boolean = false,
    onCreateDialogHandled: () -> Unit = {},
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val trackActions: TrackActionsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Playlist?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var coverChangePlaylist by remember { mutableStateOf<Playlist?>(null) }
    var pendingClear by remember { mutableStateOf<ClearTarget?>(null) }
    val downloadedIds by trackActions.downloadedTrackIds.collectAsState()
    val downloads by trackActions.downloads.collectAsState()
    val pendingIds by trackActions.pendingTrackIds.collectAsState()
    val playingId by trackActions.playingTrackId.collectAsState()
    // Several songs picked in a playlist, going to "add to playlist".
    var bulkAddTracks by remember { mutableStateOf<List<Track>?>(null) }
    val selection =
        remember(trackActions) {
            SelectionActions(
                download = trackActions::downloadAll,
                addToPlaylist = { bulkAddTracks = it },
                queue = trackActions::addAllToQueue,
                playNext = trackActions::playAllNext,
                favorite = trackActions::favoriteAll,
                more = { addToPlaylistTrack = it },
            )
        }
    // Import from Spotify opens over the playlist list; saveable so rotating doesn't drop you out of it.
    var showImport by rememberSaveable { mutableStateOf(false) }
    if (showImport) {
        ImportScreen(onClose = { showImport = false })
        return
    }

    // Re-tapping the Playlists tab while inside a playlist goes back to the grid.
    LaunchedEffect(showGridRequested) {
        if (showGridRequested) {
            viewModel.selectPlaylist(null)
            onShowGridHandled()
        }
    }
    LaunchedEffect(triggerCreateDialog) {
        if (triggerCreateDialog) {
            showCreateDialog = true
            onCreateDialogHandled()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CliBeatsBackground),
    ) {
        when (val s = state) {
            is PlaylistUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CliBeatsAccent)
                }

            is PlaylistUiState.Success -> {
                val tracks = s.selectedPlaylistTracks
                val selected = s.selectedPlaylist
                when {
                    s.isFavoritesOpen -> {
                        BackHandler { viewModel.selectPlaylist(null) }
                        PlaylistDetailView(
                            title = "♥ Favorites",
                            subtitle = "${tracks.size} tracks",
                            tracks = tracks,
                            emptyText = "No favorites yet. Tap ♥ in Now Playing to add songs.",
                            onBackClick = { viewModel.selectPlaylist(null) },
                            onTrackClick = { shown, index -> viewModel.playPlaylist(shown, index, "♥ Favorites") },
                            onRemoveClick = viewModel::removeFromFavorites,
                            removeLabel = "Remove from favorites",
                            onReorder = viewModel::reorderFavorites,
                            onSwipeRight = trackActions::addToQueue,
                            onSwipeLeft = trackActions::favorite,
                            selection = selection,
                            headerAction =
                                clearAllAction(tracks.isNotEmpty()) { pendingClear = ClearTarget.FAVORITES },
                            downloadedIds = downloadedIds,
                            downloads = downloads,
                            playingTrackId = playingId,
                        )
                    }
                    s.isMostPlayedOpen -> {
                        BackHandler { viewModel.selectPlaylist(null) }
                        PlaylistDetailView(
                            title = "Most Played",
                            subtitle = "${tracks.size} tracks · by play count",
                            tracks = tracks,
                            emptyText = "Nothing played yet. Your most played songs will show up here.",
                            onBackClick = { viewModel.selectPlaylist(null) },
                            onTrackClick = { shown, index -> viewModel.playPlaylist(shown, index, "Most Played") },
                            onRemoveClick = viewModel::removeFromMostPlayed,
                            removeLabel = "Remove from Most Played",
                            onSwipeRight = trackActions::addToQueue,
                            onSwipeLeft = trackActions::favorite,
                            selection = selection,
                            headerAction =
                                clearAllAction(tracks.isNotEmpty()) { pendingClear = ClearTarget.MOST_PLAYED },
                            downloadedIds = downloadedIds,
                            downloads = downloads,
                            playingTrackId = playingId,
                        )
                    }
                    s.isDownloadedOpen -> {
                        BackHandler { viewModel.selectPlaylist(null) }
                        PlaylistDetailView(
                            title = "Downloaded",
                            subtitle =
                                pendingIds.count { it !in downloadedIds }.let { waiting ->
                                    if (waiting > 0) {
                                        "${tracks.size} tracks · $waiting downloading"
                                    } else {
                                        "${tracks.size} tracks · offline"
                                    }
                                },
                            tracks = tracks,
                            emptyText =
                                "No downloaded songs yet. " +
                                    "Long-press any song and select 'Download for offline'.",
                            onBackClick = { viewModel.selectPlaylist(null) },
                            onTrackClick = { shown, index -> viewModel.playPlaylist(shown, index, "Downloaded") },
                            onRemoveClick = viewModel::removeFromDownloaded,
                            pendingIds = pendingIds,
                            removeLabel = "Remove download",
                            onSwipeRight = trackActions::addToQueue,
                            onSwipeLeft = trackActions::favorite,
                            selection = selection,
                            headerAction =
                                clearAllAction(tracks.isNotEmpty()) { pendingClear = ClearTarget.DOWNLOADED },
                            downloadedIds = downloadedIds,
                            downloads = downloads,
                            playingTrackId = playingId,
                        )
                    }
                    selected != null -> {
                        BackHandler { viewModel.selectPlaylist(null) }
                        val readOnly = selected.isLocalFilePlaylist()
                        PlaylistDetailView(
                            title = selected.name,
                            subtitle =
                                if (readOnly) "[LOCAL] ${tracks.size} tracks · read-only" else "${tracks.size} tracks",
                            tracks = tracks,
                            emptyText =
                                if (readOnly) {
                                    "No matching songs found. Rescan in Settings → Local Music."
                                } else {
                                    "Playlist is empty. Long-press any song to add it."
                                },
                            onBackClick = { viewModel.selectPlaylist(null) },
                            onTrackClick = { shown, index -> viewModel.playPlaylist(shown, index, selected.name) },
                            onRemoveClick =
                                if (readOnly) {
                                    null
                                } else {
                                    { track -> viewModel.removeSongFromPlaylist(selected.id, track.id) }
                                },
                            // One save for many songs: the playlist keeps the rest, in order.
                            onRemoveMany =
                                if (readOnly) {
                                    null
                                } else {
                                    { picked ->
                                        val gone = picked.mapTo(HashSet()) { it.id }
                                        viewModel.reorderPlaylist(selected.id, tracks.filterNot { it.id in gone })
                                    }
                                },
                            removeLabel = "Remove from playlist",
                            onReorder =
                                if (readOnly) {
                                    null
                                } else {
                                    { reordered -> viewModel.reorderPlaylist(selected.id, reordered) }
                                },
                            onSwipeRight = trackActions::addToQueue,
                            onSwipeLeft = trackActions::favorite,
                            selection = selection,
                            headerAction =
                                if (readOnly && tracks.isNotEmpty()) {
                                    "[ SAVE AS APP PLAYLIST ]" to { viewModel.saveAsAppPlaylist(selected, tracks) }
                                } else {
                                    null
                                },
                            coverUrl = s.covers[selected.id],
                            onChangeCoverClick =
                                if (!readOnly) {
                                    { coverChangePlaylist = selected }
                                } else {
                                    null
                                },
                            downloadedIds = downloadedIds,
                            downloads = downloads,
                            playingTrackId = playingId,
                        )
                    }
                    else ->
                        PlaylistBrowser(
                            state = s,
                            filter = filter,
                            viewMode = viewMode,
                            onViewModeChange = viewModel::setViewMode,
                            onFavoritesClick = viewModel::openFavorites,
                            onMostPlayedClick = viewModel::openMostPlayed,
                            onDownloadedClick = viewModel::openDownloaded,
                            onCreateClick = { showCreateDialog = true },
                            onImportClick = { showImport = true },
                            onPlaylistClick = { viewModel.selectPlaylist(it.id) },
                            onDeleteClick = { pendingDelete = it },
                            onPlayPlaylist = { viewModel.playNow(it) },
                            onPlayPinned = { viewModel.playNow(null, pinned = it) },
                        )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc ->
                viewModel.createPlaylist(name, desc)
                showCreateDialog = false
            },
        )
    }
    coverChangePlaylist?.let { playlist ->
        ChangeCoverDialog(
            playlist = playlist,
            onDismiss = { coverChangePlaylist = null },
            onPickImage = { uri ->
                viewModel.setPlaylistCoverImage(playlist.id, uri.toString())
                coverChangePlaylist = null
            },
            onSave = { newName, newUrl ->
                viewModel.renamePlaylist(playlist, newName)
                if (newUrl != null) viewModel.updatePlaylistArtwork(playlist.id, newUrl)
                coverChangePlaylist = null
            },
            onResetCover = {
                viewModel.updatePlaylistArtwork(playlist.id, null)
                coverChangePlaylist = null
            },
        )
    }
    // Clearing a whole list always asks first, so it can't happen by a stray tap.
    pendingClear?.let { target ->
        CliConfirmDialog(
            command =
                when (target) {
                    ClearTarget.FAVORITES -> "clear --favorites"
                    ClearTarget.MOST_PLAYED -> "clear --most-played"
                    ClearTarget.DOWNLOADED -> "rm -r downloads/"
                },
            message =
                when (target) {
                    ClearTarget.FAVORITES -> "Remove every song from ♥ Favorites?"
                    ClearTarget.MOST_PLAYED ->
                        "Reset Most Played? It starts counting again from now. Your listening history is kept."
                    ClearTarget.DOWNLOADED ->
                        "Delete every downloaded song? The files are removed from your phone and the songs " +
                            "stream again when played."
                },
            confirmLabel = "Clear all",
            onConfirm = {
                when (target) {
                    ClearTarget.FAVORITES -> viewModel.clearFavorites()
                    ClearTarget.MOST_PLAYED -> viewModel.clearMostPlayed()
                    ClearTarget.DOWNLOADED -> viewModel.clearAllDownloaded()
                }
            },
            onDismiss = { pendingClear = null },
        )
    }
    pendingDelete?.let { playlist ->
        CliConfirmDialog(
            command = "rm \"${playlist.name}\"",
            message = "Delete this playlist? The songs themselves are not deleted.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.deletePlaylist(playlist.id) },
            onDismiss = { pendingDelete = null },
        )
    }
    addToPlaylistTrack?.let { track ->
        SongActionsSheet(track = track, onDismiss = { addToPlaylistTrack = null })
    }
    bulkAddTracks?.let { picked ->
        AddToPlaylistDialog(tracks = picked, onDismiss = { bulkAddTracks = null })
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "> new playlist", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, description.ifBlank { null }) },
                enabled = name.isNotBlank(),
            ) {
                Text("Create", color = CliBeatsAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CliBeatsTextSecondary)
            }
        },
        containerColor = CliBeatsSurface,
    )
}

@Composable
private fun ChangeCoverDialog(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onPickImage: (Uri) -> Unit,
    /** New name (unchanged if the same) and a new cover URL, or null to keep the cover. */
    onSave: (String, String?) -> Unit,
    onResetCover: () -> Unit,
) {
    var name by remember { mutableStateOf(playlist.name) }
    var urlText by remember { mutableStateOf("") }
    // The copy into app storage happens off the main thread in the repository.
    val pickImageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) onPickImage(uri)
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, CliBeatsAccent),
        title = { Text(text = "> edit playlist", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = name.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(text = "Cover", style = MaterialTheme.typography.bodySmall, color = CliBeatsTextSecondary)
                Button(
                    onClick = { pickImageLauncher.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = CliBeatsAccent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("CHOOSE FROM DEVICE")
                }
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Image URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Only a custom cover can be reset; otherwise the first song's art is already shown.
                if (!playlist.artworkUrl.isNullOrBlank()) {
                    TextButton(
                        onClick = onResetCover,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reset to default cover", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, urlText.trim().ifBlank { null }) },
                enabled = name.isNotBlank() && (name.trim() != playlist.name || urlText.isNotBlank()),
            ) {
                Text("Save", color = CliBeatsAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CliBeatsTextSecondary)
            }
        },
        containerColor = CliBeatsSurface,
    )
}

private enum class ClearTarget { FAVORITES, MOST_PLAYED, DOWNLOADED }

/** Orders offered by the sort button in a playlist. */
internal enum class SongSort(
    val label: String,
) {
    TITLE_ASC("Title A → Z"),
    TITLE_DESC("Title Z → A"),
    ARTIST_ASC("Artist A → Z"),
    ARTIST_DESC("Artist Z → A"),
}

/**
 * [tracks] in [sort] order. Letters compare the way people expect (case and accents don't split the list),
 * and songs by the same artist stay in title order.
 */
internal fun sortSongs(
    tracks: List<Track>,
    sort: SongSort,
): List<Track> {
    val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
    val byTitle = Comparator<Track> { a, b -> collator.compare(a.title, b.title) }
    val byArtist = Comparator<Track> { a, b -> collator.compare(a.artist, b.artist) }.then(byTitle)
    return when (sort) {
        SongSort.TITLE_ASC -> tracks.sortedWith(byTitle)
        SongSort.TITLE_DESC -> tracks.sortedWith(byTitle.reversed())
        SongSort.ARTIST_ASC -> tracks.sortedWith(byArtist)
        SongSort.ARTIST_DESC -> tracks.sortedWith(byArtist.reversed())
    }
}

@Composable
internal fun SortButton(onSort: (SongSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Sort,
                contentDescription = "Sort songs",
                tint = CliBeatsAccent,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = CliBeatsSurface) {
            SongSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label, color = CliBeatsTextPrimary) },
                    onClick = {
                        open = false
                        onSort(sort)
                    },
                )
            }
        }
    }
}

private fun clearAllAction(
    enabled: Boolean,
    onClick: () -> Unit,
): Pair<String, () -> Unit>? = if (enabled) "[ CLEAR ALL ]" to onClick else null
