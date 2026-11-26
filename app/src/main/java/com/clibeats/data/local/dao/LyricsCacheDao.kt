// ForbiddenImport: data-layer self-imports are legitimate; Phase 0 com.clibeats.data.* pattern is over-broad.
@file:Suppress("ForbiddenImport")

package com.clibeats.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.clibeats.data.local.entity.LyricsCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsCacheDao {
    @Query("SELECT * FROM lyrics_cache WHERE song_id = :songId")
    suspend fun getById(songId: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LyricsCacheEntity)

    /** Songs with saved lyrics (not counting "none found" marks). */
    @Query("SELECT COUNT(*) FROM lyrics_cache WHERE synced_lrc IS NOT NULL")
    fun countFlow(): Flow<Int>

    @Query("SELECT COALESCE(SUM(LENGTH(CAST(synced_lrc AS BLOB))), 0) FROM lyrics_cache")
    fun sizeBytesFlow(): Flow<Long>

    /** Keeps the [keep] most recent songs' lyrics; downloaded songs always keep theirs. */
    @Query(
        "DELETE FROM lyrics_cache WHERE song_id NOT IN (SELECT song_id FROM downloaded_tracks) " +
            "AND song_id NOT IN (SELECT song_id FROM lyrics_cache " +
            "WHERE song_id NOT IN (SELECT song_id FROM downloaded_tracks) ORDER BY fetched_at DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)

    /** Removes saved lyrics except downloaded songs' (those are needed offline). */
    @Query("DELETE FROM lyrics_cache WHERE song_id NOT IN (SELECT song_id FROM downloaded_tracks)")
    suspend fun clearNotDownloaded()
}
