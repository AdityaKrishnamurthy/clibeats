@file:Suppress("ktlint:standard:property-naming", "PropertyName")

package com.clibeats.presentation.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.domain.model.Playlist
import com.clibeats.domain.model.Track
import com.clibeats.domain.repository.LibraryRepository
import com.clibeats.domain.repository.PlaybackRepository
import com.clibeats.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistViewModel
    @Inject
    constructor(
        private val playlistRepository: PlaylistRepository,
        private val playbackRepository: PlaybackRepository,
        private val libraryRepository: LibraryRepository? = null,
    ) : ViewModel() {
        private val _selectedPlaylistId = MutableStateFlow<String?>(null)

        private val favorites: kotlinx.coroutines.flow.Flow<List<Track>> =
            libraryRepository?.getLikedSongs() ?: flowOf(emptyList())

        // Selection is part of the combine so opening a playlist updates immediately
        // (it previously only refreshed when the playlist list itself changed).
        val uiState: StateFlow<PlaylistUiState> =
            combine(playlistRepository.getAllPlaylistsAsFlow(), _selectedPlaylistId, favorites) { p, s, f ->
                Triple(p, s, f)
            }.flatMapLatest { (playlists, selectedId, favTracks) ->
                when (selectedId) {
                    null -> flowOf(PlaylistUiState.Success(playlists, favoritesCount = favTracks.size))
                    FAVORITES_ID ->
                        flowOf(
                            PlaylistUiState.Success(
                                playlists = playlists,
                                favoritesCount = favTracks.size,
                                selectedPlaylistTracks = favTracks,
                                isFavoritesOpen = true,
                            ),
                        )
                    else ->
                        playlistRepository.getSongsForPlaylistAsFlow(selectedId).map { songs ->
                            PlaylistUiState.Success(
                                playlists = playlists,
                                favoritesCount = favTracks.size,
                                selectedPlaylist = playlists.find { it.id == selectedId },
                                selectedPlaylistTracks = songs,
                            )
                        }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = PlaylistUiState.Loading,
            )

        fun createPlaylist(
            name: String,
            description: String?,
        ) {
            if (name.isBlank()) return
            viewModelScope.launch {
                val newPlaylist =
                    Playlist(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        description = description?.trim(),
                        artworkUrl = null,
                        trackCount = 0,
                        isOwned = true,
                        providerId = "local",
                    )
                playlistRepository.upsertPlaylist(newPlaylist)
            }
        }

        fun deletePlaylist(playlistId: String) {
            viewModelScope.launch {
                if (_selectedPlaylistId.value == playlistId) {
                    _selectedPlaylistId.value = null
                }
                playlistRepository.deletePlaylist(playlistId)
            }
        }

        fun selectPlaylist(playlistId: String?) {
            _selectedPlaylistId.value = playlistId
        }

        fun openFavorites() {
            _selectedPlaylistId.value = FAVORITES_ID
        }

        fun addToQueue(track: Track) {
            playbackRepository.addToQueue(track)
        }

        fun removeSongFromPlaylist(
            playlistId: String,
            songId: String,
        ) {
            viewModelScope.launch {
                playlistRepository.removeSongFromPlaylist(playlistId, songId)
            }
        }

        /** Removes [track] from ♥ Favorites (it is currently liked, so toggling unlikes it). */
        fun removeFromFavorites(track: Track) {
            viewModelScope.launch { libraryRepository?.toggleLike(track) }
        }

        /** Copies a read-only local file playlist into a new editable app playlist and opens it. */
        fun saveAsAppPlaylist(
            source: Playlist,
            tracks: List<Track>,
        ) {
            viewModelScope.launch {
                val id = playlistRepository.createAppPlaylist(source.name, tracks.map { it.id })
                _selectedPlaylistId.value = id
            }
        }

        fun playPlaylist(
            tracks: List<Track>,
            startIndex: Int = 0,
        ) {
            if (tracks.isNotEmpty()) {
                playbackRepository.setQueue(tracks, startIndex)
            }
        }

        private companion object {
            const val FAVORITES_ID = "__favorites__"
        }
    }
