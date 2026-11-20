@file:Suppress("TooManyFunctions")

package com.clibeats.domain.repository

import com.clibeats.domain.model.Album
import com.clibeats.domain.model.Artist
import com.clibeats.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun getLikedSongs(): Flow<List<Track>>

    fun isLiked(trackId: String): Flow<Boolean>

    suspend fun toggleLike(track: Track)

    /** Likes [track] if it isn't already (never unlikes). */
    suspend fun addToFavorites(track: Track)

    /** Stores a new ♥ Favorites order ([trackIds] top first, as listed). */
    suspend fun reorderFavorites(trackIds: List<String>)

    /** Removes every song from ♥ Favorites. */
    suspend fun clearFavorites()

    fun getSavedAlbums(): Flow<List<Album>>

    fun searchSavedAlbums(query: String): Flow<List<Album>>

    fun isAlbumSaved(albumId: String): Flow<Boolean>

    suspend fun toggleSaveAlbum(album: Album)

    fun getSavedArtists(): Flow<List<Artist>>

    fun searchSavedArtists(query: String): Flow<List<Artist>>

    fun isArtistSaved(artistId: String): Flow<Boolean>

    suspend fun toggleSaveArtist(artist: Artist)
}
