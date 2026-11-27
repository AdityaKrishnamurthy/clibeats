@file:Suppress("ForbiddenImport")

package com.clibeats.data.download

import android.content.Context
import coil.intercept.Interceptor
import coil.request.ImageResult
import com.clibeats.domain.model.Track
import com.clibeats.util.ARTWORK_FULL_PX
import com.clibeats.util.ARTWORK_THUMB_PX
import com.clibeats.util.artworkBaseKey
import com.clibeats.util.artworkUrlFor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Covers of downloaded songs, kept with the downloads (not in the cover-art cache), so clearing that cache or it
 * filling up never leaves a downloaded song without its picture offline. Songs sharing a cover (an album) share
 * one file. Every image request for such a cover is answered from here ([interceptor]).
 */
@Singleton
class DownloadCovers
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val okHttpClient: OkHttpClient,
    ) {
        private val dir: File by lazy { File(context.filesDir, "download_covers").apply { mkdirs() } }

        /** Names of the covers on disk, read once. */
        private val saved: MutableSet<String> by lazy {
            ConcurrentHashMap.newKeySet<String>().apply {
                dir.listFiles()?.filter { it.isFile && !it.name.endsWith(TEMP) }?.forEach { add(it.name) }
            }
        }

        private fun nameFor(url: String): String? {
            val key = artworkBaseKey(url) ?: return null
            val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }

        /** The kept cover for [url] (any size of it), if there is one. */
        fun fileFor(url: String): File? {
            val name = nameFor(url) ?: return null
            return if (name in saved) File(dir, name) else null
        }

        /** Keeps [track]'s cover with its download (fetched once; nothing happens when it's already kept). */
        @Suppress("ReturnCount")
        suspend fun keep(track: Track) {
            val url = track.artworkUrl?.takeIf { it.startsWith("http") } ?: return
            val name = nameFor(url) ?: return
            if (name in saved) return
            withContext(Dispatchers.IO) {
                val candidates =
                    listOfNotNull(artworkUrlFor(url, ARTWORK_FULL_PX), artworkUrlFor(url, ARTWORK_THUMB_PX), url)
                        .distinct()
                for (candidate in candidates) {
                    if (fetchTo(candidate, name)) {
                        saved.add(name)
                        break
                    }
                }
            }
        }

        private fun fetchTo(
            url: String,
            name: String,
        ): Boolean =
            runCatching {
                val temp = File(dir, name + TEMP)
                okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) return@runCatching false
                    temp.outputStream().use { out -> body.byteStream().copyTo(out) }
                }
                temp.length() > 0 && temp.renameTo(File(dir, name))
            }.getOrDefault(false)

        /** Removes covers no downloaded song uses any more ([stillDownloaded]: what's left). */
        suspend fun keepOnly(stillDownloaded: List<Track>) =
            withContext(Dispatchers.IO) {
                val used = stillDownloaded.mapNotNull { it.artworkUrl?.let(::nameFor) }.toSet()
                saved.toList().filter { it !in used }.forEach { name ->
                    File(dir, name).delete()
                    saved.remove(name)
                }
            }

        /** Total size of the kept covers. */
        fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

        /** Answers image requests for a kept cover from its file (works offline, survives cache clears). */
        val interceptor: Interceptor =
            object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    val data = chain.request.data as? String
                    val file = data?.takeIf { it.startsWith("http") && saved.isNotEmpty() }?.let(::fileFor)
                    return if (file != null) {
                        chain.proceed(chain.request.newBuilder().data(file).build())
                    } else {
                        chain.proceed(chain.request)
                    }
                }
            }

        private companion object {
            const val TEMP = ".tmp"
        }
    }
