// ForbiddenImport: data-layer self-imports are legitimate
@file:Suppress("ForbiddenImport")

package com.clibeats.data.repository

import com.clibeats.data.network.NetworkMonitor
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.domain.model.RepeatMode
import com.clibeats.domain.model.Track
import com.clibeats.domain.provider.ProviderRegistry
import com.clibeats.domain.provider.ProviderResult
import com.clibeats.domain.radio.RadioLanguage
import com.clibeats.domain.repository.HistoryRepository
import com.clibeats.playback.PlayerAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives queue autoplay / continuous radio batching extracted from [PlaybackRepositoryImpl].
 */
@Singleton
class RadioAutoplayCoordinator
    @Inject
    constructor(
        private val appPreferences: AppPreferences,
        private val historyRepository: HistoryRepository,
        private val providerRegistry: ProviderRegistry,
        private val networkMonitor: NetworkMonitor,
    ) {
        private class RadioSession(
            val generation: Int,
            val seedId: String,
            val language: String?,
            val pending: ArrayDeque<Track>,
            val known: MutableSet<String>,
        )

        private var radio: RadioSession? = null
        private val radioMutex = Mutex()

        @Suppress("ReturnCount")
        suspend fun checkAutoplay(
            current: Track,
            playerAdapter: PlayerAdapter,
        ) {
            val enabled = runCatching { appPreferences.autoplayEnabled.first() }.getOrDefault(false)
            if (!enabled) return
            if (playerAdapter.playbackState.value.repeatMode != RepeatMode.OFF) return
            if (!networkMonitor.isOnline.value) networkMonitor.isOnline.first { it }
            val queue = playerAdapter.queueFlow.value
            val currentIndex = queue.indexOfFirst { it.id == current.id }
            if (currentIndex < 0 || currentIndex < queue.lastIndex - 1) return
            radioMutex.withLock { addRadioBatch(current, playerAdapter) }
        }

        suspend fun refreshRadio(playerAdapter: PlayerAdapter) {
            val current = playerAdapter.playbackState.value.currentTrack ?: return
            withContext(Dispatchers.Main) { playerAdapter.removeUpcomingRadio() }
            radioMutex.withLock {
                radio = null
                addRadioBatch(current, playerAdapter)
            }
        }

        @Suppress("CyclomaticComplexMethod")
        private suspend fun addRadioBatch(
            current: Track,
            playerAdapter: PlayerAdapter,
        ) {
            val generation = playerAdapter.queueGeneration
            val existing =
                radio?.takeIf { it.generation == generation && (current.id == it.seedId || current.id in it.known) }
            var session: RadioSession =
                existing ?: startRadio(current, generation, inheritedLanguage = null) ?: return

            val recentHistory =
                runCatching {
                    historyRepository.getRecentlyPlayedTracks(AUTOPLAY_HISTORY_DEDUP_LIMIT)
                        .first()
                        .map { it.id }
                        .toSet()
                }.getOrNull().orEmpty()
            val skip = playerAdapter.queueFlow.value.map { it.id }.toMutableSet().apply { addAll(recentHistory) }
            val batch = takeBatch(session, skip)
            if (batch.size < RADIO_BATCH && session.pending.isEmpty()) {
                val queue = playerAdapter.queueFlow.value
                val seed =
                    queue.lastOrNull { it.id in session.known && RadioLanguage.languageOf(it) == session.language }
                        ?: queue.lastOrNull()
                        ?: current
                val next = startRadio(seed, generation, inheritedLanguage = session.language ?: NO_LANGUAGE)
                if (next != null) {
                    session = next
                    skip.addAll(batch.map { it.id })
                    batch += takeBatch(next, skip)
                }
            }
            radio = session
            if (batch.isEmpty()) return
            withContext(Dispatchers.Main) {
                if (playerAdapter.playbackState.value.currentTrack?.id == current.id &&
                    playerAdapter.queueGeneration == generation
                ) {
                    playerAdapter.addAutoplayTracks(batch)
                }
            }
        }

        @Suppress("ReturnCount")
        private suspend fun startRadio(
            seed: Track,
            generation: Int,
            inheritedLanguage: String?,
        ): RadioSession? {
            val provider: com.clibeats.domain.provider.MusicProvider? =
                providerRegistry.getProvider(seed.providerId) ?: providerRegistry.defaultProvider()
            if (provider == null) return null
            val result = provider.relatedTracks(seed.id, RADIO_FETCH_LIMIT)
            val related = (result as? ProviderResult.Success)?.data.orEmpty()
            if (related.isEmpty()) return null
            val language =
                when (inheritedLanguage) {
                    null -> RadioLanguage.forRadio(seed, related)
                    NO_LANGUAGE -> null
                    else -> inheritedLanguage
                }
            val ordered = RadioLanguage.preferSameLanguage(language, related)
            return RadioSession(
                generation = generation,
                seedId = seed.id,
                language = language,
                pending = ArrayDeque(ordered),
                known = ordered.mapTo(mutableSetOf()) { it.id }.apply { add(seed.id) },
            )
        }

        private fun takeBatch(
            session: RadioSession,
            skip: Set<String>,
        ): MutableList<Track> {
            val batch = mutableListOf<Track>()
            while (batch.size < RADIO_BATCH && session.pending.isNotEmpty()) {
                val next = session.pending.removeFirst()
                if (next.id !in skip && batch.none { it.id == next.id }) batch += next
            }
            return batch
        }

        companion object {
            private const val AUTOPLAY_HISTORY_DEDUP_LIMIT = 25
            private const val RADIO_BATCH = 15
            private const val RADIO_FETCH_LIMIT = 80
            private const val NO_LANGUAGE = ""
        }
    }
