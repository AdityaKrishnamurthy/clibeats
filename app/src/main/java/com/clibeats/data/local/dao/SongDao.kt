// ForbiddenImport: data-layer self-imports are legitimate; Phase 0 com.clibeats.data.* pattern is over-broad.
@file:Suppress("ForbiddenImport", "MaxLineLength")

package com.clibeats.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.clibeats.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface SongDao {
    // @Upsert updates in place. REPLACE deletes + re-inserts, which fired ON DELETE CASCADE
    // and silently removed the song from every playlist / liked list on each queue save.
    @Upsert
    suspend fun upsert(song: SongEntity)

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    /** Sets the cover of a stored song that has none (e.g. saved by an earlier import); never replaces one. */
    @Query("UPDATE songs SET artwork_url = :artworkUrl WHERE id = :id AND (artwork_url IS NULL OR artwork_url = '')")
    suspend fun fillMissingArtwork(
        id: String,
        artworkUrl: String,
    )

    /** Adds songs that aren't stored yet and leaves existing rows untouched. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(songs: List<SongEntity>)

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<SongEntity>

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllAsFlow(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY COALESCE(cached_at, 0) DESC")
    fun getRecentlyAddedAsFlow(): Flow<List<SongEntity>>

    @Query(
        """
        SELECT * FROM songs 
        WHERE title LIKE '%' || :query || '%' ESCAPE '\' 
           OR artist LIKE '%' || :query || '%' ESCAPE '\'
        """,
    )
    fun searchAsFlow(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE provider_id = 'local' ORDER BY title COLLATE NOCASE ASC")
    fun getLocalAsFlow(): Flow<List<SongEntity>>

    @Query("SELECT COUNT(*) FROM songs WHERE provider_id = 'local'")
    fun countLocalAsFlow(): Flow<Int>

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN downloaded_tracks dt ON s.id = dt.song_id
        ORDER BY dt.downloaded_at DESC
        """,
    )
    fun getDownloadedSongsAsFlow(): Flow<List<SongEntity>>

    @Query(
        """
        SELECT * FROM songs
        WHERE provider_id = 'local'
          AND (title LIKE '%' || :query || '%' ESCAPE '\'
           OR artist LIKE '%' || :query || '%' ESCAPE '\'
           OR album LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY title COLLATE NOCASE ASC
        """,
    )
    fun searchLocalAsFlow(query: String): Flow<List<SongEntity>>

    /** Online songs the user played after [sinceEpochMs] (Library "clear" moves it), most recent first. */
    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN (
            SELECT song_id, MAX(played_at) AS last_played FROM history
            WHERE played_at > :sinceEpochMs GROUP BY song_id
        ) h ON s.id = h.song_id
        WHERE s.provider_id != 'local'
        ORDER BY h.last_played DESC
        """,
    )
    fun getPlayedOnlineAsFlow(sinceEpochMs: Long = 0L): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE provider_id = 'local'")
    suspend fun getLocalSongs(): List<SongEntity>

    @Query(
        """
        SELECT * FROM songs
        WHERE LOWER(TRIM(artist)) = LOWER(TRIM(:artistName))
        ORDER BY title COLLATE NOCASE ASC
        """,
    )
    suspend fun getByArtist(artistName: String): List<SongEntity>

    @Query(
        """
        SELECT * FROM songs
        WHERE LOWER(TRIM(album)) = LOWER(TRIM(:albumName))
          AND (:artistName = '' OR LOWER(TRIM(artist)) = LOWER(TRIM(:artistName)))
        ORDER BY title COLLATE NOCASE ASC
        """,
    )
    suspend fun getByAlbum(
        albumName: String,
        artistName: String = "",
    ): List<SongEntity>

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()
}

/**
 * Escapes SQL LIKE wildcards so a user query containing `%`, `_`, or `\`
 * matches literally instead of broadening the search (wildcard injection).
 * Used in conjunction with the `ESCAPE '\'` clause in [SongDao.searchAsFlow].
 */
fun String.escapeForLike(): String =
    this.replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
