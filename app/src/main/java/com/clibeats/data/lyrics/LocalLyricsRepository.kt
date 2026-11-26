@file:Suppress("ReturnCount")

package com.clibeats.data.lyrics

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.LruCache
import com.clibeats.domain.model.Lyrics
import com.clibeats.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lyrics for songs on the phone, read from the file itself: its lyrics tags (see [TagLyricsReader]) and a
 * `.lrc` file with the same name next to it. The best version wins: syllable-synced over line-synced over
 * plain text. Remembered per file for the app run.
 */
@Singleton
class LocalLyricsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val cache = LruCache<String, CachedLyrics>(CACHE_SIZE)

        private class CachedLyrics(
            val lyrics: Lyrics?,
        )

        /** Keeps [lyrics] (e.g. with translations added) as [track]'s for the rest of the app run. */
        fun remember(
            track: Track,
            lyrics: Lyrics,
        ) {
            track.streamUrl?.takeIf { it.isNotBlank() }?.let { cache.put(it, CachedLyrics(lyrics)) }
        }

        /** Null when [track] isn't a local file or has no lyrics. */
        suspend fun lyricsFor(track: Track): Lyrics? {
            if (track.providerId != LOCAL_PROVIDER) return null
            val location = track.streamUrl?.takeIf { it.isNotBlank() } ?: return null
            cache.get(location)?.let { return it.lyrics }
            val lyrics =
                withContext(Dispatchers.IO) {
                    val candidates =
                        runCatching { tagTexts(location) }.getOrDefault(emptyList()) +
                            listOfNotNull(runCatching { sidecarText(location) }.getOrNull())
                    pickBest(candidates.mapNotNull(LrcParser::parse))
                }
            cache.put(location, CachedLyrics(lyrics))
            return lyrics
        }

        private fun tagTexts(location: String): List<String> {
            if (location.startsWith("content:")) {
                val descriptor =
                    context.contentResolver.openFileDescriptor(Uri.parse(location), "r") ?: return emptyList()
                return descriptor.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        TagLyricsReader.read(ChannelSource(input.channel))
                    }
                }
            }
            val file = fileOf(location) ?: return emptyList()
            return FileInputStream(file).use { input -> TagLyricsReader.read(ChannelSource(input.channel)) }
        }

        /** The text of "song.lrc" beside "song.flac" (same folder, any case of the extension), if there is one. */
        private fun sidecarText(location: String): String? {
            if (location.startsWith("content:")) {
                val uri = Uri.parse(location)
                if (!DocumentsContract.isDocumentUri(context, uri)) return null
                val id = DocumentsContract.getDocumentId(uri)
                val base = id.substringBeforeLast('.', missingDelimiterValue = "").ifEmpty { return null }
                return LRC_EXTENSIONS.firstNotNullOfOrNull { ext ->
                    runCatching {
                        val sibling = DocumentsContract.buildDocumentUriUsingTree(uri, "$base$ext")
                        context.contentResolver.openInputStream(sibling)?.use { readLimited(it) }
                    }.getOrNull()
                }
            }
            val file = fileOf(location) ?: return null
            val base = file.path.substringBeforeLast('.')
            return LRC_EXTENSIONS.map { File("$base$it") }.firstOrNull { it.isFile && it.length() <= MAX_LRC_BYTES }
                ?.inputStream()?.use { readLimited(it) }
        }

        private fun readLimited(input: java.io.InputStream): String? {
            val bytes = input.readNBytesCompat(MAX_LRC_BYTES.toInt())
            return bytes.toString(Charsets.UTF_8).removePrefix("").takeIf { it.isNotBlank() }
        }

        private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_BYTES)
            while (out.size() < limit) {
                val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
                if (n <= 0) break
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }

        private fun fileOf(location: String): File? {
            val path = if (location.startsWith("file://")) Uri.parse(location).path else location
            return path?.let(::File)?.takeIf { it.isFile }
        }

        private companion object {
            const val LOCAL_PROVIDER = "local"
            const val CACHE_SIZE = 64
            const val MAX_LRC_BYTES = 2L * 1024 * 1024
            const val BUFFER_BYTES = 8 * 1024
            val LRC_EXTENSIONS = listOf(".lrc", ".LRC")
        }
    }

/**
 * The version to show when a song carries several: syllable-synced, then line-synced, then plain text; among
 * equals, the one with more lines (some taggers keep an older, shorter copy in a second field).
 */
internal fun pickBest(candidates: List<Lyrics>): Lyrics? =
    candidates.maxWithOrNull(
        compareBy<Lyrics> {
            when {
                it is Lyrics.Synced && it.hasWordTiming -> 2
                it is Lyrics.Synced -> 1
                else -> 0
            }
        }.thenBy {
            when (it) {
                is Lyrics.Synced -> it.lines.count { line -> line.text.isNotBlank() }
                is Lyrics.Plain -> it.text.lines().count { line -> line.isNotBlank() }
            }
        },
    )
