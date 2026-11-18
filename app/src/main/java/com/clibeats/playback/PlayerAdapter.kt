// ForbiddenImport: playback engine implementation imports are legitimate
@file:Suppress("ForbiddenImport", "TooManyFunctions", "ReturnCount", "MagicNumber")

package com.clibeats.playback

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.clibeats.domain.model.PlaybackState
import com.clibeats.domain.model.RepeatMode
import com.clibeats.domain.model.Track
import com.clibeats.playback.service.PlaybackService
import com.clibeats.util.DiagnosticLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Singleton
class PlayerAdapter
    @Inject
    constructor(
        private val player: ExoPlayer,
        private val streamResolver: StreamResolver,
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Starts the media session foreground service so playback keeps running
         * in the background and media controls appear in the notification
         * shade / lock screen. The service wraps the same singleton [ExoPlayer].
         */
        private fun startPlaybackService() {
            val intent = Intent(context, PlaybackService::class.java)
            context.startForegroundService(intent)
        }

        private val _playbackState =
            MutableStateFlow(
                PlaybackState(
                    currentTrack = null,
                    isPlaying = false,
                    positionMs = 0L,
                    bufferedPositionMs = 0L,
                    repeatMode = RepeatMode.OFF,
                    shuffleEnabled = false,
                ),
            )
        val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

        private val _queueFlow = MutableStateFlow<List<Track>>(emptyList())
        val queueFlow: StateFlow<List<Track>> = _queueFlow.asStateFlow()

        private val trackList = mutableListOf<Track>()
        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private var tickerJob: Job? = null

        private fun startPositionTicker() {
            tickerJob?.cancel()
            tickerJob =
                scope.launch {
                    while (player.isPlaying) {
                        updateState()
                        delay(POLL_INTERVAL_MS)
                    }
                }
        }

        private fun stopPositionTicker() {
            tickerJob?.cancel()
            tickerJob = null
        }

        init {
            player.addListener(
                object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            DiagnosticLogger.logMediaPlaying("player")
                            startPositionTicker()
                        } else {
                            stopPositionTicker()
                        }
                        updateState()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            DiagnosticLogger.logMediaReady("player")
                        }
                        updateState()
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        DiagnosticLogger.logError("player", "MEDIA_ERROR", error.message ?: "ExoPlayer error")
                        if (retryAfterStreamError(error)) return
                        updateState()
                    }

                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        updateState()
                    }

                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int,
                    ) {
                        retriedMediaId = null
                        updateState()
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int,
                    ) {
                        updateState()
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        updateState()
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        updateState()
                    }
                },
            )
        }

        fun playTrack(track: Track) {
            startPlaybackService()
            trackList.clear()
            trackList.add(track)
            val mediaItem = track.toMediaItem()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            updateState()
        }

        fun setQueue(
            tracks: List<Track>,
            startIndex: Int = 0,
        ) {
            startPlaybackService()
            trackList.clear()
            trackList.addAll(tracks)
            val mediaItems = tracks.map { it.toMediaItem() }
            player.setMediaItems(mediaItems, startIndex, 0L)
            player.prepare()
            player.play()
            updateState()
        }

        fun addToQueue(track: Track) {
            startPlaybackService()
            trackList.add(track)
            val mediaItem = track.toMediaItem()
            player.addMediaItem(mediaItem)
            if (player.playbackState == Player.STATE_IDLE || player.mediaItemCount == 1) {
                player.prepare()
            }
            updateState()
        }

        fun playNext(track: Track) {
            startPlaybackService()
            val targetIndex =
                if (trackList.isEmpty()) {
                    0
                } else {
                    (player.currentMediaItemIndex + 1).coerceIn(0, trackList.size)
                }
            trackList.add(targetIndex, track)
            player.addMediaItem(targetIndex, track.toMediaItem())
            if (player.playbackState == Player.STATE_IDLE || player.mediaItemCount == 1) {
                player.prepare()
            }
            updateState()
        }

        fun restoreQueue(
            tracks: List<Track>,
            startIndex: Int = 0,
            positionMs: Long = 0L,
            repeatMode: RepeatMode = RepeatMode.OFF,
            shuffleEnabled: Boolean = false,
        ) {
            if (tracks.isEmpty()) return
            trackList.clear()
            trackList.addAll(tracks)
            val mediaItems = tracks.map { it.toMediaItem() }
            val validIndex = startIndex.coerceIn(tracks.indices)
            player.setMediaItems(mediaItems, validIndex, positionMs.coerceAtLeast(0L))
            player.repeatMode =
                when (repeatMode) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                }
            player.shuffleModeEnabled = shuffleEnabled
            player.prepare()
            updateState()
        }

        fun play() {
            player.play()
        }

        fun pause() {
            player.pause()
        }

        fun seekTo(positionMs: Long) {
            player.seekTo(positionMs)
        }

        fun skipToIndex(index: Int) {
            if (index in trackList.indices) {
                player.seekTo(index, 0L)
                player.play()
            }
        }

        fun skipToNext() {
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            }
        }

        fun skipToPrevious() {
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
            }
        }

        fun setRepeatMode(mode: RepeatMode) {
            player.repeatMode =
                when (mode) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                }
            updateState()
        }

        fun toggleShuffle() {
            player.shuffleModeEnabled = !player.shuffleModeEnabled
            updateState()
        }

        fun moveTrack(
            fromIndex: Int,
            toIndex: Int,
        ) {
            if (fromIndex in trackList.indices && toIndex in trackList.indices && fromIndex != toIndex) {
                val track = trackList.removeAt(fromIndex)
                trackList.add(toIndex, track)
                player.moveMediaItem(fromIndex, toIndex)
                updateState()
            }
        }

        fun removeFromQueue(index: Int) {
            if (index in trackList.indices) {
                trackList.removeAt(index)
                player.removeMediaItem(index)
                updateState()
            }
        }

        fun clearQueue() {
            trackList.clear()
            player.clearMediaItems()
            updateState()
        }

        // Local files often carry no bitrate in the container; derive it from file size / duration once.
        private val localBitrateKbps = mutableMapOf<String, Int>()

        private fun estimateLocalBitrate(track: Track) {
            val uri = track.streamUrl?.takeIf { track.providerId == "local" } ?: return
            val durationMs = player.duration.takeIf { it > 0 } ?: return
            if (localBitrateKbps.containsKey(track.id)) return
            localBitrateKbps[track.id] = 0
            scope.launch {
                val bytes =
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver
                                .openAssetFileDescriptor(android.net.Uri.parse(uri), "r")
                                ?.use { it.length }
                        }.getOrNull()
                    } ?: return@launch
                if (bytes > 0) {
                    localBitrateKbps[track.id] = (bytes * BITS_PER_BYTE / durationMs).toInt()
                    updateState()
                }
            }
        }

        private fun updateState() {
            _queueFlow.value = trackList.toList()
            val currentIndex = player.currentMediaItemIndex
            val currentTrack = if (currentIndex in trackList.indices) trackList[currentIndex] else null
            val format = runCatching { player.audioFormat }.getOrNull()
            val formatBitrate =
                format?.let { f ->
                    listOf(f.averageBitrate, f.bitrate, f.peakBitrate).firstOrNull { it > 0 }?.div(BITS_PER_KBIT)
                }
            if (format != null && formatBitrate == null && currentTrack != null) estimateLocalBitrate(currentTrack)
            val mappedRepeatMode =
                when (player.repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                }

            _playbackState.value =
                PlaybackState(
                    currentTrack = currentTrack,
                    isPlaying = player.isPlaying,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
                    repeatMode = mappedRepeatMode,
                    shuffleEnabled = player.shuffleModeEnabled,
                    codec = format?.sampleMimeType?.let(::codecLabel),
                    bitrateKbps = formatBitrate ?: currentTrack?.let { localBitrateKbps[it.id] },
                    sampleRateHz = format?.sampleRate?.takeIf { it > 0 },
                    bitDepth = format?.pcmEncoding?.let(::bitDepthOf),
                    durationMs = player.duration.takeIf { it > 0 } ?: (currentTrack?.durationMs ?: 0L),
                )
        }

        private var retriedMediaId: String? = null

        /**
         * Expired/forbidden stream (HTTP 403/410) or a failed resolve: invalidate the cached
         * URL and re-prepare once. The resolving data source fetches a fresh URL.
         */
        private fun retryAfterStreamError(error: androidx.media3.common.PlaybackException): Boolean {
            val retryable =
                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED
            val index = player.currentMediaItemIndex
            val track = trackList.getOrNull(index)
            if (!retryable || track == null || retriedMediaId == track.id) return false
            retriedMediaId = track.id
            streamResolver.invalidate(track.providerId, track.id)
            val position = player.currentPosition.coerceAtLeast(0L)
            // Drop any stale direct URL so the placeholder resolver runs again.
            val fresh = track.copy(streamUrl = track.streamUrl?.takeUnless { it.startsWith("http") })
            trackList[index] = fresh
            player.replaceMediaItem(index, fresh.toMediaItem())
            player.seekTo(index, position)
            player.prepare()
            player.play()
            return true
        }

        private fun Track.toMediaItem(): MediaItem {
            // Local files and non-expiring provider URLs play directly. Remote tracks get a placeholder URI that
            // TrackUriResolver resolves (cache file -> fresh stream URL) when ExoPlayer opens it,
            // so queued tracks are playable and expired URLs never reach the network.
            // Signed (expiring) URLs, e.g. googlevideo `expire=`, always go through the resolver.
            val direct = streamUrl?.takeIf { it.isNotBlank() && !it.contains("expire") }
            val uri = direct?.let { android.net.Uri.parse(it) } ?: TrackUriResolver.placeholderUri(this)

            return MediaItem.Builder()
                .setMediaId(id)
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .setAlbumTitle(album)
                        .setArtworkUri(artworkUrl?.let { android.net.Uri.parse(it) })
                        .build(),
                )
                .build()
        }

        companion object {
            private const val POLL_INTERVAL_MS = 500L
            private const val BITS_PER_BYTE = 8L
            private const val BITS_PER_KBIT = 1000

            fun codecLabel(mime: String): String =
                when (mime) {
                    androidx.media3.common.MimeTypes.AUDIO_FLAC -> "FLAC"
                    androidx.media3.common.MimeTypes.AUDIO_MPEG -> "MP3"
                    androidx.media3.common.MimeTypes.AUDIO_OPUS -> "OPUS"
                    androidx.media3.common.MimeTypes.AUDIO_AAC -> "AAC"
                    androidx.media3.common.MimeTypes.AUDIO_RAW, androidx.media3.common.MimeTypes.AUDIO_WAV -> "WAV"
                    androidx.media3.common.MimeTypes.AUDIO_VORBIS -> "VORBIS"
                    androidx.media3.common.MimeTypes.AUDIO_ALAC -> "ALAC"
                    else -> mime.substringAfter('/').uppercase()
                }

            fun bitDepthOf(pcmEncoding: Int): Int? =
                when (pcmEncoding) {
                    androidx.media3.common.C.ENCODING_PCM_16BIT -> 16
                    androidx.media3.common.C.ENCODING_PCM_24BIT -> 24
                    androidx.media3.common.C.ENCODING_PCM_32BIT, androidx.media3.common.C.ENCODING_PCM_FLOAT -> 32
                    androidx.media3.common.C.ENCODING_PCM_8BIT -> 8
                    else -> null
                }
        }
    }
