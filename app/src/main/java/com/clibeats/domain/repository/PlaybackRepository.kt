@file:Suppress("TooManyFunctions")

package com.clibeats.domain.repository

import com.clibeats.domain.model.PlaybackState
import com.clibeats.domain.model.RepeatMode
import com.clibeats.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PlaybackRepository {
    val playbackState: StateFlow<PlaybackState>

    val queueState: StateFlow<List<Track>>

    /** One-off player messages for the UI (e.g. "Couldn't play X — skipped"). */
    val playerMessages: Flow<String>

    /** True while the "end of track" sleep timer is armed. */
    val pauseAtEndOfTrack: StateFlow<Boolean>

    /** Pause when the current song finishes (also with repeat-one). */
    fun setPauseAtEndOfTrack(enabled: Boolean)

    /** Remaining minutes for the active countdown sleep timer, or null if inactive. */
    val sleepTimerCountdown: StateFlow<Int?>

    fun startSleepTimer(minutes: Int)

    fun cancelSleepTimer()

    fun playTrack(track: Track)

    fun setQueue(
        tracks: List<Track>,
        startIndex: Int = 0,
    )

    fun addToQueue(track: Track)

    fun playNext(track: Track)

    fun moveTrackInQueue(
        fromIndex: Int,
        toIndex: Int,
    )

    fun removeFromQueue(index: Int)

    fun clearQueue()

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun skipToIndex(index: Int)

    /** Suspends until the saved queue has been restored into the player (used for playback resumption). */
    suspend fun awaitRestored()

    fun skipToNext()

    fun skipToPrevious()

    fun setRepeatMode(mode: RepeatMode)

    fun toggleShuffle()
}
