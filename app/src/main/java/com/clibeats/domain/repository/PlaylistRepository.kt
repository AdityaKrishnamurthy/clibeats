package com.clibeats.domain.repository

import com.clibeats.domain.model.Playlist
import com.clibeats.domain.model.Track
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface PlaylistRepository {
    fun getAllPlaylistsAsFlow(): Flow<List<Playlist>>

    /** playlistId -> cover artwork URL (first song's artwork). */
    fun getPlaylistCoversAsFlow(): Flow<Map<String, String?>>

    suspend fun getPlaylistById(id: String): Playlist?

    suspend fun upsertPlaylist(playlist: Playlist)

    suspend fun deletePlaylist(id: String)

    fun getSongsForPlaylistAsFlow(playlistId: String): Flow<List<Track>>

    suspend fun addSongToPlaylist(
        playlistId: String,
        songId: String,
        position: Int,
    )

    suspend fun removeSongFromPlaylist(
        playlistId: String,
        songId: String,
    )

    suspend fun reorderPlaylistSongs(
        playlistId: String,
        songIds: List<String>,
    )

    /** Saves [track] to the library (if needed) and appends it to the end of the playlist. */
    suspend fun addTrackToPlaylist(
        playlistId: String,
        track: Track,
    )

    /** Creates an editable app playlist with [songIds] (already in the songs table) and returns its id. */
    suspend fun createAppPlaylist(
        name: String,
        songIds: List<String>,
    ): String
}
