@file:Suppress("ForbiddenImport", "MaxLineLength", "LongMethod", "TooManyFunctions", "LongParameterList")

package com.clibeats.data.repository

import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest
import com.clibeats.data.local.dao.QueueDao
import com.clibeats.data.local.dao.SongDao
import com.clibeats.data.local.entity.QueueEntity
import com.clibeats.data.local.mapper.toDomain
import com.clibeats.data.local.mapper.toEntity
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.domain.model.PlaybackState
import com.clibeats.domain.model.RepeatMode
import com.clibeats.domain.model.Track
import com.clibeats.domain.repository.HistoryRepository
import com.clibeats.domain.repository.PlaybackRepository
import com.clibeats.playback.PlayerAdapter
import com.clibeats.playback.StreamResolver
import com.clibeats.presentation.component.ARTWORK_FULL_PX
import com.clibeats.presentation.component.artworkUrlFor
import com.clibeats.util.DiagnosticLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackRepositoryImpl
    constructor(
        private val playerAdapter: PlayerAdapter,
        private val streamResolver: StreamResolver,
        private val queueDao: QueueDao,
        private val songDao: SongDao,
        private val appPreferences: AppPreferences,
        private val ioDispatcher: CoroutineDispatcher,
        private val historyRepository: HistoryRepository? = null,
        private val imageLoader: dagger.Lazy<ImageLoader>? = null,
        private val appContext: Context? = null,
    ) : PlaybackRepository {
        @Inject
        constructor(
            playerAdapter: PlayerAdapter,
            streamResolver: StreamResolver,
            queueDao: QueueDao,
            songDao: SongDao,
            appPreferences: AppPreferences,
            historyRepository: HistoryRepository,
            imageLoader: dagger.Lazy<ImageLoader>,
            @ApplicationContext appContext: Context,
        ) : this(
            playerAdapter,
            streamResolver,
            queueDao,
            songDao,
            appPreferences,
            Dispatchers.IO,
            historyRepository,
            imageLoader,
            appContext,
        )

        private val repositoryScope = CoroutineScope(SupervisorJob() + ioDispatcher)

        override val playbackState: StateFlow<PlaybackState> = playerAdapter.playbackState

        override val queueState: StateFlow<List<Track>> = playerAdapter.queueFlow

        override val playerMessages: Flow<String> = playerAdapter.messages

        private val restored = CompletableDeferred<Unit>()

        override suspend fun awaitRestored() = restored.await()

        init {
            repositoryScope.launch {
                restorePersistentQueue()
                restored.complete(Unit)
            }
            repositoryScope.launch {
                playerAdapter.queueFlow.collect { tracks ->
                    persistQueueItems(tracks)
                }
            }
            repositoryScope.launch {
                // On each new track: record history and warm stream URLs for the next tracks
                // so skip / auto-advance starts without waiting on extraction.
                playerAdapter.playbackState
                    .map { it.currentTrack }
                    .distinctUntilChanged { a, b -> a?.id == b?.id }
                    .collectLatest { current ->
                        if (current == null) return@collectLatest
                        runCatching { historyRepository?.recordPlay(current.id, current.providerId) }
                        prefetchAfter(current)
                    }
            }
            repositoryScope.launch {
                // Persist only on meaningful changes (track/mode/play-pause), not every 500ms tick.
                playerAdapter.playbackState
                    .distinctUntilChanged { old, new ->
                        old.currentTrack?.id == new.currentTrack?.id &&
                            old.isPlaying == new.isPlaying &&
                            old.repeatMode == new.repeatMode &&
                            old.shuffleEnabled == new.shuffleEnabled
                    }.collect { state ->
                        val tracks = playerAdapter.queueFlow.value
                        val currentIndex =
                            state.currentTrack?.let { current ->
                                tracks.indexOfFirst { it.id == current.id }
                            } ?: 0
                        appPreferences.saveQueueMetadata(
                            index = if (currentIndex >= 0) currentIndex else 0,
                            positionMs = state.positionMs,
                            repeatMode = state.repeatMode.name,
                            shuffleEnabled = state.shuffleEnabled,
                        )
                    }
            }
        }

        private suspend fun restorePersistentQueue() {
            runCatching {
                val songEntities = queueDao.getQueueSongs()
                if (songEntities.isNotEmpty()) {
                    val tracks = songEntities.map { it.toDomain() }
                    val index = appPreferences.lastQueueIndex.first()
                    val positionMs = appPreferences.lastPlaybackPosition.first()
                    val repeatModeStr = appPreferences.savedRepeatMode.first()
                    val shuffleEnabled = appPreferences.savedShuffleEnabled.first()
                    val repeatMode =
                        runCatching { RepeatMode.valueOf(repeatModeStr) }.getOrDefault(RepeatMode.OFF)

                    withContext(Dispatchers.Main) {
                        playerAdapter.restoreQueue(
                            tracks = tracks,
                            startIndex = index,
                            positionMs = positionMs,
                            repeatMode = repeatMode,
                            shuffleEnabled = shuffleEnabled,
                        )
                    }
                }
            }.onFailure { e ->
                DiagnosticLogger.logError("PlaybackRepo", "QUEUE_RESTORE_FAILED", e.message ?: "Restore error")
            }
        }

        private suspend fun persistQueueItems(tracks: List<Track>) {
            runCatching {
                if (tracks.isEmpty()) {
                    queueDao.clearQueue()
                } else {
                    songDao.upsertAll(tracks.map { it.toEntity() })
                    queueDao.replaceQueue(
                        tracks.mapIndexed { index, track ->
                            QueueEntity(position = index, songId = track.id)
                        },
                    )
                }
            }.onFailure { e ->
                DiagnosticLogger.logError("PlaybackRepo", "QUEUE_PERSIST_FAILED", e.message ?: "Persist error")
            }
        }

        override fun playTrack(track: Track) {
            val traceId = DiagnosticLogger.generateTraceId()
            DiagnosticLogger.logTrackSelected(traceId, track.id, track.title)
            // Streams resolve lazily inside ExoPlayer (TrackUriResolver); hand off immediately.
            repositoryScope.launch(Dispatchers.Main) { playerAdapter.playTrack(track) }
        }

        override fun addToQueue(track: Track) {
            val traceId = DiagnosticLogger.generateTraceId()
            DiagnosticLogger.logTrackSelected(traceId, track.id, track.title)
            // Streams resolve lazily inside ExoPlayer (TrackUriResolver); hand off immediately.
            repositoryScope.launch(Dispatchers.Main) { playerAdapter.addToQueue(track) }
        }

        override fun playNext(track: Track) {
            val traceId = DiagnosticLogger.generateTraceId()
            DiagnosticLogger.logTrackSelected(traceId, track.id, track.title)
            // Streams resolve lazily inside ExoPlayer (TrackUriResolver); hand off immediately.
            repositoryScope.launch(Dispatchers.Main) { playerAdapter.playNext(track) }
        }

        override fun setQueue(
            tracks: List<Track>,
            startIndex: Int,
        ) {
            if (tracks.isEmpty()) return
            val traceId = DiagnosticLogger.generateTraceId()

            repositoryScope.launch(Dispatchers.Main) {
                val targetIndex = startIndex.coerceIn(tracks.indices)
                DiagnosticLogger.logTrackSelected(traceId, tracks[targetIndex].id, tracks[targetIndex].title)
                playerAdapter.setQueue(tracks, targetIndex)
            }
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

        /** Warms the disk cache with the next tracks' high-res covers so Now Playing shows them instantly. */
        private fun prefetchArtwork(track: Track) {
            val context = appContext ?: return
            val url = artworkUrlFor(track.artworkUrl, ARTWORK_FULL_PX) ?: return
            runCatching { imageLoader?.get()?.enqueue(ImageRequest.Builder(context).data(url).build()) }
        }

        override fun moveTrackInQueue(
            fromIndex: Int,
            toIndex: Int,
        ) = playerAdapter.moveTrack(fromIndex, toIndex)

        override fun removeFromQueue(index: Int) = playerAdapter.removeFromQueue(index)

        override fun clearQueue() = playerAdapter.clearQueue()

        override fun play() = playerAdapter.play()

        override fun pause() = playerAdapter.pause()

        override fun seekTo(positionMs: Long) = playerAdapter.seekTo(positionMs)

        override fun skipToIndex(index: Int) = playerAdapter.skipToIndex(index)

        override fun skipToNext() = playerAdapter.skipToNext()

        override fun skipToPrevious() = playerAdapter.skipToPrevious()

        override fun setRepeatMode(mode: RepeatMode) = playerAdapter.setRepeatMode(mode)

        override fun toggleShuffle() = playerAdapter.toggleShuffle()

        private companion object {
            const val PREFETCH_COUNT = 2
        }
    }
