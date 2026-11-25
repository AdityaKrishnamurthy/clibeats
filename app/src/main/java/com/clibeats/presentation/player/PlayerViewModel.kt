@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.data.download.DownloadStatus
import com.clibeats.data.download.TrackDownloadManager
import com.clibeats.domain.model.AudioOutput
import com.clibeats.domain.model.PlaybackState
import com.clibeats.domain.model.Playlist
import com.clibeats.domain.model.RepeatMode
import com.clibeats.domain.model.Track
import com.clibeats.domain.repository.LibraryRepository
import com.clibeats.domain.repository.PlaybackRepository
import com.clibeats.domain.repository.PlaylistRepository
import com.clibeats.playback.AudioOutputMonitor
import com.clibeats.presentation.component.Notices
import com.clibeats.presentation.component.isLocalFilePlaylist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sleep timer state: off, a countdown, or "pause when the current track ends". */
sealed interface SleepTimer {
    data object Off : SleepTimer

    data class Countdown(val remainingMin: Int) : SleepTimer

    data object EndOfTrack : SleepTimer
}

@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val playbackRepository: PlaybackRepository,
        private val playlistRepository: PlaylistRepository? = null,
        private val libraryRepository: LibraryRepository? = null,
        private val audioOutputMonitor: AudioOutputMonitor? = null,
        private val notices: Notices? = null,
        private val downloadManager: TrackDownloadManager? = null,
    ) : ViewModel() {
        val playbackState: StateFlow<PlaybackState> = playbackRepository.playbackState

        /** Where the music is playing (null in previews/tests). */
        val audioOutput: StateFlow<AudioOutput>? = audioOutputMonitor?.current

        fun refreshAudioOutput() {
            audioOutputMonitor?.refresh()
        }

        /** Opens Android's output picker (needs an Activity context). */
        fun openOutputSwitcher(activityContext: android.content.Context) {
            audioOutputMonitor?.openSwitcher(activityContext)
        }

        val queue: StateFlow<List<Track>> = playbackRepository.queueState

        /** Whether the current track is in Favorites (liked songs). */
        @Suppress("OPT_IN_USAGE")
        val isCurrentLiked: StateFlow<Boolean> =
            playbackState
                .map { it.currentTrack?.id }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null || libraryRepository == null) flowOf(false) else libraryRepository.isLiked(id)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)

        /** Short label shown just above the pressed button ("ONE" / "ALL" / "OFF", "ON" / "OFF"). */
        private val _hint = MutableStateFlow<ButtonHint?>(null)
        val hint: StateFlow<ButtonHint?> = _hint.asStateFlow()
        private var toastJob: Job? = null

        val playlists: StateFlow<List<Playlist>> =
            (playlistRepository?.getAllPlaylistsAsFlow() ?: flowOf(emptyList()))
                // Local M3U/PLS playlists are read-only, so they aren't add-to targets.
                .map { all -> all.filterNot { it.isLocalFilePlaylist() } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

        val sleepTimer: StateFlow<SleepTimer> =
            combine(
                playbackRepository.sleepTimerCountdown,
                playbackRepository.pauseAtEndOfTrack,
            ) { countdown, endOfTrack ->
                when {
                    countdown != null -> SleepTimer.Countdown(countdown)
                    endOfTrack -> SleepTimer.EndOfTrack
                    else -> SleepTimer.Off
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, SleepTimer.Off)

        /** Short-lived player message (e.g. a song that couldn't be played), shown above the player bar. */
        @Suppress("OPT_IN_USAGE")
        val playerMessage: StateFlow<String?> =
            playbackRepository.playerMessages
                .transformLatest { message ->
                    emit(message)
                    delay(MESSAGE_MS)
                    emit(null)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

        /** Short confirmation (e.g. "Added to queue"), shown above the player bar for a moment. */
        @Suppress("OPT_IN_USAGE")
        val notice: StateFlow<String?> =
            (notices?.messages ?: emptyFlow())
                .transformLatest { message ->
                    emit(message)
                    delay(NOTICE_MS)
                    emit(null)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

        /** Live progress, completion, and failure state for offline track downloads. */
        @Suppress("OPT_IN_USAGE")
        val downloadBanner: StateFlow<DownloadBanner?> =
            (downloadManager?.activeDownload ?: emptyFlow())
                .transformLatest { active ->
                    if (active == null) {
                        emit(null)
                        return@transformLatest
                    }
                    when (val status = active.status) {
                        is DownloadStatus.Downloading -> {
                            val pct = status.progressPercent
                            val suffix = if (pct > 0) " \u00b7 $pct%" else ""
                            // Bulk downloads: how many are still to come.
                            val more = if (active.waiting > 0) " \u00b7 ${active.waiting} more" else ""
                            emit(
                                DownloadBanner(
                                    text = "Downloading \u201c${active.title}\u201d$suffix$more",
                                    progressPercent = pct,
                                ),
                            )
                        }
                        is DownloadStatus.Completed -> {
                            emit(DownloadBanner(text = "Downloaded \u201c${active.title}\u201d"))
                            delay(NOTICE_MS)
                            emit(null)
                        }
                        is DownloadStatus.Failed -> {
                            emit(
                                DownloadBanner(
                                    text = "Download failed: \u201c${active.title}\u201d (${status.message})",
                                    isError = true,
                                ),
                            )
                            delay(MESSAGE_MS)
                            emit(null)
                        }
                        DownloadStatus.Idle, DownloadStatus.Queued -> emit(null)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

        fun playTrack(track: Track) {
            playbackRepository.playTrack(track)
        }

        /** Plays [tracks] as the queue from [index], so next/previous and auto-advance work. */
        fun playQueue(
            tracks: List<Track>,
            index: Int,
        ) {
            playbackRepository.setQueue(tracks, index)
        }

        fun onPlayPauseClick() {
            if (playbackState.value.isPlaying) {
                playbackRepository.pause()
            } else {
                playbackRepository.play()
            }
        }

        fun onSkipNextClick() {
            playbackRepository.skipToNext()
        }

        fun onSkipPreviousClick() {
            playbackRepository.skipToPrevious()
        }

        /** Cycles OFF -> ALL (repeat queue) -> ONE (repeat track) -> OFF. */
        fun onRepeatClick() {
            val next =
                when (playbackState.value.repeatMode) {
                    RepeatMode.OFF -> RepeatMode.ALL
                    RepeatMode.ALL -> RepeatMode.ONE
                    RepeatMode.ONE -> RepeatMode.OFF
                }
            playbackRepository.setRepeatMode(next)
            showHint(HintTarget.REPEAT, next.name)
        }

        fun onShuffleClick() {
            val enabling = !playbackState.value.shuffleEnabled
            playbackRepository.toggleShuffle()
            showHint(HintTarget.SHUFFLE, if (enabling) "ON" else "OFF")
        }

        fun toggleLike() {
            val track = playbackState.value.currentTrack ?: return
            viewModelScope.launch { runCatching { libraryRepository?.toggleLike(track) } }
        }

        private fun showHint(
            target: HintTarget,
            text: String,
        ) {
            // Replace any visible hint instantly; hide after TOAST_MS.
            toastJob?.cancel()
            _hint.value = ButtonHint(target, text)
            toastJob =
                viewModelScope.launch {
                    delay(TOAST_MS)
                    _hint.value = null
                }
        }

        fun seekTo(positionMs: Long) {
            playbackRepository.seekTo(positionMs)
        }

        fun addToPlaylist(
            track: Track,
            playlistId: String,
        ) {
            viewModelScope.launch {
                runCatching { playlistRepository?.addTrackToPlaylist(playlistId, track) }
            }
        }

        /** [minutes] > 0 starts a countdown; 0 turns it off; null = pause at end of current track. */
        fun setSleepTimer(minutes: Int?) {
            when {
                minutes == null -> {
                    playbackRepository.cancelSleepTimer()
                    playbackRepository.setPauseAtEndOfTrack(true)
                }
                minutes <= 0 -> {
                    playbackRepository.cancelSleepTimer()
                    playbackRepository.setPauseAtEndOfTrack(false)
                }
                else -> {
                    playbackRepository.startSleepTimer(minutes)
                }
            }
        }

        private companion object {
            const val MINUTE_MS = 60_000L
            const val TOAST_MS = 1_500L
            const val MESSAGE_MS = 3_000L
            const val NOTICE_MS = 1_800L
        }
    }

enum class HintTarget { REPEAT, SHUFFLE }

data class ButtonHint(
    val target: HintTarget,
    val text: String,
)

data class DownloadBanner(
    val text: String,
    val progressPercent: Int? = null,
    val isError: Boolean = false,
)
