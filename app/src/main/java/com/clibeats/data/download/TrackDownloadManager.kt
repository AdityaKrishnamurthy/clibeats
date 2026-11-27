// ForbiddenImport: data-layer self-imports are legitimate
@file:Suppress("ForbiddenImport", "TooGenericExceptionCaught", "LongParameterList", "TooManyFunctions")
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.clibeats.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.clibeats.data.cache.MediaCaches
import com.clibeats.data.local.dao.DownloadedSongItem
import com.clibeats.data.local.dao.DownloadedTrackDao
import com.clibeats.data.local.dao.SongDao
import com.clibeats.data.local.entity.DownloadedTrackEntity
import com.clibeats.domain.model.Track
import com.clibeats.playback.StreamResolver
import com.clibeats.util.DiagnosticLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class TrackDownloadManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val store: DownloadStore,
        private val fetcher: DownloadStreamFetcher,
        /** Saves a downloaded song's lyrics too, so they show offline. */
        private val onlineLyrics: dagger.Lazy<com.clibeats.data.lyrics.online.OnlineLyricsRepository>? = null,
    ) {
        constructor(
            context: Context,
            okHttpClient: OkHttpClient,
            streamResolver: dagger.Lazy<StreamResolver>,
            downloadedTrackDao: DownloadedTrackDao,
            songDao: SongDao,
            mediaCaches: dagger.Lazy<MediaCaches>? = null,
        ) : this(
            context = context,
            store = DownloadStore(context, downloadedTrackDao, songDao),
            fetcher = DownloadStreamFetcher(okHttpClient, streamResolver, mediaCaches),
        )

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val _downloads = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
        val downloads: StateFlow<Map<String, DownloadStatus>> = _downloads.asStateFlow()

        private val _activeDownload = MutableStateFlow<ActiveDownload?>(null)
        val activeDownload: StateFlow<ActiveDownload?> = _activeDownload.asStateFlow()

        private val trackTitles = ConcurrentHashMap<String, String>()
        private val activeJobs = ConcurrentHashMap<String, Job>()
        private val pending = java.util.Collections.synchronizedMap(LinkedHashMap<String, Track>())
        private val _pendingTracks = MutableStateFlow<List<Track>>(emptyList())
        val pendingTracks: StateFlow<List<Track>> = _pendingTracks.asStateFlow()

        private val downloadSlots = Semaphore(PARALLEL_DOWNLOADS)

        val downloadedTrackIds: StateFlow<Set<String>> =
            store.getDownloadedTracksFlow()
                .map { list -> list.map { it.songId }.toSet() }
                .stateIn(scope, SharingStarted.Eagerly, emptySet())

        init {
            scope.launch {
                delay(LEFTOVER_CLEANUP_DELAY_MS)
                store.removeLeftovers()
                // Downloaded songs without saved lyrics (older downloads) get them now, while online.
                onlineLyrics?.let { lyrics ->
                    runCatching { lyrics.get().backfill(store.getDownloadedSongs().first()) }
                }
            }
        }

        private fun publishPending() {
            synchronized(pending) { _pendingTracks.value = pending.values.toList() }
        }

        fun downloadAll(tracks: List<Track>) {
            val have = downloadedTrackIds.value
            tracks.filter { it.id !in have && it.providerId != LOCAL_PROVIDER_ID }.forEach(::downloadTrack)
        }

        suspend fun isTrackDownloaded(songId: String): Boolean = store.isTrackDownloaded(songId)

        fun getDownloadedTracks(): Flow<List<DownloadedTrackEntity>> = store.getDownloadedTracksFlow()

        fun getDownloadedSongs(): Flow<List<Track>> = store.getDownloadedSongs()

        fun getDownloadedSongItems(): Flow<List<DownloadedSongItem>> = store.getDownloadedSongItems()

        fun getTotalDownloadedSizeBytes(): Flow<Long> = store.getTotalDownloadedSizeBytes()

        fun downloadTrack(track: Track) {
            if (activeJobs[track.id]?.isActive == true) return
            trackTitles[track.id] = track.title
            activeJobs.remove(track.id)?.cancel()
            pending[track.id] = track
            publishPending()
            updateStatus(track.id, DownloadStatus.Queued)

            val enqueuedWork = enqueueWorkManager(track)
            if (!enqueuedWork) {
                val job =
                    scope.launch {
                        executeWork(track)
                    }
                activeJobs[track.id] = job
            }
        }

        /** Executes a single queued download (called by [TrackDownloadWorker] or in-process fallback). */
        suspend fun executeWork(track: Track): Boolean {
            trackTitles.putIfAbsent(track.id, track.title)
            val hash = store.hashFor(track.id)
            val partFile = store.partFile(hash)
            val currentJob = coroutineContext[Job]
            if (currentJob != null) activeJobs.putIfAbsent(track.id, currentJob)
            return try {
                downloadSlots.withPermit {
                    runDownload(track, hash, partFile)
                }
            } finally {
                if (pending.remove(track.id) != null) publishPending()
            }
        }

        private fun enqueueWorkManager(track: Track): Boolean =
            runCatching {
                val request =
                    OneTimeWorkRequestBuilder<TrackDownloadWorker>()
                        .setInputData(TrackDownloadWorker.toInputData(track))
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        )
                        .addTag(WORK_TAG)
                        .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    TrackDownloadWorker.uniqueWorkName(track.id),
                    ExistingWorkPolicy.KEEP,
                    request,
                )
                true
            }.getOrDefault(false)

        @Suppress("ReturnCount")
        private suspend fun runDownload(
            track: Track,
            hash: String,
            partFile: File,
        ): Boolean {
            updateStatus(track.id, DownloadStatus.Downloading(0))
            val chunksFile = store.chunksFile(hash)
            try {
                store.existingDownload(track.id)?.let { existing ->
                    updateStatus(track.id, DownloadStatus.Completed(existing))
                    onlineLyrics?.let { lyrics -> scope.launch { lyrics.get().prefetch(track) } }
                    return true
                }

                val cached =
                    runCatching {
                        fetcher.copyFromSongCache(track, partFile) { pct ->
                            updateStatus(track.id, DownloadStatus.Downloading(pct))
                        }
                    }.getOrNull()
                if (cached != null) {
                    val file = store.saveCompleted(track, hash, partFile, cached)
                    updateStatus(track.id, DownloadStatus.Completed(file))
                    // The lyrics come along (looked up now, while online), so the song shows them offline too.
                    onlineLyrics?.let { lyrics -> scope.launch { lyrics.get().prefetch(track) } }
                    return true
                }

                val streamUrl =
                    fetcher.resolveStreamUrl(track, forceRefresh = false) ?: run {
                        updateStatus(track.id, DownloadStatus.Failed("No stream URL available"))
                        return false
                    }
                return downloadWithRetries(track, hash, partFile, chunksFile, streamUrl)
            } catch (e: CancellationException) {
                if (activeJobs[track.id] == null) {
                    store.deletePartialFiles(hash)
                    updateStatus(track.id, DownloadStatus.Idle)
                }
                throw e
            } catch (e: Exception) {
                store.deletePartialFiles(hash)
                if (activeJobs[track.id] == coroutineContext[Job]) {
                    updateStatus(track.id, DownloadStatus.Failed(e.message ?: "Download failed"))
                }
                return false
            } finally {
                activeJobs.remove(track.id, coroutineContext[Job])
            }
        }

        private suspend fun downloadWithRetries(
            track: Track,
            hash: String,
            partFile: File,
            chunksFile: File,
            initialUrl: String,
        ): Boolean {
            var streamUrl: String? = initialUrl
            var lastError = "Download failed"
            var succeeded = false
            var attempt = 1
            while (attempt <= MAX_ATTEMPTS && streamUrl != null && !succeeded) {
                val url = streamUrl
                val format =
                    runCatching {
                        fetcher.fetch(url, partFile, chunksFile) { pct ->
                            updateStatus(track.id, DownloadStatus.Downloading(pct))
                        }
                    }.getOrElse { error ->
                        if (error is CancellationException) throw error
                        lastError = error.message?.takeIf { it.isNotBlank() } ?: lastError
                        store.deletePartialFiles(hash)
                        streamUrl =
                            if (attempt < MAX_ATTEMPTS) {
                                fetcher.invalidateStream(track)
                                fetcher.resolveStreamUrl(track, forceRefresh = true)
                            } else {
                                null
                            }
                        null
                    }
                if (format != null) {
                    val file = store.saveCompleted(track, hash, partFile, format)
                    updateStatus(track.id, DownloadStatus.Completed(file))
                    // The lyrics come along (looked up now, while online), so the song shows them offline too.
                    onlineLyrics?.let { lyrics -> scope.launch { lyrics.get().prefetch(track) } }
                    succeeded = true
                }
                attempt++
            }
            if (!succeeded) {
                val msg = "${track.id}: $lastError"
                DiagnosticLogger.logError("TrackDownloadManager", "DOWNLOAD_FAILED", msg)
                updateStatus(track.id, DownloadStatus.Failed(lastError))
            }
            return succeeded
        }

        fun cancelDownload(trackId: String) {
            runCatching {
                WorkManager.getInstance(context).cancelUniqueWork(TrackDownloadWorker.uniqueWorkName(trackId))
            }
            activeJobs.remove(trackId)?.cancel()
            store.deletePartialFiles(store.hashFor(trackId))
            if (pending.remove(trackId) != null) publishPending()
            updateStatus(trackId, DownloadStatus.Idle)
        }

        suspend fun deleteDownload(trackId: String) {
            withContext(Dispatchers.IO + NonCancellable) {
                cancelDownload(trackId)
                store.deleteTrackRecordAndFiles(trackId)
                trackTitles.remove(trackId)
                _downloads.update { it - trackId }
                if (_activeDownload.value?.trackId == trackId) _activeDownload.value = null
            }
        }

        suspend fun deleteAllDownloads() {
            withContext(Dispatchers.IO + NonCancellable) {
                runCatching { WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG) }
                activeJobs.keys.toList().forEach(::cancelDownload)
                pending.clear()
                publishPending()
                store.deleteAllRecordsAndFiles()
                trackTitles.clear()
                _downloads.value = emptyMap()
                _activeDownload.value = null
            }
        }

        private fun updateStatus(
            songId: String,
            status: DownloadStatus,
        ) {
            val title = trackTitles[songId]
            when {
                status is DownloadStatus.Idle ->
                    if (_activeDownload.value?.trackId == songId) _activeDownload.value = null
                status is DownloadStatus.Queued -> Unit
                title != null -> {
                    val waiting = synchronized(pending) { pending.keys.count { it != songId } }
                    _activeDownload.value = ActiveDownload(songId, title, status, waiting)
                }
            }
            _downloads.update { it + (songId to status) }
        }

        companion object {
            const val PARALLEL_DOWNLOADS = 2
            private const val MAX_ATTEMPTS = 2
            private const val LOCAL_PROVIDER_ID = "local"
            private const val LEFTOVER_CLEANUP_DELAY_MS = 30_000L
            private const val WORK_TAG = "clibeats_track_download"
        }
    }

data class ActiveDownload(
    val trackId: String,
    val title: String,
    val status: DownloadStatus,
    val waiting: Int = 0,
)
