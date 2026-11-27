@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.data.download.DownloadStatus
import com.clibeats.data.download.TrackDownloadManager
import com.clibeats.data.lyrics.LocalLyricsRepository
import com.clibeats.data.lyrics.LyricsTranslator
import com.clibeats.data.lyrics.needsPronunciation
import com.clibeats.data.lyrics.online.OnlineLyricsRepository
import com.clibeats.data.lyrics.online.OnlineLyricsResult
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.domain.model.AudioOutput
import com.clibeats.domain.model.Lyrics
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

/** The current song's lyrics, for the Now Playing lyrics view. */
sealed interface LyricsState {
    /** Not looked up yet: lyrics haven't been opened for this song. */
    data object Idle : LyricsState

    /** Being read from the file, or searched for online. */
    data object Loading : LyricsState

    /** Nothing to show: no lyrics in the file, or none found online. */
    data object None : LyricsState

    /** An online song whose lyrics aren't saved, and no connection to look them up. */
    data object Offline : LyricsState

    data class Loaded(
        val lyrics: Lyrics,
        /** Has lines in a non-Latin script, so a pronunciation can be shown. */
        val canPronounce: Boolean = false,
    ) : LyricsState
}

@Suppress("LongParameterList", "TooManyFunctions")
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
        /** Lyrics read from local files; null in tests. */
        private val localLyrics: LocalLyricsRepository? = null,
        /** Lyrics for YouTube Music songs (also when downloaded); null in tests. */
        private val onlineLyrics: OnlineLyricsRepository? = null,
        private val translator: LyricsTranslator? = null,
        private val appPreferences: AppPreferences? = null,
    ) : ViewModel() {
        val playbackState: StateFlow<PlaybackState> = playbackRepository.playbackState

        /** Player output volume, 0..1; shown on Now Playing next to the equalizer shortcut. */
        val volume: StateFlow<Float> = playbackRepository.volume

        fun setVolume(volume: Float) = playbackRepository.setVolume(volume)

        /** Show each lyric line's translation under it (remembered). */
        val showTranslation: StateFlow<Boolean> =
            (appPreferences?.lyricsTranslate ?: flowOf(false)).stateIn(viewModelScope, SharingStarted.Eagerly, false)

        /** Show how each non-Latin lyric line is pronounced, in Latin letters (remembered). */
        val showPronunciation: StateFlow<Boolean> =
            (appPreferences?.lyricsPronounce ?: flowOf(false)).stateIn(viewModelScope, SharingStarted.Eagerly, false)

        fun setShowTranslation(on: Boolean) {
            viewModelScope.launch { appPreferences?.setLyricsTranslate(on) }
        }

        fun setShowPronunciation(on: Boolean) {
            viewModelScope.launch { appPreferences?.setLyricsPronounce(on) }
        }

        private val _extrasLoading = MutableStateFlow(false)

        /** True while translations / pronunciations are being fetched (a small spinner by the buttons). */
        val extrasLoading: StateFlow<Boolean> = _extrasLoading.asStateFlow()

        /** The most complete lyrics seen per song (with the translations and pronunciations fetched so far). */
        private val enriched =
            java.util.Collections.synchronizedMap(
                object : LinkedHashMap<String, Lyrics>() {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>?): Boolean {
                        return size > ENRICHED_KEPT
                    }
                },
            )

        /** The song whose lyrics have been asked for (opened), so they're looked up. */
        private val lyricsRequestedFor = MutableStateFlow<String?>(null)

        /** The playing song's lyrics (local file, saved, or found online), before any translation. */
        @Suppress("OPT_IN_USAGE")
        private val baseLyrics =
            playbackState
                .map { it.currentTrack }
                .distinctUntilChanged { a, b -> a?.id == b?.id && a?.streamUrl == b?.streamUrl }
                .flatMapLatest { track ->
                    if (track == null) return@flatMapLatest flowOf<Pair<Track?, LyricsState>>(null to LyricsState.None)
                    // Looked up only once lyrics are opened for this song (saves data on songs nobody reads).
                    lyricsRequestedFor
                        .map { it == track.id }
                        .distinctUntilChanged()
                        .transformLatest<Boolean, Pair<Track?, LyricsState>> { wanted ->
                            if (!wanted) {
                                emit(track to LyricsState.Idle)
                                return@transformLatest
                            }
                            emit(track to LyricsState.Loading)
                            emit(track to loadLyrics(track))
                        }
                }

        @Suppress("ReturnCount")
        private suspend fun loadLyrics(track: Track): LyricsState {
            if (track.providerId == LOCAL_PROVIDER) {
                val found = runCatching { localLyrics?.lyricsFor(track) }.getOrNull()
                return found?.let(::loaded) ?: LyricsState.None
            }
            val repo = onlineLyrics ?: return LyricsState.None
            return when (val result = runCatching { repo.lyricsFor(track) }.getOrNull()) {
                is OnlineLyricsResult.Found -> loaded(result.lyrics)
                OnlineLyricsResult.NotFound -> LyricsState.None
                OnlineLyricsResult.Unreachable, null -> LyricsState.Offline
            }
        }

        private fun loaded(lyrics: Lyrics) = LyricsState.Loaded(lyrics, canPronounce = hasNonLatin(lyrics))

        /**
         * What the lyrics view shows: the song's lyrics, filled in with translations / pronunciations when those
         * are switched on. The best version so far shows at once; missing lines are fetched in the background and
         * kept, so switching back and forth doesn't ask again. A failed fetch just leaves the lines without them.
         */
        @Suppress("OPT_IN_USAGE")
        val lyrics: StateFlow<LyricsState> =
            combine(baseLyrics, showTranslation, showPronunciation, ::Triple)
                .transformLatest { (base, translate, pronounce) ->
                    val (track, state) = base
                    val loadedState = state as? LyricsState.Loaded
                    if (track == null || loadedState == null) {
                        emit(state)
                        return@transformLatest
                    }
                    val best = enriched[track.id] ?: loadedState.lyrics
                    emit(loaded(best))
                    val tool = translator
                    val wanted = translate || pronounce
                    if (tool == null || !wanted || isComplete(best, translate, pronounce)) {
                        return@transformLatest
                    }
                    _extrasLoading.value = true
                    try {
                        val filled = tool.enrich(best, translate, pronounce, translationTarget())
                        enriched[track.id] = filled
                        emit(loaded(filled))
                        keep(track, filled)
                    } finally {
                        _extrasLoading.value = false
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), LyricsState.None)

        /** Saves lyrics with their translations, so a song's are there next time (and offline, when downloaded). */
        private suspend fun keep(
            track: Track,
            lyrics: Lyrics,
        ) {
            if (track.providerId == LOCAL_PROVIDER) {
                localLyrics?.remember(track, lyrics)
            } else {
                onlineLyrics?.save(track.id, lyrics)
            }
        }

        /** Playback state without 500 ms position ticks, so UI chrome does not recompose twice a second. */
        val playbackMetadata: StateFlow<PlaybackState> =
            playbackState
                .map { it.copy(positionMs = 0L, bufferedPositionMs = 0L) }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.Eagerly, playbackState.value)

        val positionMs: StateFlow<Long> =
            playbackState
                .map { it.positionMs }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0L)

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

        /** The exact position right now, for lyrics (main thread). */
        fun livePositionMs(): Long = playbackRepository.livePositionMs()

        private val _lyricsOpen = MutableStateFlow(false)

        /** The song lyrics were last opened for (so reopening Now Playing on it comes back to them). */
        private var lyricsOpenFor: String? = null

        /**
         * Whether Now Playing shows lyrics instead of the big cover. Kept here, not in the screen, so closing the
         * player and opening it again from the mini player comes back to the lyrics.
         */
        val lyricsOpen: StateFlow<Boolean> = _lyricsOpen.asStateFlow()

        fun setLyricsOpen(open: Boolean) {
            val id = playbackState.value.currentTrack?.id
            _lyricsOpen.value = open
            lyricsOpenFor = if (open) id else null
            if (open && id != null) lyricsRequestedFor.value = id
        }

        /**
         * Opening Now Playing: lyrics come back only if they were open for this same song; for any other song
         * it opens on the cover (and its lyrics aren't looked up until asked for).
         */
        fun lyricsOpenOnEntry(): Boolean {
            val same = _lyricsOpen.value && lyricsOpenFor == playbackState.value.currentTrack?.id
            if (!same) setLyricsOpen(false)
            return same
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
            const val LOCAL_PROVIDER = "local"
            const val ENRICHED_KEPT = 30
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

/** True when some line is in a non-Latin script (Japanese, Hindi, Korean, Kannada, …). */
private fun hasNonLatin(lyrics: Lyrics): Boolean =
    when (lyrics) {
        is Lyrics.Synced -> lyrics.lines.any { needsPronunciation(it.text) }
        is Lyrics.Plain -> needsPronunciation(lyrics.text)
    }

/** Whether every line that wants a translation / pronunciation already has one ("" counts: nothing to show). */
private fun isComplete(
    lyrics: Lyrics,
    translate: Boolean,
    pronounce: Boolean,
): Boolean =
    when (lyrics) {
        is Lyrics.Synced ->
            lyrics.lines.filter { it.text.isNotBlank() }.all { line ->
                (!translate || line.translation != null) &&
                    (!pronounce || !needsPronunciation(line.text) || line.pronunciation != null)
            }
        is Lyrics.Plain -> {
            val lines = lyrics.text.lines()
            lines.indices.filter { lines[it].isNotBlank() }.all { i ->
                (!translate || lyrics.translations.getOrNull(i) != null) &&
                    (!pronounce || !needsPronunciation(lines[i]) || lyrics.pronunciations.getOrNull(i) != null)
            }
        }
    }

/** The phone's language for translations ("en", "hi", "ja"; Chinese keeps its region: "zh-TW"). */
private fun translationTarget(): String {
    val locale = java.util.Locale.getDefault()
    return if (locale.language == "zh") locale.toLanguageTag() else locale.language.ifBlank { "en" }
}
