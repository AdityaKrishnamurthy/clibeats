package com.clibeats.data.discord

import com.clibeats.domain.model.PlaybackState
import com.clibeats.domain.model.RepeatMode
import com.clibeats.domain.model.Track
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiscordPresenceTest {
    private val track =
        Track(
            id = "track-1",
            title = "Zaalima",
            artist = "Arijit Singh",
            album = "Raees",
            durationMs = 299_000,
            artworkUrl = "https://example.com/cover.jpg",
            streamUrl = null,
            providerId = "youtube_music",
        )

    @Test
    fun playingTrack_becomesAListeningActivity() {
        val activity =
            PlaybackState(
                currentTrack = track,
                isPlaying = true,
                positionMs = 42_000,
                bufferedPositionMs = 60_000,
                repeatMode = RepeatMode.OFF,
                shuffleEnabled = false,
                durationMs = 299_000,
            ).toDiscordActivity()

        assertThat(activity).isNotNull()
        requireNotNull(activity)
        assertThat(activity.details).isEqualTo("Zaalima")
        assertThat(activity.state).isEqualTo("Arijit Singh")
        assertThat(activity.largeImageUrl).isEqualTo("https://example.com/cover.jpg")
        assertThat(activity.isPlaying).isTrue()
        assertThat(activity.activityType).isEqualTo(2)
        assertThat(activity.positionMs).isEqualTo(42_000)
        assertThat(activity.durationMs).isEqualTo(299_000)
    }

    @Test
    fun noTrack_meansNoActivity() {
        val state =
            PlaybackState(
                currentTrack = null,
                isPlaying = false,
                positionMs = 0,
                bufferedPositionMs = 0,
                repeatMode = RepeatMode.OFF,
                shuffleEnabled = false,
            )
        assertThat(state.toDiscordActivity()).isNull()
    }

    @Test
    fun activityFallsBackToTheTrackLength() {
        val activity =
            PlaybackState(
                currentTrack = track,
                isPlaying = false,
                positionMs = 0,
                bufferedPositionMs = 0,
                repeatMode = RepeatMode.OFF,
                shuffleEnabled = false,
            ).toDiscordActivity()

        assertThat(activity?.durationMs).isEqualTo(299_000)
        assertThat(activity?.isPlaying).isFalse()
    }

    @Test
    fun publishingRequiresEnabledSdkAndAnActivity() {
        val activity = trackActivity()
        assertThat(DiscordPresenceState(true, DiscordSdkStatus.READY, activity).canPublish).isTrue()
        assertThat(DiscordPresenceState(false, DiscordSdkStatus.READY, activity).canPublish).isFalse()
        assertThat(DiscordPresenceState(true, DiscordSdkStatus.SDK_REQUIRED, activity).canPublish).isFalse()
        assertThat(DiscordPresenceState(true, DiscordSdkStatus.READY, null).canPublish).isFalse()
    }

    @Test
    fun unavailableGateway_neverClaimsToPublish() {
        val gateway = UnavailableDiscordPresenceGateway()
        assertThat(gateway.sdkStatus).isEqualTo(DiscordSdkStatus.SDK_REQUIRED)
        gateway.publish(trackActivity())
    }

    private fun trackActivity(): DiscordActivity =
        PlaybackState(
            currentTrack = track,
            isPlaying = true,
            positionMs = 0,
            bufferedPositionMs = 0,
            repeatMode = RepeatMode.OFF,
            shuffleEnabled = false,
            durationMs = 299_000,
        ).toDiscordActivity()!!
}
