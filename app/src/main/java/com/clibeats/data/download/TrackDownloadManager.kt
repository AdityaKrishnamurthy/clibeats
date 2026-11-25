// ForbiddenImport: data-layer self-imports are legitimate
@file:Suppress("ForbiddenImport", "TooGenericExceptionCaught", "LongParameterList", "TooManyFunctions")
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.clibeats.data.download

import android.content.Context
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import com.clibeats.data.cache.MediaCaches
import com.clibeats.data.local.dao.DownloadedSongItem
import com.clibeats.data.local.dao.DownloadedTrackDao
import com.clibeats.data.local.dao.SongDao
import com.clibeats.data.local.entity.DownloadedTrackEntity
import com.clibeats.data.local.mapper.toDomain
import com.clibeats.data.local.mapper.toEntity
import com.clibeats.domain.model.Track
import com.clibeats.playback.StreamResolver
import com.clibeats.playback.TrackUriResolver
import com.clibeats.util.DiagnosticLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Suppress("LargeClass") // download, cache copy, removal and the downloads list share one state
@Singleton
class TrackDownloadManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val okHttpClient: OkHttpClient,
        private val streamResolver: dagger.Lazy<StreamResolver>,
        private val downloadedTrackDao: DownloadedTrackDao,
        private val songDao: SongDao,
        /** The streaming cache: a song already played in full is copied from it, with no network. */
        private val mediaCaches: dagger.Lazy<MediaCaches>? = null,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val _downloads = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
        val downloads: StateFlow<Map<String, DownloadStatus>> = _downloads.asStateFlow()

        private val _activeDownload = MutableStateFlow<ActiveDownload?>(null)
        val activeDownload: StateFlow<ActiveDownload?> = _activeDownload.asStateFlow()

        private val trackTitles = ConcurrentHashMap<String, String>()
        private val activeJobs = ConcurrentHashMap<String, Job>()

        /** Songs asked for but not finished yet (queued or downloading), in the order they were asked for. */
        private val pending = java.util.Collections.synchronizedMap(LinkedHashMap<String, Track>())
        private val _pendingTracks = MutableStateFlow<List<Track>>(emptyList())

        /** Shown in Downloaded (greyed out) until each one finishes. */
        val pendingTracks: StateFlow<List<Track>> = _pendingTracks.asStateFlow()

        /** At most this many songs download at once; the rest wait their turn (a whole playlist can be queued). */
        private val downloadSlots = Semaphore(PARALLEL_DOWNLOADS)

        private fun publishPending() {
            _pendingTracks.value = synchronized(pending) { pending.values.toList() }
        }

        /** Downloads every song in [tracks] that isn't downloaded or on its way yet, in order. */
        fun downloadAll(tracks: List<Track>) {
            val have = downloadedTrackIds.value
            tracks.filter { it.id !in have && it.providerId != LOCAL_PROVIDER_ID }.forEach(::downloadTrack)
        }

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

        /** Ids of downloads whose file is on disk; a record whose file is gone is dropped on the way. */
        private val presentIds: Flow<Set<String>> =
            downloadedTrackDao.getAllFlow()
                .map { list ->
                    val (present, missing) = list.partition { File(it.filePath).exists() }
                    missing.forEach { runCatching { downloadedTrackDao.deleteById(it.songId) } }
                    present.map { it.songId }.toSet()
                }.flowOn(Dispatchers.IO)

        fun getDownloadedSongs(): Flow<List<Track>> =
            combine(songDao.getDownloadedSongsAsFlow(), presentIds) { entities, ids ->
                entities.filter { it.id in ids }.map { it.toDomain() }
            }

        fun getDownloadedSongItems(): Flow<List<DownloadedSongItem>> =
            combine(downloadedTrackDao.getDownloadedSongItemsFlow(), presentIds) { items, ids ->
                items.filter { it.songId in ids }
            }

        /** Space the downloads really take: the files on disk (a missing file counts as nothing). */
        fun getTotalDownloadedSizeBytes(): Flow<Long> =
            downloadedTrackDao.getAllFlow()
                .map { list -> list.sumOf { File(it.filePath).takeIf(File::exists)?.length() ?: 0L } }
                .flowOn(Dispatchers.IO)

        init {
            // Later, not during app start (or while something is being downloaded or removed right away).
            scope.launch {
                delay(LEFTOVER_CLEANUP_DELAY_MS)
                removeLeftovers()
            }
        }

        /**
         * Files in the downloads folder that no download points to (an interrupted download, or one removed
         * while the app was closing) take space but never show anywhere: delete them.
         */
        private suspend fun removeLeftovers() {
            runCatching {
                val kept = downloadedTrackDao.getAll().map { File(it.filePath).absolutePath }.toSet()
                // Only old files: one written in the last minute may be a download finishing right now.
                val cutoff = System.currentTimeMillis() - LEFTOVER_AGE_MS
                downloadsDir.listFiles()?.forEach { file ->
                    if (file.absolutePath !in kept && file.lastModified() < cutoff) file.delete()
                }
            }
        }

        @Suppress("LongMethod", "CyclomaticComplexMethod", "ThrowsCount")
        fun downloadTrack(track: Track) {
            // A second tap while this song is already downloading must not restart it (and truncate the part file).
            if (activeJobs[track.id]?.isActive == true) return

            trackTitles[track.id] = track.title
            val hash = sha256(track.id)
            val partFile = File(downloadsDir, "$hash.part")

            activeJobs.remove(track.id)?.cancel()
            pending[track.id] = track
            publishPending()
            // Publish the start synchronously so the UI never observes a stale Completed/Failed status.
            updateStatus(track.id, DownloadStatus.Queued)

            val job =
                scope.launch {
                    try {
                        downloadSlots.withPermit { runDownload(track, hash, partFile) }
                    } finally {
                        if (pending.remove(track.id) != null) publishPending()
                    }
                }

            activeJobs[track.id] = job
        }

        @Suppress("LongMethod", "CyclomaticComplexMethod", "ThrowsCount", "NestedBlockDepth", "ReturnCount")
        private suspend fun runDownload(
            track: Track,
            hash: String,
            partFile: File,
        ) {
            updateStatus(track.id, DownloadStatus.Downloading(0))
            try {
                existingDownload(track.id)?.let { existing ->
                    updateStatus(track.id, DownloadStatus.Completed(existing))
                    return
                }

                // Played to the end before? The whole song is in the streaming cache: copy it.
                val cached = runCatching { copyFromSongCache(track, partFile) }.getOrNull()
                if (cached != null) {
                    finish(track, hash, partFile, cached)
                    return
                }

                var streamUrl: String =
                    resolveStreamUrl(track, forceRefresh = false) ?: run {
                        updateStatus(track.id, DownloadStatus.Failed("No stream URL available"))
                        return
                    }

                var lastError = "Download failed"
                for (attempt in 1..MAX_ATTEMPTS) {
                    try {
                        val format = fetch(track.id, streamUrl, partFile)
                        finish(track, hash, partFile, format)
                        return
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
                            return
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
                                return
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

        /** Renames the part file after its container, records it, and reports the download done. */
        private suspend fun finish(
            track: Track,
            hash: String,
            partFile: File,
            format: DownloadFormat,
        ) {
            val finalFile = File(downloadsDir, "$hash.${format.extension}")
            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.delete()
                throw IOException("Failed to finalize download file")
            }
            // Keep the file and the record in step: an orphan file would never show in Downloads.
            try {
                // Keep what's already stored for the song (local path, cache time, a cover the new copy lacks),
                // so a download never changes how the song shows in playlists, backups and exports.
                val stored = songDao.getById(track.id)
                val entity = track.toEntity(stored?.localPath, stored?.cachedAt)
                songDao.upsert(
                    if (entity.artworkUrl.isNullOrBlank()) entity.copy(artworkUrl = stored?.artworkUrl) else entity,
                )
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
        }

        /**
         * Copies [track] out of the streaming cache into [target] when the cache holds the whole song (it was
         * played to the end). Returns its container, or null when it isn't fully cached.
         */
        @Suppress("NestedBlockDepth", "ReturnCount")
        private suspend fun copyFromSongCache(
            track: Track,
            target: File,
        ): DownloadFormat? {
            val cache = mediaCaches?.get()?.songCache ?: return null
            val key = TrackUriResolver.placeholderUri(track).toString()
            val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
            if (length <= 0 || !cache.isCached(key, 0, length)) return null
            val progress = Progress(track.id).apply { total = length }
            val source = CacheDataSource(cache, null)
            val head = ByteArray(SNIFF_BYTES)
            var headSize = 0
            try {
                source.open(DataSpec.Builder().setUri(key).setKey(key).build())
                val buffer = ByteArray(BUFFER_SIZE)
                var written = 0L
                FileOutputStream(target).use { out ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = source.read(buffer, 0, buffer.size)
                        if (read == C.RESULT_END_OF_INPUT) break
                        if (headSize < SNIFF_BYTES) {
                            val n = minOf(read, SNIFF_BYTES - headSize)
                            System.arraycopy(buffer, 0, head, headSize, n)
                            headSize += n
                        }
                        out.write(buffer, 0, read)
                        written += read
                        progress.add(read.toLong())
                    }
                }
                if (written != length) {
                    target.delete()
                    return null
                }
            } finally {
                runCatching { source.close() }
            }
            return DownloadFormat.sniff(head, headSize)
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

        /**
         * Downloads [url] into [target] and returns the container; throws so the caller retries.
         *
         * Why ranged and parallel: YouTube's servers send one long request at roughly playback speed, so a
         * single GET of a 4-minute song took minutes. Short byte ranges are served at full speed, so the file
         * is fetched as [CHUNK_BYTES] pieces, [PARALLEL_CHUNKS] at a time, each written at its own offset.
         * A server that ignores the range (answers 200 with the whole file) is read in one go, as before.
         */
        private suspend fun fetch(
            songId: String,
            url: String,
            target: File,
        ): DownloadFormat {
            val progress = Progress(songId)
            openRange(url, 0, CHUNK_BYTES - 1).use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) throw IOException("HTTP ${response.code}")
                val format = DownloadFormat.from(response.header("Content-Type"), url)
                val total = if (response.code == HTTP_PARTIAL) totalFromContentRange(response) else -1L

                if (response.code != HTTP_PARTIAL || total <= 0L) {
                    // Whole file in one response (no range support, or unknown length).
                    val expectedBytes = body.contentLength()
                    progress.total = expectedBytes
                    val written = writeBody(body.byteStream(), target, progress)
                    if (expectedBytes >= 0 && written != expectedBytes) {
                        throw IOException("Download incomplete ($written/$expectedBytes bytes)")
                    }
                    return format
                }

                progress.total = total
                RandomAccessFile(target, "rw").use { raf ->
                    raf.setLength(total)
                    val channel = raf.channel
                    // First piece: already open, write it at 0.
                    val firstLen = minOf(CHUNK_BYTES, total)
                    writeRange(body.byteStream(), channel, 0, firstLen, progress)

                    val ranges =
                        (firstLen until total step CHUNK_BYTES).map { from ->
                            from to minOf(from + CHUNK_BYTES, total) - 1
                        }
                    val next = AtomicInteger(0)
                    coroutineScope {
                        repeat(minOf(PARALLEL_CHUNKS, ranges.size)) {
                            launch(Dispatchers.IO) {
                                while (true) {
                                    val (from, to) = ranges.getOrNull(next.getAndIncrement()) ?: break
                                    downloadRange(url, channel, from, to, progress)
                                }
                            }
                        }
                    }
                }
                return format
            }
        }

        /** One byte range, retried a few times on a dropped connection; rewrites the range from its start. */
        @Suppress("NestedBlockDepth")
        private suspend fun downloadRange(
            url: String,
            channel: FileChannel,
            from: Long,
            to: Long,
            progress: Progress,
        ) {
            var attempt = 0
            while (true) {
                coroutineContext.ensureActive()
                val counted = AtomicLong(0)
                try {
                    openRange(url, from, to).use { response ->
                        val body = response.body
                        if (response.code != HTTP_PARTIAL || body == null) throw IOException("HTTP ${response.code}")
                        writeRange(body.byteStream(), channel, from, to - from + 1, progress, counted)
                    }
                    return
                } catch (e: IOException) {
                    // The range is written again from its start: take its bytes back out of the progress.
                    progress.add(-counted.get())
                    if (++attempt >= RANGE_ATTEMPTS) throw e
                }
            }
        }

        private fun openRange(
            url: String,
            from: Long,
            to: Long,
        ): Response {
            val request = Request.Builder().url(url).header("Range", "bytes=$from-$to").build()
            return okHttpClient.newCall(request).execute()
        }

        /** `Content-Range: bytes 0-1048575/4031337` -> 4031337, or -1 when missing. */
        private fun totalFromContentRange(response: Response): Long =
            response.header("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull() ?: -1L

        /** Writes exactly [length] bytes of [input] at [offset]; throws if the stream ends early. */
        private suspend fun writeRange(
            input: InputStream,
            channel: FileChannel,
            offset: Long,
            length: Long,
            progress: Progress,
            counted: AtomicLong = AtomicLong(0),
        ) {
            val buffer = ByteArray(BUFFER_SIZE)
            var written = 0L
            input.use { stream ->
                while (written < length) {
                    coroutineContext.ensureActive()
                    val want = minOf(buffer.size.toLong(), length - written).toInt()
                    val read = stream.read(buffer, 0, want)
                    if (read == -1) break
                    val chunk = ByteBuffer.wrap(buffer, 0, read)
                    var position = offset + written
                    while (chunk.hasRemaining()) position += channel.write(chunk, position)
                    written += read
                    counted.addAndGet(read.toLong())
                    progress.add(read.toLong())
                }
            }
            if (written != length) throw IOException("Download incomplete ($written/$length bytes)")
        }

        /** Bytes received so far across all pieces; publishes whole-percent steps only. */
        private inner class Progress(
            private val songId: String,
        ) {
            @Volatile var total: Long = -1L
            private val received = AtomicLong(0)
            private val lastPercent = AtomicInteger(-1)

            fun add(bytes: Long) {
                val now = received.addAndGet(bytes)
                val size = total
                if (size <= 0) return
                val percent = ((now * PERCENT) / size).toInt().coerceIn(0, PERCENT)
                val previous = lastPercent.get()
                if (percent > previous && lastPercent.compareAndSet(previous, percent)) {
                    updateStatus(songId, DownloadStatus.Downloading(percent))
                }
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
        @Suppress("NestedBlockDepth")
        private suspend fun writeBody(
            input: InputStream,
            target: File,
            progress: Progress,
        ): Long {
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0L
            input.use { stream ->
                FileOutputStream(target).use { out ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = stream.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        total += read
                        progress.add(read.toLong())
                    }
                }
            }
            return total
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

                /** The container from a file's first bytes (the streaming cache keeps no content type). */
                @Suppress("MagicNumber") // file signatures
                fun sniff(
                    head: ByteArray,
                    size: Int,
                ): DownloadFormat {
                    fun at(
                        offset: Int,
                        vararg bytes: Int,
                    ) = size >= offset + bytes.size && bytes.indices.all { head[offset + it] == bytes[it].toByte() }
                    return when {
                        at(0, 0x1A, 0x45, 0xDF, 0xA3) -> MIME_FORMATS.getValue("audio/webm")
                        at(4, 'f'.code, 't'.code, 'y'.code, 'p'.code) -> DEFAULT
                        at(0, 'O'.code, 'g'.code, 'g'.code, 'S'.code) -> MIME_FORMATS.getValue("audio/ogg")
                        at(0, 'f'.code, 'L'.code, 'a'.code, 'C'.code) -> MIME_FORMATS.getValue("audio/flac")
                        at(0, 'I'.code, 'D'.code, '3'.code) || at(0, 0xFF, 0xFB) -> MIME_FORMATS.getValue("audio/mpeg")
                        else -> DEFAULT
                    }
                }

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

        /**
         * Removes one download: its record first (the song leaves Downloaded at once), then its files.
         * Runs to the end even if the screen that asked is closed meanwhile.
         */
        suspend fun deleteDownload(trackId: String) {
            withContext(Dispatchers.IO + NonCancellable) {
                cancelDownload(trackId)
                val entity = downloadedTrackDao.getById(trackId)
                downloadedTrackDao.deleteById(trackId)
                entity?.let { File(it.filePath).delete() }
                val hash = sha256(trackId)
                downloadsDir.listFiles { _, name -> name.startsWith(hash) }?.forEach { it.delete() }
                trackTitles.remove(trackId)
                _downloads.value = _downloads.value - trackId
                if (_activeDownload.value?.trackId == trackId) _activeDownload.value = null
            }
        }

        /**
         * Removes every download: running ones are stopped, all records go (Downloaded empties at once), then
         * every file in the downloads folder. Runs to the end even if the screen that asked is closed.
         */
        suspend fun deleteAllDownloads() {
            withContext(Dispatchers.IO + NonCancellable) {
                activeJobs.keys.toList().forEach(::cancelDownload)
                pending.clear()
                publishPending()
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
            when {
                status is DownloadStatus.Idle ->
                    if (_activeDownload.value?.trackId == songId) _activeDownload.value = null
                // A queued song doesn't take over the banner from the one downloading now.
                status is DownloadStatus.Queued -> Unit
                title != null -> {
                    val waiting = synchronized(pending) { pending.keys.count { it != songId } }
                    _activeDownload.value = ActiveDownload(songId, title, status, waiting)
                }
            }
            _downloads.value = _downloads.value + (songId to status)
        }

        private fun sha256(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }

        companion object {
            /** Songs downloaded at the same time. */
            const val PARALLEL_DOWNLOADS = 2
            private const val BUFFER_SIZE = 64 * 1024
            private const val PERCENT = 100
            private const val MAX_ATTEMPTS = 2
            private const val HTTP_PARTIAL = 206

            /** 1 MB pieces: small enough that YouTube serves each one at full speed. */
            private const val CHUNK_BYTES = 1L * 1024 * 1024

            /** Pieces fetched at the same time (a typical song is 3 to 8 pieces). */
            private const val PARALLEL_CHUNKS = 4
            private const val RANGE_ATTEMPTS = 3
            private const val LOCAL_PROVIDER_ID = "local"
            private const val SNIFF_BYTES = 12
            private const val LEFTOVER_AGE_MS = 60_000L
            private const val LEFTOVER_CLEANUP_DELAY_MS = 30_000L
        }
    }

data class ActiveDownload(
    val trackId: String,
    val title: String,
    val status: DownloadStatus,
    /** Other songs still queued or downloading. */
    val waiting: Int = 0,
)
