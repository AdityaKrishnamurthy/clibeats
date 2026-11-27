package com.clibeats.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val streamUrl: String?,
    val providerId: String,
    /** A music video rather than the official audio track (YouTube Music only; not stored). */
    val isVideo: Boolean = false,
)
