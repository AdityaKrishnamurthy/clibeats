// ForbiddenImport: data-layer self-imports are legitimate; Phase 0 com.clibeats.data.* pattern is over-broad.
@file:Suppress("ForbiddenImport", "MaxLineLength", "TooManyFunctions")

package com.clibeats.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.clibeats.data.local.entity.PlaylistEntity
import com.clibeats.data.local.entity.PlaylistSongCrossRef
import com.clibeats.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Upsert // not REPLACE: that cascades and deletes the playlist's songs on every edit
    suspend fun upsert(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY updated_at DESC")
    fun getAllAsFlow(): Flow<List<PlaylistEntity>>

    @Query(
        """
        SELECT * FROM playlists 
        WHERE name LIKE '%' || :query || '%' ESCAPE '\'
        """,
    )
    fun searchAsFlow(query: String): Flow<List<PlaylistEntity>>

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun removeSongFromPlaylist(
        playlistId: String,
        songId: String,
    )

    @Query(
        """
        SELECT songs.* FROM songs
        INNER JOIN playlist_song_cross_ref ON songs.id = playlist_song_cross_ref.song_id
        WHERE playlist_song_cross_ref.playlist_id = :playlistId
        ORDER BY playlist_song_cross_ref.position ASC
        """,
    )
    fun getSongsForPlaylistAsFlow(playlistId: String): Flow<List<SongEntity>>

    @Query(
        "UPDATE playlists SET track_count = " +
            "(SELECT COUNT(*) FROM playlist_song_cross_ref WHERE playlist_id = :playlistId), " +
            "updated_at = :now WHERE id = :playlistId",
    )
    suspend fun refreshTrackCount(
        playlistId: String,
        now: Long = System.currentTimeMillis(),
    )

    /** First song's artwork per playlist (by position), used as the playlist tile cover. */
    @Query(
        "SELECT c.playlist_id AS playlist_id, s.artwork_url AS artwork_url FROM playlist_song_cross_ref c " +
            "INNER JOIN songs s ON s.id = c.song_id " +
            "WHERE c.position = (SELECT MIN(position) FROM playlist_song_cross_ref WHERE playlist_id = c.playlist_id)",
    )
    fun getPlaylistCoversAsFlow(): Flow<List<PlaylistCover>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_song_cross_ref WHERE playlist_id = :playlistId")
    suspend fun nextPosition(playlistId: String): Int

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlist_id = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: String)

    /** Ids of playlists mirrored from M3U/PLS files in the music folder (read-only in the app). */
    @Query("SELECT id FROM playlists WHERE id LIKE 'localpl:%'")
    suspend fun getLocalFilePlaylistIds(): List<String>

    @androidx.room.Transaction
    suspend fun replaceLocalPlaylist(
        playlist: PlaylistEntity,
        songIds: List<String>,
    ) {
        upsert(playlist)
        clearPlaylistSongs(playlist.id)
        songIds.forEachIndexed { index, songId ->
            addSongToPlaylist(PlaylistSongCrossRef(playlist.id, songId, index))
        }
    }

    @androidx.room.Transaction
    suspend fun reorderPlaylistSongs(
        playlistId: String,
        songIds: List<String>,
    ) {
        clearPlaylistSongs(playlistId)
        songIds.forEachIndexed { index, songId ->
            addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId, index))
        }
    }
}

data class PlaylistCover(
    @androidx.room.ColumnInfo(name = "playlist_id") val playlistId: String,
    @androidx.room.ColumnInfo(name = "artwork_url") val artworkUrl: String?,
)
