@file:Suppress("ForbiddenImport", "TooGenericExceptionCaught", "NestedBlockDepth", "SwallowedException")

package com.clibeats.data.scan

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.clibeats.data.local.dao.SongDao
import com.clibeats.data.local.entity.SongEntity
import com.clibeats.domain.provider.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScanStatus {
    data object Idle : ScanStatus

    data class Scanning(val processed: Int, val total: Int) : ScanStatus

    data class Done(val trackCount: Int) : ScanStatus

    data class Failed(val message: String) : ScanStatus
}

/**
 * Scans a user-picked SAF folder (no storage permission needed) for audio files and stores
 * them as `local` songs. Titles/artists/albums come from the file's tags, not its name.
 */
@Singleton
class LocalMusicScanner
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val songDao: SongDao,
    ) {
        private val _status = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
        val status: StateFlow<ScanStatus> = _status.asStateFlow()
        private val mutex = Mutex()

        private data class AudioDoc(
            val documentId: String,
            val name: String,
            val lastModified: Long,
        )

        /** Returns the number of local tracks after the scan. */
        suspend fun scan(treeUri: Uri): Int =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    try {
                        _status.value = ScanStatus.Scanning(0, 0)
                        val docs = listAudioDocuments(treeUri)
                        val existing = songDao.getLocalSongs().associateBy { it.id }
                        val found = ArrayList<SongEntity>(docs.size)
                        docs.forEachIndexed { index, doc ->
                            val id = ProviderId.composite(LOCAL, doc.documentId)
                            val previous = existing[id]
                            // cached_at = file last-modified for local songs; unchanged files skip tag reads.
                            val unchanged = previous?.cachedAt == doc.lastModified && doc.lastModified != 0L
                            val entity =
                                if (previous != null && unchanged) {
                                    previous
                                } else {
                                    readTags(treeUri, doc, id)
                                }
                            found += entity
                            if (index % PROGRESS_EVERY == 0) _status.value = ScanStatus.Scanning(index + 1, docs.size)
                        }
                        found.chunked(DB_CHUNK).forEach { songDao.upsertAll(it) }
                        val foundIds = found.mapTo(HashSet()) { it.id }
                        existing.keys.filterNot { it in foundIds }.chunked(DB_CHUNK).forEach { songDao.deleteByIds(it) }
                        _status.value = ScanStatus.Done(found.size)
                        found.size
                    } catch (e: Exception) {
                        _status.value = ScanStatus.Failed(e.message ?: "Scan failed")
                        throw e
                    }
                }
            }

        private fun listAudioDocuments(treeUri: Uri): List<AudioDoc> {
            val result = mutableListOf<AudioDoc>()
            val pending = ArrayDeque<String>().apply { add(DocumentsContract.getTreeDocumentId(treeUri)) }
            val projection =
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                )
            while (pending.isNotEmpty()) {
                val parentId = pending.removeLast()
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val docId = c.getString(COL_ID) ?: continue
                        val name = c.getString(COL_NAME).orEmpty()
                        val mime = c.getString(COL_MIME).orEmpty()
                        when {
                            mime == DocumentsContract.Document.MIME_TYPE_DIR -> pending.add(docId)
                            isAudio(mime, name) -> {
                                val modified = if (c.isNull(COL_MODIFIED)) 0L else c.getLong(COL_MODIFIED)
                                result += AudioDoc(docId, name, modified)
                            }
                        }
                    }
                }
            }
            return result
        }

        private fun readTags(
            treeUri: Uri,
            doc: AudioDoc,
            id: String,
        ): SongEntity {
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, doc.documentId)
            val retriever = MediaMetadataRetriever()
            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var durationMs = 0L
            var artworkPath: String? = null
            try {
                retriever.setDataSource(context, uri)
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                artist =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                durationMs =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                artworkPath = retriever.embeddedPicture?.let { saveArtwork(id, it) }
            } catch (e: Exception) {
                // Unreadable tags: keep the file playable with filename-based fallbacks below.
            } finally {
                runCatching { retriever.release() }
            }
            return SongEntity(
                id = id,
                title = title?.takeIf { it.isNotBlank() } ?: doc.name.substringBeforeLast('.'),
                artist = artist?.takeIf { it.isNotBlank() } ?: UNKNOWN_ARTIST,
                album = album?.takeIf { it.isNotBlank() } ?: UNKNOWN_ALBUM,
                durationMs = durationMs,
                artworkUrl = artworkPath,
                streamUrl = uri.toString(),
                providerId = LOCAL,
                cachedAt = doc.lastModified,
            )
        }

        private fun saveArtwork(
            id: String,
            bytes: ByteArray,
        ): String? =
            runCatching {
                val dir = File(context.filesDir, "local_artwork").apply { mkdirs() }
                val file = File(dir, UUID.nameUUIDFromBytes(id.toByteArray()).toString() + ".img")
                file.writeBytes(bytes)
                Uri.fromFile(file).toString()
            }.getOrNull()

        companion object {
            const val LOCAL = "local"
            private const val UNKNOWN_ARTIST = "Unknown Artist"
            private const val UNKNOWN_ALBUM = "Unknown Album"
            private const val PROGRESS_EVERY = 10
            private const val COL_ID = 0
            private const val COL_NAME = 1
            private const val COL_MIME = 2
            private const val COL_MODIFIED = 3
            private const val DB_CHUNK = 500
            private val AUDIO_EXTENSIONS =
                setOf("flac", "mp3", "wav", "m4a", "aac", "ogg", "opus", "alac", "aiff", "aif")

            fun isAudio(
                mime: String,
                name: String,
            ): Boolean = mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
        }
    }
