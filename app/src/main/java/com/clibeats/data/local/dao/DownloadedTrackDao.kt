// ForbiddenImport: data-layer self-imports are legitimate; Phase 0 com.clibeats.data.* pattern is over-broad.
@file:Suppress("ForbiddenImport")

package com.clibeats.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.clibeats.data.local.entity.DownloadedTrackEntity
import kotlinx.coroutines.flow.Flow

data class DownloadedSongItem(
    val songId: String,
    val title: String,
    val artist: String,
    val sizeBytes: Long,
    val downloadedAt: Long,
    val artworkUrl: String? = null,
)

@Dao
interface DownloadedTrackDao {
    @Query("SELECT * FROM downloaded_tracks WHERE song_id = :songId")
    suspend fun getById(songId: String): DownloadedTrackEntity?

    @Query("SELECT * FROM downloaded_tracks WHERE song_id = :songId")
    fun getByIdFlow(songId: String): Flow<DownloadedTrackEntity?>

    @Query("SELECT * FROM downloaded_tracks")
    suspend fun getAll(): List<DownloadedTrackEntity>

    @Query("SELECT * FROM downloaded_tracks")
    fun getAllFlow(): Flow<List<DownloadedTrackEntity>>

    @Query(
        """
        SELECT dt.song_id AS songId, s.title AS title, s.artist AS artist,
               dt.size_bytes AS sizeBytes, dt.downloaded_at AS downloadedAt,
               s.artwork_url AS artworkUrl
        FROM downloaded_tracks dt
        INNER JOIN songs s ON dt.song_id = s.id
        ORDER BY dt.downloaded_at DESC
        """,
    )
    fun getDownloadedSongItemsFlow(): Flow<List<DownloadedSongItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks WHERE song_id = :songId")
    suspend fun deleteById(songId: String)

    @Query("DELETE FROM downloaded_tracks")
    suspend fun deleteAll()
}
