package com.clibeats.domain.repository

import com.clibeats.domain.model.Track
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface SongRepository {
    fun getAllTracksAsFlow(): Flow<List<Track>>

    fun getRecentlyAddedTracksAsFlow(): Flow<List<Track>>

    fun searchTracksAsFlow(query: String): Flow<List<Track>>

    /** Tracks scanned from the device folder (providerId = "local"). */
    fun getLocalTracksAsFlow(): Flow<List<Track>>

    /** Number of songs scanned from the device's music folders. */
    fun getLocalTrackCountAsFlow(): Flow<Int>

    fun searchLocalTracksAsFlow(query: String): Flow<List<Track>>

    /** Online tracks with at least one play in history, most recently played first. */
    fun getPlayedOnlineTracksAsFlow(): Flow<List<Track>>

    suspend fun getTrackById(id: String): Track?

    suspend fun upsertTrack(track: Track)

    suspend fun upsertTracks(tracks: List<Track>)

    suspend fun deleteTrack(id: String)
}
