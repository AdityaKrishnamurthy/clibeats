package com.clibeats.data.discord

import com.clibeats.domain.model.PlaybackState

/**
 * What the Discord Social SDK should show. Kept free of SDK types so it can be unit-tested and so
 * the native bridge can be supplied without changing the playback layer.
 */
data class DiscordActivity(
    val details: String,
    val state: String,
    val largeImageUrl: String?,
    val isPlaying: Boolean,
    val durationMs: Long,
    val positionMs: Long,
) {
    /** Discord "Listening" activity payload. */
    val activityType: Int = ACTIVITY_TYPE_LISTENING

    companion object {
        const val ACTIVITY_TYPE_LISTENING = 2
    }
}

enum class DiscordSdkStatus {
    /** The official Social SDK is bundled and can publish to the Discord app. */
    READY,

    /** No SDK in this build: the preview works, publishing is disabled rather than using a user token. */
    SDK_REQUIRED,
}

data class DiscordPresenceState(
    val enabled: Boolean = false,
    val sdkStatus: DiscordSdkStatus = DiscordSdkStatus.SDK_REQUIRED,
    val activity: DiscordActivity? = null,
) {
    val canPublish: Boolean get() = enabled && sdkStatus == DiscordSdkStatus.READY && activity != null
}

/** Maps playback to the card Discord shows; null means "nothing to show". */
fun PlaybackState.toDiscordActivity(): DiscordActivity? {
    val track = currentTrack ?: return null
    return DiscordActivity(
        details = track.title,
        state = track.artist,
        largeImageUrl = track.artworkUrl,
        isPlaying = isPlaying,
        durationMs = durationMs.takeIf { it > 0 } ?: track.durationMs,
        positionMs = positionMs.coerceAtLeast(0L),
    )
}
