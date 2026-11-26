// ForbiddenImport: data-layer self-imports are legitimate
@file:Suppress("ForbiddenImport", "TooManyFunctions", "LongParameterList")

package com.clibeats.data.repository

import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest
import com.clibeats.data.local.dao.SongDao
import com.clibeats.data.local.mapper.toDomain
import com.clibeats.di.IoDispatcher
import com.clibeats.domain.model.PlaybackState
import com.clibeats.domain.model.RepeatMode
import com.clibeats.domain.model.Track
import com.clibeats.domain.repository.HistoryRepository
import com.clibeats.domain.repository.PlaybackRepository
import com.clibeats.playback.PlayerAdapter
import com.clibeats.playback.StreamResolver
import com.clibeats.util.ARTWORK_FULL_PX
import com.clibeats.util.DiagnosticLogger
import com.clibeats.util.artworkUrlFor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackRepositoryImpl
    @Inject
    constructor(
        private val playerAdapter: PlayerAdapter,
        private val streamResolver: StreamResolver,
        private val queuePersistence: QueuePersistenceCoordinator,
        private val radioAutoplay: RadioAutoplayCoordinator,
        private val songDao: SongDao,
        private val historyRepository: HistoryRepository,
        private val imageLoader: dagger.Lazy<ImageLoader>,
        @ApplicationContext private val appContext: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : PlaybackRepository {
        private val repositoryScope = CoroutineScope(SupervisorJob() + ioDispatcher)

        private val playedHistory = ArrayDeque<Track>()
        private val forward = ArrayDeque<Track>()

        @Volatile private var goingBack = false

        override val playbackState: StateFlow<PlaybackState> = playerAdapter.playbackState
        override val queueState: StateFlow<List<Track>> = playerAdapter.queueFlow
        override val playerMessages: Flow<String> = playerAdapter.messages
        override val pauseAtEndOfTrack: StateFlow<Boolean> = playerAdapter.pauseAtEndOfTrack
        override val sleepTimerCountdown: StateFlow<Int?> = playerAdapter.sleepTimerCountdown
        override val radioTrackIds: StateFlow<Set<String>> get() = playerAdapter.radioTrackIds

        private val restored = CompletableDeferred<Unit>()

        init {
            repositoryScope.launch {
                queuePersistence.restore(playerAdapter)
                restored.complete(Unit)
                launch(start = CoroutineStart.UNDISPATCHED) {
                    playerAdapter.queueFlow.drop(1).collect { tracks ->
                        queuePersistence.persistQueueItems(tracks, playerAdapter.radioTrackIds.value)
                    }
                }
                launch(start = CoroutineStart.UNDISPATCHED) {
                    queuePersistence.persistPlaybackPosition(playerAdapter)
                }
            }
            repositoryScope.launch {
                playerAdapter.playbackState
                    .map { it.currentTrack }
                    .distinctUntilChanged { a, b -> a?.id == b?.id }
                    .collectLatest { current ->
                        if (current == null) return@collectLatest
                        rememberPlayed(current)
                        runCatching { historyRepository.recordPlay(current.id, current.providerId) }
                        prefetchAfter(current)
                        radioAutoplay.checkAutoplay(current, playerAdapter)
                    }
            }
        }

        override suspend fun awaitRestored() = restored.await()

        override fun setPauseAtEndOfTrack(enabled: Boolean) = playerAdapter.setPauseAtEndOfTrack(enabled)

        override fun startSleepTimer(minutes: Int) = playerAdapter.startSleepTimer(minutes)

        override fun cancelSleepTimer() = playerAdapter.cancelSleepTimer()

        override fun playTrack(track: Track) {
            val traceId = DiagnosticLogger.generateTraceId()
            DiagnosticLogger.logTrackSelected(traceId, track.id, track.title)
            repositoryScope.launch(Dispatchers.Main) { playerAdapter.playTrack(track) }
        }

        override fun addToQueue(track: Track) {
            val traceId = DiagnosticLogger.generateTraceId()
            DiagnosticLogger.logTrackSelected(traceId, track.id, track.title)
            repositoryScope.launch(Dispatchers.Main) { playerAdapter.addToQueue(track) }
        }

        override fun playNext(track: Track) {
            val traceId = DiagnosticLogger.generateTraceId()
            DiagnosticLogger.logTrackSelected(traceId, track.id, track.title)
            repositoryScope.launch(Dispatchers.Main) { playerAdapter.playNext(track) }
        }

        override fun setQueue(
            tracks: List<Track>,
            startIndex: Int,
        ) = startQueue(tracks, startIndex, source = null)

        override fun playFrom(
            tracks: List<Track>,
            startIndex: Int,
            source: String,
        ) = startQueue(tracks, startIndex, source)

        private fun startQueue(
            tracks: List<Track>,
            startIndex: Int,
            source: String?,
        ) {
            if (tracks.isEmpty()) return
            val traceId = DiagnosticLogger.generateTraceId()
            repositoryScope.launch(Dispatchers.Main) {
                val targetIndex = startIndex.coerceIn(tracks.indices)
                DiagnosticLogger.logTrackSelected(traceId, tracks[targetIndex].id, tracks[targetIndex].title)
                playerAdapter.setQueue(tracks, targetIndex, source)
            }
        }

        override fun moveTrackInQueue(
            fromIndex: Int,
            toIndex: Int,
        ) = playerAdapter.moveTrack(fromIndex, toIndex)

        override fun removeFromQueue(index: Int) = playerAdapter.removeFromQueue(index)

        override fun insertIntoQueue(
            index: Int,
            track: Track,
        ) = playerAdapter.insertAt(index, track)

        override fun clearQueue() = playerAdapter.clearQueue()

        override fun play() = playerAdapter.play()

        override fun pause() = playerAdapter.pause()

        override fun seekTo(positionMs: Long) = playerAdapter.seekTo(positionMs)

        override fun skipToIndex(index: Int) = playerAdapter.skipToIndex(index)

        override fun skipToNext() {
            val target = synchronized(playedHistory) { forward.lastOrNull() }
            when {
                target == null -> playerAdapter.skipToNext()
                playerAdapter.nextTrack()?.id == target.id -> playerAdapter.skipToNext()
                else -> playerAdapter.playForward(target)
            }
        }

        override fun skipToPrevious() {
            if (playerAdapter.currentPositionMs() > RESTART_THRESHOLD_MS) {
                playerAdapter.seekTo(0L)
                return
            }
            val previous =
                synchronized(playedHistory) {
                    if (playedHistory.size < 2) {
                        null
                    } else {
                        playedHistory.removeLast()
                        playedHistory.last()
                    }
                }
            if (previous == null) {
                playerAdapter.skipToPrevious()
                return
            }
            goingBack = true
            playerAdapter.playbackState.value.currentTrack?.let { leaving ->
                synchronized(playedHistory) { forward.addLast(leaving) }
            }
            if (playerAdapter.previousTrack()?.id == previous.id) {
                playerAdapter.skipToPrevious()
            } else {
                playerAdapter.playBeforeCurrent(previous)
            }
        }

        override suspend fun refreshLocalTracks() {
            val ids = playerAdapter.queueFlow.value.filter { it.providerId == LOCAL_PROVIDER }.map { it.id }.distinct()
            if (ids.isEmpty()) return
            val fresh =
                runCatching { songDao.getByIds(ids) }
                    .getOrNull()
                    ?.associate { it.id to it.toDomain() }
                    ?: return
            withContext(Dispatchers.Main) { playerAdapter.refreshTracks(fresh) }
        }

        override fun setRepeatMode(mode: RepeatMode) = playerAdapter.setRepeatMode(mode)

        override fun toggleShuffle() = playerAdapter.toggleShuffle()

        override fun refreshRadio() {
            repositoryScope.launch { radioAutoplay.refreshRadio(playerAdapter) }
        }

        private suspend fun prefetchAfter(current: Track) {
            val queue = playerAdapter.queueFlow.value
            val idx = queue.indexOfFirst { it.id == current.id }
            if (idx < 0) return
            queue.drop(idx + 1).take(PREFETCH_COUNT).forEach { next ->
                prefetchArtwork(next)
                if (next.streamUrl.isNullOrBlank() || next.streamUrl.contains("expire")) {
                    runCatching { streamResolver.resolve(next) }
                }
            }
        }

        private suspend fun rememberPlayed(current: Track) {
            if (goingBack) {
                goingBack = false
                return
            }
            synchronized(playedHistory) {
                if (forward.lastOrNull()?.id == current.id) forward.removeLast() else forward.clear()
            }
            val seed =
                if (synchronized(playedHistory) { playedHistory.isEmpty() }) {
                    runCatching { historyRepository.getRecentlyPlayedTracks(HISTORY_LIMIT).first() }
                        .getOrNull()
                        .orEmpty()
                        .asReversed()
                } else {
                    emptyList()
                }
            synchronized(playedHistory) {
                if (playedHistory.isEmpty()) seed.filter { it.id != current.id }.forEach(playedHistory::addLast)
                if (playedHistory.lastOrNull()?.id != current.id) playedHistory.addLast(current)
                while (playedHistory.size > HISTORY_LIMIT) playedHistory.removeFirst()
            }
        }

        private fun prefetchArtwork(track: Track) {
            val url = artworkUrlFor(track.artworkUrl, ARTWORK_FULL_PX) ?: return
            runCatching {
                imageLoader.get().enqueue(
                    ImageRequest.Builder(appContext)
                        .data(url)
                        .size(ARTWORK_FULL_PX)
                        .build(),
                )
            }
        }

        private companion object {
            const val PREFETCH_COUNT = 2
            const val HISTORY_LIMIT = 100
            const val RESTART_THRESHOLD_MS = 3_000L
            const val LOCAL_PROVIDER = "local"
        }
    }
