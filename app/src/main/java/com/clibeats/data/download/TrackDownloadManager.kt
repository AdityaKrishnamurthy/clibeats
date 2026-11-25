// ForbiddenImport: data-layer self-imports are legitimate
@file:Suppress("ForbiddenImport", "TooGenericExceptionCaught", "LongParameterList", "TooManyFunctions")

package com.clibeats.data.download

import android.content.Context
import com.clibeats.data.local.dao.DownloadedSongItem
import com.clibeats.data.local.dao.DownloadedTrackDao
import com.clibeats.data.local.dao.SongDao
import com.clibeats.data.local.entity.DownloadedTrackEntity
import com.clibeats.data.local.mapper.toDomain
import com.clibeats.data.local.mapper.toEntity
import com.clibeats.domain.model.Track
import com.clibeats.playback.StreamResolver
import com.clibeats.util.DiagnosticLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackDownloadManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val okHttpClient: OkHttpClient,
        private val streamResolver: dagger.Lazy<StreamResolver>,
        private val downloadedTrackDao: DownloadedTrackDao,
        private val songDao: SongDao,
    ) {
        private val scope = CoroutineScope(Dispatchers.IO)
        private val _downloads = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
        val downloads: StateFlow<Map<String, DownloadStatus>> = _downloads.asStateFlow()

        private val _activeDownload = MutableStateFlow<ActiveDownload?>(null)
        val activeDownload: StateFlow<ActiveDownload?> = _activeDownload.asStateFlow()

        private val trackTitles = ConcurrentHashMap<String, String>()
        private val activeJobs = ConcurrentHashMap<String, Job>()

        private val downloadsDir: File by lazy {
            File(context.filesDir, "downloads").apply {
                if (!exists()) mkdirs()
            }
        }

        suspend fun isTrackDownloaded(songId: String): Boolean =
            withContext(Dispatchers.IO) {
                val record = downloadedTrackDao.getById(songId) ?: return@withContext false
                File(record.filePath).exists()
            }

        fun getDownloadedTracks(): Flow<List<DownloadedTrackEntity>> = downloadedTrackDao.getAllFlow()

        val downloadedTrackIds: StateFlow<Set<String>> =
            downloadedTrackDao.getAllFlow()
                .map { list -> list.map { it.songId }.toSet() }
                .stateIn(scope, SharingStarted.Eagerly, emptySet())

        fun getDownloadedSongs(): Flow<List<Track>> =
            songDao.getDownloadedSongsAsFlow().map { entities ->
                entities.map { it.toDomain() }
            }

        fun getDownloadedSongItems(): Flow<List<DownloadedSongItem>> = downloadedTrackDao.getDownloadedSongItemsFlow()

        fun getTotalDownloadedSizeBytes(): Flow<Long> =
            downloadedTrackDao.getAllFlow().map { list ->
                list.sumOf { it.sizeBytes }
            }

        @Suppress("LongMethod", "CyclomaticComplexMethod", "ThrowsCount")
        fun downloadTrack(track: Track) {
            // A second tap while this song is already downloading must not restart it (and truncate the part file).
            if (activeJobs[track.id]?.isActive == true) return

            trackTitles[track.id] = track.title
            val hash = sha256(track.id)
            val partFile = File(downloadsDir, "$hash.part")

            activeJobs.remove(track.id)?.cancel()
            // Publish the start synchronously so the UI never observes a stale Completed/Failed status.
            updateStatus(track.id, DownloadStatus.Downloading(0))

            val job =
                scope.launch {
                    try {
                        existingDownload(track.id)?.let { existing ->
                            updateStatus(track.id, DownloadStatus.Completed(existing))
                            return@launch
                        }

                        var streamUrl: String =
                            resolveStreamUrl(track, forceRefresh = false) ?: run {
                                updateStatus(track.id, DownloadStatus.Failed("No stream URL available"))
                                return@launch
                            }

                        var lastError = "Download failed"
                        for (attempt in 1..MAX_ATTEMPTS) {
                            try {
                                val format = fetch(track.id, streamUrl, partFile)
                                // Name the file after the container the provider actually served.
                                val finalFile = File(downloadsDir, "$hash.${format.extension}")
                                if (finalFile.exists()) finalFile.delete()

                                if (!partFile.renameTo(finalFile)) {
                                    partFile.delete()
                                    throw IOException("Failed to finalize download file")
                                }

                                // Keep the file and the record in step: an orphan file would never show in Downloads.
                                try {
                                    songDao.upsert(track.toEntity())
                                    downloadedTrackDao.insert(
                                        DownloadedTrackEntity(
                                            songId = track.id,
                                            filePath = finalFile.absolutePath,
                                            sizeBytes = finalFile.length(),
                                            downloadedAt = System.currentTimeMillis(),
                                            mimeType = format.mimeType,
                                        ),
                                    )
                                } catch (e: Exception) {
                                    finalFile.delete()
                                    throw e
                                }

                                updateStatus(track.id, DownloadStatus.Completed(finalFile))
                                return@launch
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                partFile.delete()
                                lastError = e.message?.takeIf { it.isNotBlank() } ?: lastError
                                if (attempt == MAX_ATTEMPTS) {
                                    DiagnosticLogger.logError(
                                        "TrackDownloadManager",
                                        "DOWNLOAD_FAILED",
                                        "${track.id}: $lastError",
                                    )
                                    updateStatus(track.id, DownloadStatus.Failed(lastError))
                                    return@launch
                                }
                                // A stale signed URL or PO token is the usual cause of a 403: invalidate and refresh.
                                streamResolver.get().invalidate(track.providerId, track.id)
                                streamUrl =
                                    resolveStreamUrl(track, forceRefresh = true) ?: run {
                                        DiagnosticLogger.logError(
                                            "TrackDownloadManager",
                                            "DOWNLOAD_FAILED",
                                            "${track.id}: $lastError",
                                        )
                                        updateStatus(track.id, DownloadStatus.Failed(lastError))
                                        return@launch
                                    }
                            }
                        }
                    } catch (e: CancellationException) {
                        // Only the job that is still current may delete the shared part file.
                        if (activeJobs[track.id] == null) {
                            if (partFile.exists()) partFile.delete()
                            updateStatus(track.id, DownloadStatus.Idle)
                        }
                        throw e
                    } catch (e: Exception) {
                        if (partFile.exists()) partFile.delete()
                        if (activeJobs[track.id] == coroutineContext[Job]) {
                            updateStatus(track.id, DownloadStatus.Failed(e.message ?: "Download failed"))
                        }
                    } finally {
                        activeJobs.remove(track.id, coroutineContext[Job])
                    }
                }

            activeJobs[track.id] = job
        }

        /** A still-valid URL from the track, else a freshly resolved one. */
        private suspend fun resolveStreamUrl(
            track: Track,
            forceRefresh: Boolean,
        ): String? {
            if (!forceRefresh) {
                track.streamUrl
                    ?.takeIf { it.isNotBlank() && !StreamResolver.isStreamExpired(it) }
                    ?.let { return it }
            }
            return runCatching { streamResolver.get().resolve(track, forceRefresh = forceRefresh).streamUrl }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }

        /** Downloads [url] into [target] and returns the container; throws so the caller retries. */
        private fun fetch(
            songId: String,
            url: String,
            target: File,
        ): DownloadFormat {
            val request = Request.Builder().url(url).build()
            // Close the response on every path: an abandoned body keeps its socket out of the pool.
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) throw IOException("HTTP ${response.code}")

                val expectedBytes = body.contentLength()
                val writtenBytes = writeBody(songId, body.byteStream(), expectedBytes, target)
                if (expectedBytes >= 0 && writtenBytes != expectedBytes) {
                    throw IOException("Download incomplete ($writtenBytes/$expectedBytes bytes)")
                }
                return DownloadFormat.from(response.header("Content-Type"), url)
            }
        }

        /** An earlier download whose file is still on disk; a record pointing at a missing file is cleared. */
        private suspend fun existingDownload(songId: String): File? {
            val record = downloadedTrackDao.getById(songId)
            val file = record?.let { File(it.filePath) }
            return if (file?.exists() == true) {
                file
            } else {
                if (record != null) downloadedTrackDao.deleteById(songId)
                null
            }
        }

        /** Streams [input] into [target] and returns total bytes written. */
        private fun writeBody(
            songId: String,
            input: InputStream,
            contentLength: Long,
            target: File,
        ): Long =
            input.use { inputStream ->
                FileOutputStream(target).use { outputStream ->
                    copyWithProgress(songId, inputStream, outputStream, contentLength)
                }
            }

        private fun copyWithProgress(
            songId: String,
            inputStream: InputStream,
            outputStream: OutputStream,
            contentLength: Long,
        ): Long {
            val buffer = ByteArray(BUFFER_SIZE)
            var totalBytesRead = 0L
            var lastProgress = -1

            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                if (contentLength > 0) {
                    val progress = ((totalBytesRead * PERCENT) / contentLength).toInt().coerceIn(0, PERCENT)
                    if (progress != lastProgress) {
                        lastProgress = progress
                        updateStatus(songId, DownloadStatus.Downloading(progress))
                    }
                }
            }
            return totalBytesRead
        }

        /**
         * Maps what the server actually sends to a file extension and MIME type, so an Opus/WebM or MP3
         * stream isn't stored as `.m4a` (which misleads other apps and the system media scanner).
         */
        private data class DownloadFormat(
            val extension: String,
            val mimeType: String,
        ) {
            companion object {
                private val MIME_FORMATS =
                    mapOf(
                        "audio/mp4" to DownloadFormat("m4a", "audio/mp4"),
                        "audio/m4a" to DownloadFormat("m4a", "audio/mp4"),
                        "audio/x-m4a" to DownloadFormat("m4a", "audio/mp4"),
                        "audio/aac" to DownloadFormat("m4a", "audio/mp4"),
                        "audio/webm" to DownloadFormat("webm", "audio/webm"),
                        "audio/ogg" to DownloadFormat("ogg", "audio/ogg"),
                        "application/ogg" to DownloadFormat("ogg", "audio/ogg"),
                        "audio/opus" to DownloadFormat("opus", "audio/opus"),
                        "audio/mpeg" to DownloadFormat("mp3", "audio/mpeg"),
                        "audio/mp3" to DownloadFormat("mp3", "audio/mpeg"),
                        "audio/flac" to DownloadFormat("flac", "audio/flac"),
                        "audio/x-flac" to DownloadFormat("flac", "audio/flac"),
                        "audio/wav" to DownloadFormat("wav", "audio/wav"),
                        "audio/x-wav" to DownloadFormat("wav", "audio/wav"),
                        "audio/wave" to DownloadFormat("wav", "audio/wav"),
                    )

                private val EXTENSION_FORMATS =
                    MIME_FORMATS.values.associateBy { it.extension } +
                        mapOf("mp4" to DownloadFormat("m4a", "audio/mp4"))

                private val DEFAULT = DownloadFormat("m4a", "audio/mp4")

                fun from(
                    contentType: String?,
                    url: String,
                ): DownloadFormat {
                    val mime = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
                    MIME_FORMATS[mime]?.let { return it }
                    val extension = url.substringBefore('?').substringAfterLast('.', "").lowercase()
                    return EXTENSION_FORMATS[extension] ?: DEFAULT
                }
            }
        }

        fun cancelDownload(trackId: String) {
            activeJobs.remove(trackId)?.cancel()
            val hash = sha256(trackId)
            val partFile = File(downloadsDir, "$hash.part")
            if (partFile.exists()) partFile.delete()
            updateStatus(trackId, DownloadStatus.Idle)
        }

        suspend fun deleteDownload(trackId: String) {
            withContext(Dispatchers.IO) {
                cancelDownload(trackId)
                val entity = downloadedTrackDao.getById(trackId)
                if (entity != null) {
                    val file = File(entity.filePath)
                    if (file.exists()) file.delete()
                    downloadedTrackDao.deleteById(trackId)
                } else {
                    downloadedTrackDao.deleteById(trackId)
                }
                val hash = sha256(trackId)
                downloadsDir.listFiles { _, name -> name.startsWith(hash) }?.forEach { it.delete() }
                trackTitles.remove(trackId)
                _downloads.value = _downloads.value - trackId
                if (_activeDownload.value?.trackId == trackId) _activeDownload.value = null
            }
        }

        suspend fun deleteAllDownloads() {
            withContext(Dispatchers.IO) {
                val all = downloadedTrackDao.getAll()
                for (item in all) {
                    cancelDownload(item.songId)
                    val file = File(item.filePath)
                    if (file.exists()) file.delete()
                }
                downloadedTrackDao.deleteAll()
                downloadsDir.listFiles()?.forEach { it.delete() }
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
            if (status is DownloadStatus.Idle) {
                if (_activeDownload.value?.trackId == songId) _activeDownload.value = null
            } else if (title != null) {
                _activeDownload.value = ActiveDownload(songId, title, status)
            }
            _downloads.value = _downloads.value + (songId to status)
        }

        private fun sha256(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }

        private companion object {
            const val BUFFER_SIZE = 8192
            const val PERCENT = 100
            const val MAX_ATTEMPTS = 2
        }
    }

data class ActiveDownload(
    val trackId: String,
    val title: String,
    val status: DownloadStatus,
)
