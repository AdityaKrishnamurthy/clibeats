@file:Suppress("ktlint:standard:function-naming", "MatchingDeclarationName")

package com.clibeats.presentation.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.domain.model.Playlist
import com.clibeats.domain.model.Track
import com.clibeats.domain.repository.PlaylistRepository
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsDivider
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddToPlaylistViewModel
    @Inject
    constructor(
        private val playlistRepository: PlaylistRepository,
    ) : ViewModel() {
        /** Only app playlists: local M3U/PLS mirrors are read-only. */
        val appPlaylists: StateFlow<List<Playlist>> =
            playlistRepository.getAllPlaylistsAsFlow()
                .map { all -> all.filterNot { it.isLocalFilePlaylist() } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

        fun add(
            track: Track,
            playlistId: String,
        ) {
            viewModelScope.launch { runCatching { playlistRepository.addTrackToPlaylist(playlistId, track) } }
        }

        fun createAndAdd(
            name: String,
            track: Track,
        ) {
            if (name.isBlank()) return
            viewModelScope.launch {
                runCatching {
                    val id = playlistRepository.createAppPlaylist(name.trim(), emptyList())
                    playlistRepository.addTrackToPlaylist(id, track)
                }
            }
        }
    }

/** True for playlists mirrored from M3U/M3U8/PLS files in the music folder. */
fun Playlist.isLocalFilePlaylist(): Boolean = id.startsWith("localpl:")

/** Shared "add to playlist" picker used by long-press on any song row. */
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun AddToPlaylistDialog(
    track: Track,
    onDismiss: () -> Unit,
    viewModel: AddToPlaylistViewModel = hiltViewModel(),
) {
    val playlists by viewModel.appPlaylists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, CliBeatsAccent),
        title = {
            Column {
                Text("> add --to-playlist", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${track.title} — ${track.artist}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CliBeatsTextSecondary,
                    maxLines = 1,
                )
            }
        },
        text = {
            Column {
                if (creating) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text("Playlist name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = "[ + NEW PLAYLIST ]",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CliBeatsAccent,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { creating = true }
                                .padding(vertical = 12.dp),
                    )
                    HorizontalDivider(color = CliBeatsDivider)
                    if (playlists.isEmpty()) {
                        Text(
                            text = "No playlists yet.",
                            color = CliBeatsTextSecondary,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(playlists, key = { it.id }) { playlist ->
                                Text(
                                    text = "[ ${playlist.name} ]  ${playlist.trackCount}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.add(track, playlist.id)
                                                onDismiss()
                                            }
                                            .padding(vertical = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    onClick = {
                        viewModel.createAndAdd(newName, track)
                        onDismiss()
                    },
                    enabled = newName.isNotBlank(),
                ) { Text("Create & add") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
