@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.clibeats.presentation.detail

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clibeats.domain.model.Track
import com.clibeats.presentation.component.SongActionsSheet
import com.clibeats.presentation.component.SongTableRow
import com.clibeats.presentation.component.TrackActionsViewModel
import com.clibeats.presentation.component.TrackArtwork
import com.clibeats.presentation.navigation.AppRoute
import com.clibeats.presentation.search.formatDuration
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary

@Suppress("LongMethod")
@Composable
fun AlbumDetailScreen(
    route: AppRoute.AlbumDetail,
    viewModel: EntityDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(route.albumName, route.artistName, route.browseId, route.providerId) {
        viewModel.loadAlbum(route.albumName, route.artistName, route.browseId, route.providerId)
    }

    val trackActions: TrackActionsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadedIds by trackActions.downloadedTrackIds.collectAsStateWithLifecycle()
    val playingId by trackActions.playingTrackId.collectAsStateWithLifecycle()
    var actionTrack by remember { mutableStateOf<Track?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(CliBeatsBackground),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().background(CliBeatsSurface).padding(16.dp),
        ) {
            Text(
                text = "ALBUM: ${route.albumName.uppercase()}",
                style = MaterialTheme.typography.titleMedium,
                color = CliBeatsTextPrimary,
            )
            (state as? EntityDetailUiState.Success)?.subtitle?.let { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = CliBeatsTextSecondary,
                )
            }
        }

        when (val current = state) {
            is EntityDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CliBeatsAccent)
                }
            }
            is EntityDetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = current.message, color = CliBeatsTextSecondary)
                }
            }
            is EntityDetailUiState.Success -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "[ PLAY ALL ]",
                        style = MaterialTheme.typography.labelSmall,
                        color = CliBeatsAccent,
                        modifier =
                            Modifier
                                .clickable { viewModel.playAll(current.tracks, source = route.albumName) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "[ SHUFFLE ]",
                        style = MaterialTheme.typography.labelSmall,
                        color = CliBeatsAccent,
                        modifier =
                            Modifier
                                .clickable { viewModel.shuffleAll(current.tracks, route.albumName) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }

                if (current.tracks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "> No tracks found for ${route.albumName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CliBeatsTextSecondary,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(current.tracks, key = { idx, t -> "${idx}_${t.id}" }) { index, track ->
                            SongTableRow(
                                trackTitle = track.title,
                                artist = track.artist,
                                duration = formatDuration(track.durationMs),
                                index = index + 1,
                                isDownloaded = track.id in downloadedIds,
                                isNowPlaying = track.id == playingId,
                                artworkContent = { TrackArtwork(artworkUrl = track.artworkUrl, size = 32) },
                                onClick = { viewModel.playAll(current.tracks, index, route.albumName) },
                                onSwipeRight = { trackActions.addToQueue(track) },
                                onSwipeLeft = { trackActions.favorite(track) },
                                onLongPress = { actionTrack = track },
                            )
                        }
                    }
                }
            }
        }
    }

    actionTrack?.let { track ->
        SongActionsSheet(track = track, onDismiss = { actionTrack = null })
    }
}
