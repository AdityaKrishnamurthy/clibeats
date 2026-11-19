package com.clibeats.data.playlist

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps custom playlist covers picked from the device in app storage (`filesDir/covers/`).
 *
 * Every pick gets a new file name, so image caches keyed by the file URI never show the previous
 * picture, and the playlist's older cover files are deleted.
 */
@Singleton
class PlaylistCoverStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val dir: File get() = File(context.filesDir, COVERS_DIR)

        /** Copies [imageUri] into app storage and returns the stored file's URI, or null on failure. */
        suspend fun save(
            playlistId: String,
            imageUri: String,
        ): String? =
            withContext(Dispatchers.IO) {
                runCatching {
                    dir.mkdirs()
                    val target = File(dir, "${prefixFor(playlistId)}${System.currentTimeMillis()}.jpg")
                    val copied =
                        context.contentResolver.openInputStream(Uri.parse(imageUri))?.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                    if (copied == null || target.length() == 0L) {
                        target.delete()
                        return@runCatching null
                    }
                    deleteFor(playlistId, keep = target)
                    Uri.fromFile(target).toString()
                }.getOrNull()
            }

        /** Deletes every stored cover of [playlistId] except [keep]. */
        suspend fun deleteFor(
            playlistId: String,
            keep: File? = null,
        ) {
            withContext(Dispatchers.IO) {
                val prefix = prefixFor(playlistId)
                dir.listFiles()?.filter { it.name.startsWith(prefix) && it != keep }?.forEach { it.delete() }
            }
        }

        /** Playlist ids can contain ':' or '/', so file names use a stable hash of the id. */
        private fun prefixFor(playlistId: String): String {
            val hash = UUID.nameUUIDFromBytes(playlistId.toByteArray(Charsets.UTF_8))
            return "playlist_${hash}_"
        }

        private companion object {
            const val COVERS_DIR = "covers"
        }
    }
