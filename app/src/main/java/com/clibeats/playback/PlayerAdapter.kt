// ForbiddenImport: playback engine implementation imports are legitimate
@file:Suppress("ForbiddenImport", "TooManyFunctions", "ReturnCount")

package com.clibeats.playback

import android.content.Context
import android.content.Intent
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.clibeats.data.local.dao.DownloadedTrackDao
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
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
        /** Offline downloads: queued songs that are downloaded play from the file, not the network. */
        private val downloadedTrackDao: dagger.Lazy<DownloadedTrackDao>? = null,
    ) {
        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private val queueController = QueueController()
        private val sleepTimer =
            SleepTimerController(
                scope = scope,
                setPauseAtEndOfMediaItems = { enabled -> player.pauseAtEndOfMediaItems = enabled },
                onCountdownExpired = ::pause,
            )
        private val formatResolver =
            TrackFormatResolver(
                context = context,
                streamResolver = streamResolver,
                scope = scope,
                onBitrateResolved = ::updateState,
            )

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

        val queueFlow: StateFlow<List<Track>> = queueController.queueFlow

        /** The radio songs, for the Queue screen's "from radio" divider. */
        val radioTrackIds: StateFlow<Set<String>> = queueController.radioTrackIds

        val queueGeneration: Int get() = queueController.queueGeneration

        /** One-off messages for the UI, e.g. a song that couldn't be played and was skipped. */
        private val _messages = MutableSharedFlow<String>(extraBufferCapacity = MESSAGE_BUFFER)
        val messages: SharedFlow<String> = _messages

        val pauseAtEndOfTrack: StateFlow<Boolean> = sleepTimer.pauseAtEndOfTrack
        val sleepTimerCountdown: StateFlow<Int?> = sleepTimer.sleepTimerCountdown

        private var consecutiveFailures = 0
        private var retriedMediaId: String? = null
        private var tickerJob: Job? = null

        /** Song id -> downloaded file path, kept in step with the downloads table. */
        private val downloadedFiles = ConcurrentHashMap<String, String>()
        private val queueExecutor =
            PlayerQueueExecutor(
                player = player,
                queueController = queueController,
                startPlaybackService = ::startPlaybackService,
                updateState = ::updateState,
                toMediaItem = { it.toMediaItem() },
            )

        init {
            watchDownloads()
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
                            consecutiveFailures = 0
                        }
                        if (playbackState == Player.STATE_ENDED) sleepTimer.setPauseAtEndOfTrack(false)
                        updateState()
                    }

                    override fun onPlayWhenReadyChanged(
                        playWhenReady: Boolean,
                        reason: Int,
                    ) {
                        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                            sleepTimer.setPauseAtEndOfTrack(false)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        DiagnosticLogger.logError("player", "MEDIA_ERROR", error.message ?: "ExoPlayer error")
                        if (retryAfterStreamError(error)) return
                        skipAfterError()
                        updateState()
                    }

                    override fun onTracksChanged(tracks: Tracks) = updateState()

                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int,
                    ) {
                        retriedMediaId = null
                        val current = player.currentMediaItemIndex
                        queueController.getOrNull(current)?.let { track ->
                            val duration = player.duration.takeIf { it > 0 } ?: track.durationMs
                            formatResolver.resolveTrackBitrate(track, duration)
                        }
                        updateWakeMode()
                        updateState()
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int,
                    ) = updateState()

                    override fun onRepeatModeChanged(repeatMode: Int) = updateState()

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updateState()
                },
            )
        }

        private fun startPlaybackService() {
            runCatching { context.startService(Intent(context, PlaybackService::class.java)) }
        }

        /** Where [trackId] was played from, or null when that isn't known. */
        fun sourceOf(trackId: String): String? = queueController.sourceOf(trackId)

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

        private fun watchDownloads() {
            val dao = downloadedTrackDao ?: return
            scope.launch {
                val flow = withContext(Dispatchers.IO) { dao.get().getAllFlow() }
                flow.collect { list -> onDownloadsChanged(list.associate { it.songId to it.filePath }) }
            }
        }

        private fun onDownloadsChanged(files: Map<String, String>) =
            synchronized(queueController) {
                val changed =
                    (downloadedFiles.keys + files.keys).filter { downloadedFiles[it] != files[it] }.toSet()
                if (changed.isEmpty()) return@synchronized
                val removed = changed.filter { it !in files }.toSet()
                downloadedFiles.clear()
                downloadedFiles.putAll(files)
                val current = player.currentMediaItemIndex
                queueController.snapshot().forEachIndexed { index, track ->
                    if (track.id !in changed || index >= player.mediaItemCount) return@forEachIndexed
                    if (index != current) {
                        player.replaceMediaItem(index, track.toMediaItem())
                    } else if (track.id in removed && PlayerMediaBridge.isFileItem(player.getMediaItemAt(index))) {
                        val position = player.currentPosition.coerceAtLeast(0L)
                        val playWhenReady = player.playWhenReady
                        player.replaceMediaItem(index, track.toMediaItem())
                        player.seekTo(index, position)
                        player.prepare()
                        player.playWhenReady = playWhenReady
                    }
                }
            }

        fun playTrack(track: Track) = queueExecutor.playTrack(track)

        fun setQueue(
            tracks: List<Track>,
            startIndex: Int = 0,
            source: String? = null,
        ) = queueExecutor.setQueue(tracks, startIndex, source)

        fun addToQueue(track: Track) = queueExecutor.addToQueue(track)

        /** Autoplay's related songs: queued at the end and remembered as the radio. */
        fun addAutoplayTracks(tracks: List<Track>) = queueExecutor.addAutoplayTracks(tracks)

        /** Drops the radio songs still to come; autoplay then starts over from the current song. */
        fun removeUpcomingRadio() = queueExecutor.removeUpcomingRadio()

        fun addAllToQueue(tracks: List<Track>) = queueExecutor.addAllToQueue(tracks)

        fun playNext(track: Track) = queueExecutor.playNext(track)

        @Suppress("LongParameterList")
        fun restoreQueue(
            tracks: List<Track>,
            startIndex: Int = 0,
            positionMs: Long = 0L,
            repeatMode: RepeatMode = RepeatMode.OFF,
            shuffleEnabled: Boolean = false,
            radioIds: Set<String> = emptySet(),
        ) = queueExecutor.restoreQueue(tracks, startIndex, positionMs, repeatMode, shuffleEnabled, radioIds)

        fun play() {
            if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
                player.prepare()
            }
            if (player.mediaItemCount > 0) startPlaybackService()
            player.play()
        }

        fun pause() = player.pause()

        fun seekTo(positionMs: Long) = player.seekTo(positionMs)

        fun skipToIndex(index: Int) = queueExecutor.skipToIndex(index)

        fun skipToNext() {
            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
        }

        fun nextTrack(): Track? = queueController.getOrNull(player.nextMediaItemIndex)

        fun previousTrack(): Track? = queueController.getOrNull(player.previousMediaItemIndex)

        fun playForward(track: Track) = queueExecutor.playForward(track)

        fun skipToPrevious() {
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
            } else if (player.mediaItemCount > 0) {
                player.seekTo(0L)
            }
        }

        fun currentPositionMs(): Long = player.currentPosition.coerceAtLeast(0L)

        fun currentIndex(): Int = if (queueController.isEmpty()) -1 else player.currentMediaItemIndex

        fun playBeforeCurrent(track: Track) = queueExecutor.playBeforeCurrent(track)

        fun setRepeatMode(mode: RepeatMode) {
            player.repeatMode =
                when (mode) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                }
            updateState()
        }

        fun toggleShuffle() = queueExecutor.toggleShuffle()

        fun moveTrack(
            fromIndex: Int,
            toIndex: Int,
        ) = queueExecutor.moveTrack(fromIndex, toIndex)

        fun removeFromQueue(index: Int) = queueExecutor.removeFromQueue(index)

        fun refreshTracks(fresh: Map<String, Track>) = queueExecutor.refreshTracks(fresh)

        fun insertAt(
            index: Int,
            track: Track,
        ) = queueExecutor.insertAt(index, track)

        fun clearQueue() = queueExecutor.clearQueue()

        fun setPauseAtEndOfTrack(enabled: Boolean) = sleepTimer.setPauseAtEndOfTrack(enabled)

        fun startSleepTimer(minutes: Int) = sleepTimer.startSleepTimer(minutes)

        fun cancelSleepTimer() = sleepTimer.cancelSleepTimer()

        private fun updateWakeMode() {
            val local = PlayerMediaBridge.isLocalMediaItem(player.currentMediaItem)
            player.setWakeMode(if (local) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NETWORK)
        }

        private fun updateState() {
            _playbackState.value = PlayerMediaBridge.buildPlaybackState(player, queueController, formatResolver)
        }

        private fun skipAfterError() {
            val title = queueController.getOrNull(player.currentMediaItemIndex)?.title ?: "this song"
            consecutiveFailures++
            if (player.hasNextMediaItem() && consecutiveFailures < MAX_CONSECUTIVE_FAILURES) {
                _messages.tryEmit("Couldn't play \"$title\" — skipped")
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            } else {
                _messages.tryEmit("Couldn't play \"$title\"")
            }
        }

        private fun retryAfterStreamError(error: PlaybackException): Boolean =
            synchronized(queueController) {
                val retryable = PlayerMediaBridge.isRetryableError(error)
                val index = player.currentMediaItemIndex
                val track = queueController.getOrNull(index)
                if (!retryable || track == null || retriedMediaId == track.id) return@synchronized false
                retriedMediaId = track.id
                streamResolver.invalidate(track.providerId, track.id)
                val position = player.currentPosition.coerceAtLeast(0L)
                val fresh = track.copy(streamUrl = track.streamUrl?.takeUnless { it.startsWith("http") })
                queueController.replaceTrackAt(index, fresh)
                player.replaceMediaItem(index, fresh.toMediaItem())
                player.seekTo(index, position)
                player.prepare()
                player.play()
                true
            }

        private fun Track.toMediaItem(): MediaItem = PlayerMediaBridge.buildMediaItem(this, downloadedFiles[id])

        companion object {
            private const val POLL_INTERVAL_MS = 500L
            private const val MESSAGE_BUFFER = 4
            private const val MAX_CONSECUTIVE_FAILURES = 5

            fun codecLabel(mime: String): String = TrackFormatResolver.codecLabel(mime)

            fun bitDepthOf(pcmEncoding: Int): Int? = TrackFormatResolver.bitDepthOf(pcmEncoding)
        }
    }

internal const val QUEUED_SOURCE = "Queue"
internal const val HISTORY_SOURCE = "Recently played"
