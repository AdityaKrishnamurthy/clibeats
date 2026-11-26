@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.clibeats.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clibeats.domain.model.Track
import com.clibeats.presentation.component.AddToPlaylistDialog
import com.clibeats.presentation.component.SongActionsSheet
import com.clibeats.presentation.component.TrackActionsViewModel
import com.clibeats.presentation.navigation.AppRoute
import com.clibeats.presentation.playlist.PlaylistDetailView
import com.clibeats.presentation.playlist.SelectionActions
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary

/** Artist tracks screen using the same TUI detail view as Library's OpenedGroupView. */
@Composable
fun ArtistDetailScreen(
    route: AppRoute.ArtistDetail,
    onBack: () -> Unit = {},
    viewModel: EntityDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(route.artistName, route.browseId, route.providerId) {
        viewModel.loadArtist(route.artistName, route.browseId, route.providerId)
    }
    EntityDetailContent(
        fallbackTitle = route.artistName,
        fallbackSubtitle = { "Artist · ${it.size} tracks" },
        emptyText = "No tracks found for ${route.artistName}.",
        onBack = onBack,
        viewModel = viewModel,
    )
}

@Composable
internal fun EntityDetailContent(
    fallbackTitle: String,
    fallbackSubtitle: (List<Track>) -> String,
    emptyText: String,
    onBack: () -> Unit,
    viewModel: EntityDetailViewModel,
) {
    val trackActions: TrackActionsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadedIds by trackActions.downloadedTrackIds.collectAsStateWithLifecycle()
    val downloads by trackActions.downloads.collectAsStateWithLifecycle()
    val playingId by trackActions.playingTrackId.collectAsStateWithLifecycle()
    var actionTrack by remember { mutableStateOf<Track?>(null) }
    var bulkAddTracks by remember { mutableStateOf<List<Track>?>(null) }

    actionTrack?.let { SongActionsSheet(track = it, onDismiss = { actionTrack = null }) }
    bulkAddTracks?.let { AddToPlaylistDialog(tracks = it, onDismiss = { bulkAddTracks = null }) }

    val selection =
        remember(trackActions) {
            SelectionActions(
                download = trackActions::downloadAll,
                addToPlaylist = { bulkAddTracks = it },
                queue = trackActions::addAllToQueue,
                playNext = trackActions::playAllNext,
                favorite = trackActions::favoriteAll,
                more = { actionTrack = it },
            )
        }

    Column(modifier = Modifier.fillMaxSize().background(CliBeatsBackground)) {
        when (val current = state) {
            is EntityDetailUiState.Loading ->
                DetailStateScaffold(title = fallbackTitle, onBack = onBack) {
                    CircularProgressIndicator(color = CliBeatsAccent)
                }
            is EntityDetailUiState.Error ->
                DetailStateScaffold(title = fallbackTitle, onBack = onBack) {
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CliBeatsTextSecondary,
                    )
                }
            is EntityDetailUiState.Success ->
                PlaylistDetailView(
                    title = current.title,
                    subtitle = current.subtitle ?: fallbackSubtitle(current.tracks),
                    tracks = current.tracks,
                    emptyText = emptyText,
                    onBackClick = onBack,
                    onTrackClick = { list, index -> viewModel.playAll(list, index, current.title) },
                    onRemoveClick = null,
                    removeLabel = "",
                    onSwipeRight = trackActions::addToQueue,
                    onSwipeLeft = trackActions::favorite,
                    selection = selection,
                    coverUrl = current.tracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl,
                    downloadedIds = downloadedIds,
                    downloads = downloads,
                    playingTrackId = playingId,
                    playAll = true,
                )
        }
    }
}

@Composable
internal fun DetailStateScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "< Back",
                style = MaterialTheme.typography.labelLarge,
                color = CliBeatsAccent,
                modifier = Modifier.clickable(role = Role.Button, onClick = onBack).padding(vertical = 4.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = CliBeatsTextPrimary,
            )
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}
