// ForbiddenImport: data-layer self-imports are legitimate
@file:Suppress("ForbiddenImport")

package com.clibeats.data.repository

import com.clibeats.data.local.dao.QueueDao
import com.clibeats.data.local.dao.SongDao
import com.clibeats.data.local.entity.QueueEntity
import com.clibeats.data.local.mapper.toDomain
import com.clibeats.data.local.mapper.toEntity
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.domain.model.RepeatMode
import com.clibeats.domain.model.Track
import com.clibeats.playback.PlayerAdapter
import com.clibeats.util.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists queue ordering, radio-item flags, and playback cursor across process restarts.
 */
@Singleton
class QueuePersistenceCoordinator
    @Inject
    constructor(
        private val queueDao: QueueDao,
        private val songDao: SongDao,
        private val appPreferences: AppPreferences,
    ) {
        suspend fun restore(playerAdapter: PlayerAdapter) {
            runCatching {
                val songEntities = queueDao.getQueueSongs()
                if (songEntities.isNotEmpty()) {
                    val queueItems = runCatching { queueDao.getQueueItems() }.getOrNull().orEmpty()
                    val radioIds = queueItems.filter { it.isRadio }.map { it.songId }.toSet()
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
                            radioIds = radioIds,
                        )
                    }
                }
            }.onFailure { e ->
                DiagnosticLogger.logError("PlaybackRepo", "QUEUE_RESTORE_FAILED", e.message ?: "Restore error")
            }
        }

        suspend fun persistQueueItems(
            tracks: List<Track>,
            radioIds: Set<String>,
        ) {
            runCatching {
                if (tracks.isEmpty()) {
                    queueDao.clearQueue()
                } else {
                    // Local songs belong to the scanner: insert missing rows without overwriting scanned metadata.
                    val (local, online) = tracks.partition { it.providerId == LOCAL_PROVIDER }
                    if (local.isNotEmpty()) songDao.insertAllIfAbsent(local.map { it.toEntity() })
                    if (online.isNotEmpty()) songDao.upsertAll(online.map { it.toEntity() })
                    queueDao.replaceQueue(
                        tracks.mapIndexed { index, track ->
                            QueueEntity(
                                position = index,
                                songId = track.id,
                                isRadio = track.id in radioIds,
                            )
                        },
                    )
                }
            }.onFailure { e ->
                DiagnosticLogger.logError("PlaybackRepo", "QUEUE_PERSIST_FAILED", e.message ?: "Persist error")
            }
        }

        suspend fun persistPlaybackPosition(playerAdapter: PlayerAdapter) {
            playerAdapter.playbackState
                .distinctUntilChanged { old, new ->
                    old.currentTrack?.id == new.currentTrack?.id &&
                        old.isPlaying == new.isPlaying &&
                        old.repeatMode == new.repeatMode &&
                        old.shuffleEnabled == new.shuffleEnabled &&
                        old.positionMs / POSITION_SAVE_STEP_MS == new.positionMs / POSITION_SAVE_STEP_MS
                }.collect { state ->
                    val current = state.currentTrack ?: return@collect
                    val index = playerAdapter.queueFlow.value.indexOfFirst { it.id == current.id }
                    if (index < 0) return@collect
                    appPreferences.saveQueueMetadata(
                        index = index,
                        positionMs = state.positionMs,
                        repeatMode = state.repeatMode.name,
                        shuffleEnabled = state.shuffleEnabled,
                    )
                }
        }

        companion object {
            private const val POSITION_SAVE_STEP_MS = 5_000L
            private const val LOCAL_PROVIDER = "local"
        }
    }
