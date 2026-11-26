// ForbiddenImport: data-layer self-imports are legitimate
@file:Suppress("ForbiddenImport", "TooGenericExceptionCaught")
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.clibeats.data.download

import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import com.clibeats.data.cache.MediaCaches
import com.clibeats.domain.model.Track
import com.clibeats.playback.StreamResolver
import com.clibeats.playback.TrackUriResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Fetches audio bytes either from Media3's streaming cache or over parallel HTTP byte ranges
 * with chunk-level resume metadata stored alongside the `.part` file.
 */
@Singleton
class DownloadStreamFetcher
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val streamResolver: dagger.Lazy<StreamResolver>,
        private val mediaCaches: dagger.Lazy<MediaCaches>? = null,
    ) {
        suspend fun resolveStreamUrl(
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

        fun invalidateStream(track: Track) {
            streamResolver.get().invalidate(track.providerId, track.id)
        }

        @Suppress("NestedBlockDepth", "ReturnCount")
        suspend fun copyFromSongCache(
            track: Track,
            target: File,
            onPercent: (Int) -> Unit,
        ): DownloadFormat? {
            val cache = mediaCaches?.get()?.songCache ?: return null
            val key = TrackUriResolver.placeholderUri(track).toString()
            val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
            if (length <= 0 || !cache.isCached(key, 0, length)) return null
            val progress = Progress(onPercent).apply { total = length }
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

        suspend fun fetch(
            url: String,
            partFile: File,
            chunksFile: File,
            onPercent: (Int) -> Unit,
        ): DownloadFormat {
            val progress = Progress(onPercent)
            val checkpoint = readCheckpoint(chunksFile, partFile)
            return if (checkpoint != null) {
                resumeRanged(url, partFile, chunksFile, checkpoint, progress)
            } else {
                fetchFresh(url, partFile, chunksFile, progress)
            }
        }

        private suspend fun fetchFresh(
            url: String,
            partFile: File,
            chunksFile: File,
            progress: Progress,
        ): DownloadFormat {
            openRange(url, 0, CHUNK_BYTES - 1).use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) throw IOException("HTTP ${response.code}")
                val format = DownloadFormat.from(response.header("Content-Type"), url)
                val total = if (response.code == HTTP_PARTIAL) totalFromContentRange(response) else -1L

                if (response.code != HTTP_PARTIAL || total <= 0L) {
                    val expectedBytes = body.contentLength()
                    progress.total = expectedBytes
                    val written = writeBody(body.byteStream(), partFile, progress)
                    if (expectedBytes >= 0 && written != expectedBytes) {
                        throw IOException("Download incomplete ($written/$expectedBytes bytes)")
                    }
                    return format
                }

                progress.total = total
                val chunkLock = Any()
                chunksFile.writeText("v1:$total:${format.extension}:${format.mimeType}\n")
                RandomAccessFile(partFile, "rw").use { raf ->
                    raf.setLength(total)
                    val target = RangeTarget(url, raf.channel, chunksFile, chunkLock, progress)
                    val firstLen = minOf(CHUNK_BYTES, total)
                    writeRange(body.byteStream(), 0, firstLen, target)
                    appendCompletedChunk(chunksFile, 0L, chunkLock)

                    val ranges =
                        (firstLen until total step CHUNK_BYTES).map { from ->
                            from to minOf(from + CHUNK_BYTES, total) - 1
                        }
                    fetchMissingRanges(target, ranges)
                }
                return format
            }
        }

        private suspend fun resumeRanged(
            url: String,
            partFile: File,
            chunksFile: File,
            checkpoint: RangeCheckpoint,
            progress: Progress,
        ): DownloadFormat {
            val total = checkpoint.totalBytes
            progress.total = total
            val allRanges =
                (0L until total step CHUNK_BYTES).map { from ->
                    from to minOf(from + CHUNK_BYTES, total) - 1
                }
            val alreadyDone =
                allRanges
                    .filter { (from, _) -> from in checkpoint.completedOffsets }
                    .sumOf { (from, to) -> to - from + 1 }
            if (alreadyDone > 0) progress.add(alreadyDone)

            val missing = allRanges.filter { (from, _) -> from !in checkpoint.completedOffsets }
            val chunkLock = Any()
            RandomAccessFile(partFile, "rw").use { raf ->
                raf.setLength(total)
                val target = RangeTarget(url, raf.channel, chunksFile, chunkLock, progress)
                fetchMissingRanges(target, missing)
            }
            return checkpoint.format
        }

        private suspend fun fetchMissingRanges(
            target: RangeTarget,
            ranges: List<Pair<Long, Long>>,
        ) {
            if (ranges.isEmpty()) return
            val next = AtomicInteger(0)
            coroutineScope {
                repeat(minOf(PARALLEL_CHUNKS, ranges.size)) {
                    launch(Dispatchers.IO) {
                        while (true) {
                            val (from, to) = ranges.getOrNull(next.getAndIncrement()) ?: break
                            downloadRange(target, from, to)
                            appendCompletedChunk(target.chunksFile, from, target.chunkLock)
                        }
                    }
                }
            }
        }

        private fun appendCompletedChunk(
            chunksFile: File,
            offset: Long,
            lock: Any,
        ) {
            synchronized(lock) { chunksFile.appendText("$offset\n") }
        }

        private fun readCheckpoint(
            chunksFile: File,
            partFile: File,
        ): RangeCheckpoint? {
            if (!chunksFile.isFile || !partFile.isFile) return null
            val lines = chunksFile.readLines().filter { it.isNotBlank() }
            val parts =
                lines
                    .firstOrNull()
                    ?.split(":")
                    ?.takeIf { it.size == CHECKPOINT_HEADER_PARTS && it[0] == "v1" }
            val total = parts?.get(1)?.toLongOrNull()?.takeIf { it > 0L && partFile.length() == it }
            return if (parts != null && total != null) {
                val completed = lines.drop(1).mapNotNull { it.toLongOrNull() }.toSet()
                RangeCheckpoint(total, DownloadFormat(parts[HEADER_EXT_INDEX], parts[HEADER_MIME_INDEX]), completed)
            } else {
                null
            }
        }

        @Suppress("NestedBlockDepth")
        private suspend fun downloadRange(
            target: RangeTarget,
            from: Long,
            to: Long,
        ) {
            var attempt = 0
            while (true) {
                coroutineContext.ensureActive()
                val counted = AtomicLong(0)
                try {
                    openRange(target.url, from, to).use { response ->
                        val body = response.body
                        if (response.code != HTTP_PARTIAL || body == null) throw IOException("HTTP ${response.code}")
                        writeRange(body.byteStream(), from, to - from + 1, target, counted)
                    }
                    return
                } catch (e: IOException) {
                    target.progress.add(-counted.get())
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

        private fun totalFromContentRange(response: Response): Long =
            response.header("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull() ?: -1L

        private suspend fun writeRange(
            input: InputStream,
            offset: Long,
            length: Long,
            target: RangeTarget,
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
                    while (chunk.hasRemaining()) position += target.channel.write(chunk, position)
                    written += read
                    counted.addAndGet(read.toLong())
                    target.progress.add(read.toLong())
                }
            }
            if (written != length) throw IOException("Download incomplete ($written/$length bytes)")
        }

        private data class RangeTarget(
            val url: String,
            val channel: FileChannel,
            val chunksFile: File,
            val chunkLock: Any,
            val progress: Progress,
        )

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

        private class Progress(
            private val onPercent: (Int) -> Unit,
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
                    onPercent(percent)
                }
            }
        }

        private data class RangeCheckpoint(
            val totalBytes: Long,
            val format: DownloadFormat,
            val completedOffsets: Set<Long>,
        )

        companion object {
            private const val BUFFER_SIZE = 64 * 1024
            private const val PERCENT = 100
            private const val HTTP_PARTIAL = 206
            private const val CHUNK_BYTES = 1L * 1024 * 1024
            private const val PARALLEL_CHUNKS = 4
            private const val RANGE_ATTEMPTS = 3
            private const val SNIFF_BYTES = 12
            private const val CHECKPOINT_HEADER_PARTS = 4
            private const val HEADER_EXT_INDEX = 2
            private const val HEADER_MIME_INDEX = 3
        }
    }

data class DownloadFormat(
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

        @Suppress("MagicNumber")
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
