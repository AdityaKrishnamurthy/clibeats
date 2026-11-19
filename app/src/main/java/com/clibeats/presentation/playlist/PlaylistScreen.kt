@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:multiline-expression-wrapping",
    "MagicNumber",
    "LongMethod",
    "FunctionNaming",
    "LongParameterList",
)

package com.clibeats.presentation.playlist

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.clibeats.domain.model.Playlist
import com.clibeats.domain.model.Track
import com.clibeats.presentation.component.ARTWORK_THUMB_PX
import com.clibeats.presentation.component.AddToPlaylistDialog
import com.clibeats.presentation.component.FavoritesViewModel
import com.clibeats.presentation.component.SongTableRow
import com.clibeats.presentation.component.TrackArtwork
import com.clibeats.presentation.component.artworkUrlFor
import com.clibeats.presentation.component.isLocalFilePlaylist
import com.clibeats.presentation.search.formatDuration
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsBorderInactive
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsSurfaceVariant
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import java.io.File

@Suppress("CyclomaticComplexMethod")
@Composable
fun PlaylistScreen(
    showGridRequested: Boolean = false,
    onShowGridHandled: () -> Unit = {},
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    // Re-tapping the Playlists tab while inside a playlist goes back to the grid.
    LaunchedEffect(showGridRequested) {
        if (showGridRequested) {
            viewModel.selectPlaylist(null)
            onShowGridHandled()
        }
    }
    val favorites: FavoritesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Playlist?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var coverChangePlaylist by remember { mutableStateOf<Playlist?>(null) }

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
                            title = "♥ FAVORITES",
                            subtitle = "${tracks.size} tracks",
                            tracks = tracks,
                            emptyText = "No favorites yet. Tap ♥ in Now Playing to add songs.",
                            onBackClick = { viewModel.selectPlaylist(null) },
                            onTrackClick = { index -> viewModel.playPlaylist(tracks, index) },
                            onRemoveClick = viewModel::removeFromFavorites,
                            removeLabel = "Remove from favorites",
                            onSwipeRight = viewModel::addToQueue,
                            onSwipeLeft = favorites::add,
                            onLongPress = { addToPlaylistTrack = it },
                        )
                    }
                    s.isMostPlayedOpen -> {
                        BackHandler { viewModel.selectPlaylist(null) }
                        PlaylistDetailView(
                            title = "MOST PLAYED",
                            subtitle = "${tracks.size} tracks · by play count",
                            tracks = tracks,
                            emptyText = "Nothing played yet. Your most played songs will show up here.",
                            onBackClick = { viewModel.selectPlaylist(null) },
                            onTrackClick = { index -> viewModel.playPlaylist(tracks, index) },
                            onRemoveClick = null,
                            removeLabel = "",
                            onSwipeRight = viewModel::addToQueue,
                            onSwipeLeft = favorites::add,
                            onLongPress = { addToPlaylistTrack = it },
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
                            onTrackClick = { index -> viewModel.playPlaylist(tracks, index) },
                            onRemoveClick =
                                if (readOnly) {
                                    null
                                } else {
                                    { track -> viewModel.removeSongFromPlaylist(selected.id, track.id) }
                                },
                            removeLabel = "Remove from playlist",
                            onSwipeRight = viewModel::addToQueue,
                            onSwipeLeft = favorites::add,
                            onLongPress = { addToPlaylistTrack = it },
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
                        )
                    }
                    else ->
                        PlaylistGridView(
                            state = s,
                            onFavoritesClick = viewModel::openFavorites,
                            onMostPlayedClick = viewModel::openMostPlayed,
                            onCreateClick = { showCreateDialog = true },
                            onPlaylistClick = { viewModel.selectPlaylist(it.id) },
                            onDeleteClick = { pendingDelete = it },
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
            currentCover = (state as? PlaylistUiState.Success)?.covers?.get(playlist.id),
            onDismiss = { coverChangePlaylist = null },
            onSave = { newUrl ->
                viewModel.updatePlaylistArtwork(playlist.id, newUrl)
                coverChangePlaylist = null
            },
        )
    }
    pendingDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("> rm \"${playlist.name}\"") },
            text = { Text("Delete this playlist? The songs themselves are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(playlist.id)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
            containerColor = CliBeatsSurface,
        )
    }
    addToPlaylistTrack?.let { track ->
        AddToPlaylistDialog(track = track, onDismiss = { addToPlaylistTrack = null })
    }
}

@Suppress("LongMethod", "LongParameterList")
@Composable
private fun PlaylistGridView(
    state: PlaylistUiState.Success,
    onFavoritesClick: () -> Unit,
    onMostPlayedClick: () -> Unit,
    onCreateClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onDeleteClick: (Playlist) -> Unit,
) {
    val (local, app) = state.playlists.partition { it.isLocalFilePlaylist() }
    val fullSpan: (LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = TILE_MIN_DP.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "fav") {
            PlaylistTile(
                title = "♥ FAVORITES",
                subtitle = "${state.favoritesCount} tracks",
                coverUrl = state.favoritesCover,
                glyph = "♥",
                highlight = true,
                onClick = onFavoritesClick,
            )
        }
        item(key = "most") {
            PlaylistTile(
                title = "MOST PLAYED",
                subtitle = "${state.mostPlayedCount} tracks",
                coverUrl = state.mostPlayedCover,
                glyph = "▲",
                highlight = true,
                onClick = onMostPlayedClick,
            )
        }
        items(app, key = { it.id }) { playlist ->
            PlaylistTile(
                title = playlist.name,
                subtitle = "${playlist.trackCount} tracks",
                coverUrl = state.covers[playlist.id],
                glyph = "♫",
                onClick = { onPlaylistClick(playlist) },
                onLongClick = { onDeleteClick(playlist) },
                longClickLabel = "Delete playlist",
            )
        }
        item(key = "new") { NewPlaylistTile(onClick = onCreateClick) }

        item(key = "local_label", span = fullSpan) {
            Column {
                SectionLabel("LOCAL PLAYLISTS")
                HintText("From M3U/PLS files in your music folder (read-only). Rescan in Settings → Local Music.")
            }
        }
        items(local, key = { it.id }) { playlist ->
            PlaylistTile(
                title = playlist.name,
                subtitle = "[LOCAL] ${playlist.trackCount} tracks",
                coverUrl = state.covers[playlist.id],
                glyph = "♫",
                onClick = { onPlaylistClick(playlist) },
            )
        }
    }
}

@Suppress("LongParameterList")
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistTile(
    title: String,
    subtitle: String,
    coverUrl: String?,
    glyph: String,
    onClick: () -> Unit,
    highlight: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
) {
    val borderColor = if (highlight) CliBeatsAccent else CliBeatsBorderInactive
    Column(
        modifier =
            Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = longClickLabel,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CliBeatsSurfaceVariant)
                    .border(1.dp, borderColor, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUrl.isNullOrBlank()) {
                Text(text = glyph, style = MaterialTheme.typography.headlineMedium, color = borderColor)
            } else {
                AsyncImage(
                    // Same thumbnail URL as song rows: usually already cached, so tiles fill instantly.
                    model = artworkUrlFor(coverUrl, ARTWORK_THUMB_PX * 2),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlight) CliBeatsAccent else CliBeatsTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = CliBeatsTextSecondary, maxLines = 1)
    }
}

@Composable
private fun NewPlaylistTile(onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClickLabel = "Create new playlist", onClick = onClick)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, CliBeatsAccent, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "+", style = MaterialTheme.typography.headlineLarge, color = CliBeatsAccent)
        }
        Text(
            text = "NEW PLAYLIST",
            style = MaterialTheme.typography.bodySmall,
            color = CliBeatsAccent,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(text = " ", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = CliBeatsAccent,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = CliBeatsTextSecondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun PlaylistDetailView(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    emptyText: String,
    onBackClick: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onRemoveClick: ((Track) -> Unit)?,
    removeLabel: String,
    onSwipeRight: (Track) -> Unit,
    onSwipeLeft: (Track) -> Unit,
    onLongPress: (Track) -> Unit,
    headerAction: Pair<String, () -> Unit>? = null,
    coverUrl: String? = null,
    onChangeCoverClick: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CliBeatsSurface)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back to playlists",
                    tint = CliBeatsTextPrimary,
                )
            }
            if (onChangeCoverClick != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CliBeatsSurfaceVariant)
                        .border(1.dp, CliBeatsAccent, RoundedCornerShape(4.dp))
                        .clickable(onClick = onChangeCoverClick),
                    contentAlignment = Alignment.Center,
                ) {
                    if (coverUrl.isNullOrBlank()) {
                        Text(text = "♫", style = MaterialTheme.typography.titleMedium, color = CliBeatsAccent)
                    } else {
                        AsyncImage(
                            model = artworkUrlFor(coverUrl, ARTWORK_THUMB_PX * 2),
                            contentDescription = "Change playlist cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = CliBeatsTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = CliBeatsTextSecondary)
            }
            if (onChangeCoverClick != null) {
                IconButton(onClick = onChangeCoverClick) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Change playlist cover",
                        tint = CliBeatsAccent,
                    )
                }
            }
        }
        if (headerAction != null) {
            Text(
                text = headerAction.first,
                style = MaterialTheme.typography.labelMedium,
                color = CliBeatsAccent,
                modifier = Modifier
                    .clickable(onClick = headerAction.second)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(text = emptyText, style = MaterialTheme.typography.bodyMedium, color = CliBeatsTextSecondary)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
                itemsIndexed(items = tracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
                    SongTableRow(
                        trackTitle = track.title,
                        artist = track.artist,
                        duration = formatDuration(track.durationMs),
                        index = index + 1,
                        artworkContent = { TrackArtwork(artworkUrl = track.artworkUrl, size = 32) },
                        onClick = { onTrackClick(index) },
                        onSwipeRight = { onSwipeRight(track) },
                        onSwipeLeft = { onSwipeLeft(track) },
                        onLongPress = { onLongPress(track) },
                        actions =
                            onRemoveClick?.let { remove ->
                                {
                                    IconButton(onClick = { remove(track) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "$removeLabel: ${track.title}",
                                            tint = CliBeatsTextSecondary,
                                        )
                                    }
                                }
                            },
                    )
                }
            }
        }
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
    currentCover: String?,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    val context = LocalContext.current
    var urlText by remember { mutableStateOf("") }
    val pickImageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    val dir = File(context.filesDir, "covers").also { it.mkdirs() }
                    val file = File(dir, "playlist_${playlist.id}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    onSave(file.toURI().toString())
                }
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, CliBeatsAccent),
        title = { Text(text = "> edit --cover", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Change thumbnail for \"${playlist.name}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = CliBeatsTextSecondary,
                )
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
                if (!currentCover.isNullOrBlank()) {
                    TextButton(
                        onClick = { onSave(null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reset to default cover", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(urlText.trim().ifBlank { null }) },
                enabled = urlText.isNotBlank(),
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

private const val TILE_MIN_DP = 100 // 3 columns on most phones
