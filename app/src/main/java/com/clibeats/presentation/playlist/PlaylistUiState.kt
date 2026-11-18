package com.clibeats.presentation.playlist

import com.clibeats.domain.model.Playlist
import com.clibeats.domain.model.Track

sealed interface PlaylistUiState {
    data object Loading : PlaylistUiState

    data class Success(
        /** All playlists (app + local file mirrors). */
        val playlists: List<Playlist>,
        val favoritesCount: Int = 0,
        val selectedPlaylist: Playlist? = null,
        val selectedPlaylistTracks: List<Track> = emptyList(),
        /** True when the pinned ♥ Favorites list is open (selectedPlaylist is null then). */
        val isFavoritesOpen: Boolean = false,
    ) : PlaylistUiState
}
