// ForbiddenImport: HistoryRepository returns HistoryEntity (data-layer type) per Plan 03-03 spec.
@file:Suppress("ForbiddenImport", "MaxLineLength")

package com.clibeats.domain.repository

import com.clibeats.data.local.entity.HistoryEntity
import com.clibeats.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface HistoryRepository {
    /** How many times each song was played (song id -> plays), all time. */
    fun getPlayCounts(): Flow<Map<String, Int>> = flowOf(emptyMap())

    fun getRecentHistoryAsFlow(limit: Int = 50): Flow<List<HistoryEntity>>

    fun getRecentlyPlayedTracks(limit: Int = 50): Flow<List<Track>>

    /** Songs ordered by play count (ties: most recent first). */
    fun getMostPlayedTracks(limit: Int = 50): Flow<List<Track>>

    /**
     * Most played, counting only plays after [sinceEpochMs] and, per song, after its entry in
     * [songSince] (set when a song is removed from Most Played). Ties go to the most recently played.
     */
    fun getMostPlayedTracksSince(
        sinceEpochMs: Long,
        songSince: Map<String, Long>,
        limit: Int,
    ): Flow<List<Track>>

    /** One entry per song played after [sinceEpochMs], most recent first. */
    fun getRecentlyPlayedTracksSince(
        sinceEpochMs: Long,
        limit: Int,
    ): Flow<List<Track>>

    suspend fun recordPlay(
        songId: String,
        providerId: String,
    )

    suspend fun clearHistoryBefore(epochMs: Long)

    suspend fun clearAllHistory()
}
