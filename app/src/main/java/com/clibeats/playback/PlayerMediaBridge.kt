// ForbiddenImport: playback engine helper intentionally sits in playback package with TrackFormatResolver.
@file:Suppress("ForbiddenImport")

package com.clibeats.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.clibeats.domain.model.PlaybackState
import com.clibeats.domain.model.RepeatMode
import com.clibeats.domain.model.Track
import com.clibeats.util.ARTWORK_FULL_PX
import com.clibeats.util.artworkUrlFor
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object PlayerMediaBridge {
    private const val LOCAL_PROVIDER_ID = "local"

    fun buildMediaItem(
        track: Track,
        downloadedPath: String?,
    ): MediaItem {
        val direct = track.streamUrl?.takeIf { it.isNotBlank() && !it.contains("expire") }
        val downloaded = downloadedPath?.takeIf { track.providerId != LOCAL_PROVIDER_ID }
        val uri =
            downloaded?.let { Uri.fromFile(File(it)) }
                ?: direct?.let { Uri.parse(it) }
                ?: TrackUriResolver.placeholderUri(track)

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(artworkUrlFor(track.artworkUrl, ARTWORK_FULL_PX)?.let { Uri.parse(it) })
                    .setExtras(
                        Bundle().apply {
                            putString(ArtworkBitmapLoader.ARTWORK_SOURCE_KEY, track.artworkUrl)
                        },
                    ).build(),
            ).build()
    }

    fun isFileItem(item: MediaItem?): Boolean = item?.localConfiguration?.uri?.scheme == "file"

    fun isLocalMediaItem(item: MediaItem?): Boolean {
        val scheme = item?.localConfiguration?.uri?.scheme
        return scheme == "content" || scheme == "file"
    }

    fun mappedRepeatMode(playerRepeatMode: Int): RepeatMode =
        when (playerRepeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }

    fun buildPlaybackState(
        player: ExoPlayer,
        queueController: QueueController,
        formatResolver: TrackFormatResolver,
    ): PlaybackState {
        val currentIndex = player.currentMediaItemIndex
        val currentTrack = queueController.getOrNull(currentIndex)
        val format = runCatching { player.audioFormat }.getOrNull()
        val durationMs = player.duration.takeIf { it > 0 } ?: (currentTrack?.durationMs ?: 0L)
        val bitrateKbps = formatResolver.inspectBitrate(currentTrack, format, durationMs)

        return PlaybackState(
            currentTrack = currentTrack,
            currentIndex = if (currentTrack != null) currentIndex else -1,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
            repeatMode = mappedRepeatMode(player.repeatMode),
            shuffleEnabled = queueController.shuffleEnabled,
            codec = format?.sampleMimeType?.let(TrackFormatResolver::codecLabel),
            bitrateKbps = bitrateKbps,
            sampleRateHz = format?.sampleRate?.takeIf { it > 0 },
            bitDepth = format?.pcmEncoding?.let(TrackFormatResolver::bitDepthOf),
            durationMs = durationMs,
            fromDownload =
                currentTrack?.providerId != LOCAL_PROVIDER_ID && isFileItem(player.currentMediaItem),
            queueSource = currentTrack?.let { queueController.sourceOf(it.id) },
            fromRadio = queueController.isRadio(currentTrack?.id),
        )
    }

    fun isRetryableError(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
}
