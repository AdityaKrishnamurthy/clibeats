package com.clibeats.domain.model

enum class RepeatMode { OFF, ONE, ALL }

data class PlaybackState(
    val currentTrack: Track?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val bufferedPositionMs: Long,
    val repeatMode: RepeatMode,
    val shuffleEnabled: Boolean,
    // Audio format of the current stream (null until the player knows it).
    val codec: String? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val durationMs: Long = 0L,
    /** The song is playing from its offline download (a file), not streamed. */
    val fromDownload: Boolean = false,
    // Position of the playing song in the queue (-1 if none); the same song can be queued twice.
    val currentIndex: Int = -1,
)
