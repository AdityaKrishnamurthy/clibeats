@file:Suppress("ForbiddenImport")

package com.clibeats.data.home

import com.clibeats.domain.repository.HistoryRepository
import com.clibeats.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Local Home, built from the scanned library and the play history (see [buildLocalHome]). */
@Singleton
class LocalHomeRepository
    @Inject
    constructor(
        private val songRepository: SongRepository,
        private val historyRepository: HistoryRepository,
    ) {
        /**
         * The page as of now. The same [reshuffle] on the same day gives the same page; Refresh passes a new
         * one for a fresh shuffle.
         */
        suspend fun build(reshuffle: Int = 0): LocalHome =
            withContext(Dispatchers.Default) {
                val songs = songRepository.getLocalTracksAsFlow().first().map { it.withFileUri() }
                val ids = songs.mapTo(HashSet()) { it.id }
                val recentlyAdded =
                    songRepository.getRecentlyAddedTracksAsFlow().first()
                        .filter { it.id in ids }
                        .map { it.withFileUri() }
                val plays =
                    historyRepository.getRecentHistoryAsFlow(HISTORY_LIMIT).first()
                        .map { Play(it.songId, it.playedAt) }
                val zone = ZoneId.systemDefault()
                val day = LocalDate.now(zone).toEpochDay()
                val seed = day * SEED_STEP + reshuffle
                buildLocalHome(songs, recentlyAdded, plays, System.currentTimeMillis(), zone, seed)
            }

        /** Emits the number of local songs whenever a scan changes it. */
        fun libraryChanges(): Flow<Int> = songRepository.getLocalTrackCountAsFlow().distinctUntilChanged()

        /** Local rows store a bare path; the player wants a file:// URI (content:// ones pass through). */
        private fun com.clibeats.domain.model.Track.withFileUri() =
            if (streamUrl != null && streamUrl.startsWith("/")) copy(streamUrl = "file://$streamUrl") else this

        private companion object {
            /** Enough plays for a few years of listening; older ones barely change the page. */
            const val HISTORY_LIMIT = 20_000
            const val SEED_STEP = 1_000L
        }
    }
