@file:Suppress("ForbiddenImport")

package com.clibeats.data.lyrics

import com.clibeats.data.lyrics.online.OnlineLyricsRepository
import com.clibeats.domain.repository.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks up the playing song's lyrics in the background, so opening lyrics shows them straight away.
 * Only the lyrics: translations and pronunciations are fetched when they're switched on. A song skipped within
 * a moment isn't looked up.
 */
@Singleton
class LyricsPrefetcher
    @Inject
    constructor(
        private val playbackRepository: PlaybackRepository,
        private val onlineLyrics: OnlineLyricsRepository,
        private val localLyrics: LocalLyricsRepository,
    ) {
        private val started = AtomicBoolean(false)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Starts following the playing song (once; later calls do nothing). */
        fun start() {
            if (!started.compareAndSet(false, true)) return
            scope.launch {
                playbackRepository.playbackState
                    .map { it.currentTrack }
                    .distinctUntilChanged { a, b -> a?.id == b?.id }
                    .collectLatest { track ->
                        track ?: return@collectLatest
                        delay(SETTLE_MS)
                        runCatching {
                            if (track.providerId == LOCAL_PROVIDER) {
                                localLyrics.lyricsFor(track)
                            } else {
                                onlineLyrics.lyricsFor(track)
                            }
                        }
                    }
            }
        }

        private companion object {
            const val SETTLE_MS = 1_500L
            const val LOCAL_PROVIDER = "local"
        }
    }
