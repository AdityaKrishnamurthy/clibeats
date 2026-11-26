@file:Suppress("TooManyFunctions")

package com.clibeats.domain.repository

import com.clibeats.domain.model.PlaybackState
import com.clibeats.domain.model.RepeatMode
import com.clibeats.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    /** [setQueue], remembering where it was started from ([source]: a playlist's name, "Search", ...). */
    fun playFrom(
        tracks: List<Track>,
        startIndex: Int,
        source: String,
    ) = setQueue(tracks, startIndex)

    fun addToQueue(track: Track)

    fun playNext(track: Track)

    fun moveTrackInQueue(
        fromIndex: Int,
        toIndex: Int,
    )

    fun removeFromQueue(index: Int)

    /** After a local rescan: swaps queued local songs for their freshly scanned details (tags, cover). */
    suspend fun refreshLocalTracks()

    /** Puts [track] back at [index] (undo of a removal); the playing song keeps playing. */
    fun insertIntoQueue(
        index: Int,
        track: Track,
    )

    fun clearQueue()

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    /**
     * The exact playback position right now (the player's own clock, not the twice-a-second update in
     * [playbackState]). Main thread only. Used to keep lyrics in step frame by frame.
     */
    fun livePositionMs(): Long = playbackState.value.positionMs

    fun skipToIndex(index: Int)

    /** Suspends until the saved queue has been restored into the player (used for playback resumption). */
    suspend fun awaitRestored()

    fun skipToNext()

    fun skipToPrevious()

    fun setRepeatMode(mode: RepeatMode)

    fun toggleShuffle()

    /** Songs autoplay's radio added to the queue (for the Queue screen's "from radio" divider). */
    val radioTrackIds: StateFlow<Set<String>>
        get() = MutableStateFlow(emptySet())

    /** Drops the radio songs still to come and starts a new radio from the song playing now. */
    fun refreshRadio() = Unit
}
