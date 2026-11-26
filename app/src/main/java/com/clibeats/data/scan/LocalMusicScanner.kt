@file:Suppress(
    "ForbiddenImport",
    "TooGenericExceptionCaught",
    "NestedBlockDepth",
    "SwallowedException",
    "LongMethod",
    "CyclomaticComplexMethod",
)

package com.clibeats.data.scan

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.clibeats.data.local.dao.PlaylistDao
import com.clibeats.data.local.dao.SongDao
import com.clibeats.data.local.entity.PlaylistEntity
import com.clibeats.data.local.entity.SongEntity
import com.clibeats.domain.provider.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
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
        private val playlistDao: PlaylistDao,
    ) {
        private val _status = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
        val status: StateFlow<ScanStatus> = _status.asStateFlow()
        private val mutex = Mutex()

        private data class AudioDoc(
            val documentId: String,
            val name: String,
            val lastModified: Long,
            // Normalized path inside the picked tree, e.g. "rock/queen/song.flac".
            val relativePath: String,
        )

        private data class Listing(
            val audio: List<AudioDoc>,
            val playlists: List<AudioDoc>,
        )

        /** A failed tree must never be treated as an empty tree: that would delete its songs. */
        private sealed interface ListResult {
            data class Ok(val listing: Listing) : ListResult

            data object Failed : ListResult
        }

        /**
         * Scans every chosen music folder in one pass and returns the number of local tracks.
         * One pass matters: "remove songs that are gone" must see all folders, or scanning one
         * folder would delete the others' songs.
         *
         * [force] (the Rescan button) re-reads every file's tags and cover, so edits show up even when
         * the tag editor didn't change the file's date. Otherwise unchanged files are skipped.
         */
        suspend fun scan(
            treeUris: List<Uri>,
            force: Boolean = false,
        ): Int =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    try {
                        _status.value = ScanStatus.Scanning(0, 0)
                        // A revoked/unreadable tree is a failure, not an empty folder: deleting its
                        // songs (and cascading to playlists) is exactly the data loss this guards.
                        val results =
                            treeUris.map { tree ->
                                tree to
                                    runCatching { listDocuments(tree) }.fold(
                                        onSuccess = { ListResult.Ok(it) },
                                        onFailure = { ListResult.Failed },
                                    )
                            }
                        val listings =
                            results.mapNotNull { (tree, result) ->
                                (result as? ListResult.Ok)?.let { tree to it.listing }
                            }
                        val anyTreeFailed = results.any { (_, result) -> result is ListResult.Failed }
                        val existing = songDao.getLocalSongs().associateBy { it.id }
                        // Never wipe a real library because a folder failed or came back empty
                        // (unmounted SD card, revoked permission, empty response).
                        if (treeUris.isNotEmpty() && listings.isEmpty()) {
                            _status.value =
                                ScanStatus.Failed(
                                    "Couldn't read any music folder. Your library was left unchanged.",
                                )
                            return@withLock existing.size
                        }
                        // Nested/overlapping folder picks list the same file twice; keep one.
                        val pairs =
                            listings
                                .flatMap { (tree, listing) -> listing.audio.map { tree to it } }
                                .distinctBy { it.second.documentId }
                        if (pairs.isEmpty() && existing.isNotEmpty()) {
                            _status.value =
                                ScanStatus.Failed("Scan found no songs. Your library was left unchanged.")
                            return@withLock existing.size
                        }
                        // Pass 1: publish discovered audio files immediately so the library updates at once.
                        val placeholders =
                            pairs.mapNotNull { (treeUri, doc) ->
                                val id = ProviderId.composite(LOCAL, doc.documentId)
                                if (existing[id] != null) return@mapNotNull null
                                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, doc.documentId)
                                fallbackEntity(doc, id, uri).copy(cachedAt = RETRY_NEXT_SCAN)
                            }
                        placeholders.chunked(DB_CHUNK).forEach { songDao.insertAllIfAbsent(it) }

                        // Pass 2: read tags & embedded artwork in parallel on a bounded IO dispatcher.
                        val done = AtomicInteger(0)

                        @OptIn(ExperimentalCoroutinesApi::class)
                        val metadataDispatcher = Dispatchers.IO.limitedParallelism(PARALLEL_READS)
                        val found: List<SongEntity> =
                            coroutineScope {
                                pairs.map { (treeUri, doc) ->
                                    async(metadataDispatcher) {
                                        currentCoroutineContext().ensureActive()
                                        val id = ProviderId.composite(LOCAL, doc.documentId)
                                        val previous = existing[id]
                                        val unchanged =
                                            !force &&
                                                previous?.cachedAt == doc.lastModified &&
                                                doc.lastModified != 0L &&
                                                hasUsableArtwork(previous)
                                        val entity =
                                            if (previous != null && unchanged) {
                                                previous
                                            } else {
                                                readTags(treeUri, doc, id, previous)
                                            }
                                        val n = done.incrementAndGet()
                                        if (n % PROGRESS_EVERY == 0 || n == pairs.size) {
                                            _status.value = ScanStatus.Scanning(n, pairs.size)
                                        }
                                        entity
                                    }
                                }.awaitAll()
                            }
                        found.chunked(DB_CHUNK).forEach { songDao.upsertAll(it) }
                        if (!anyTreeFailed) {
                            val foundIds = found.mapTo(HashSet()) { it.id }
                            // Also drops stale rows for playlist files the old scanner stored as tracks.
                            val toDelete = existing.keys.filterNot { it in foundIds }
                            toDelete.forEach { id ->
                                runCatching {
                                    val file = artworkFileForId(id)
                                    if (file.exists()) file.delete()
                                }
                            }
                            toDelete.chunked(DB_CHUNK).forEach { songDao.deleteByIds(it) }
                            // Every song now points at the new cover folder; drop the pre-2.3 copies.
                            runCatching { File(context.filesDir, ARTWORK_DIR).deleteRecursively() }
                        }
                        val keepPlaylistIds = HashSet<String>()
                        listings.forEach { (treeUri, listing) ->
                            val pathToId =
                                listing.audio.associate { doc ->
                                    doc.relativePath to ProviderId.composite(LOCAL, doc.documentId)
                                }
                            keepPlaylistIds += syncLocalPlaylists(treeUri, listing, pathToId)
                        }
                        if (!anyTreeFailed) {
                            playlistDao.getLocalFilePlaylistIds()
                                .filterNot { it in keepPlaylistIds }
                                .forEach { playlistDao.deleteById(it) }
                        }
                        _status.value = ScanStatus.Done(found.size)
                        found.size
                    } catch (e: Exception) {
                        _status.value = ScanStatus.Failed(e.message ?: "Scan failed")
                        throw e
                    }
                }
            }

        private fun listDocuments(treeUri: Uri): Listing {
            val result = mutableListOf<AudioDoc>()
            val playlists = mutableListOf<AudioDoc>()
            // (documentId, normalized directory path inside the tree)
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val pending = ArrayDeque<Pair<String, String>>().apply { add(rootId to "") }
            val projection =
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                )
            while (pending.isNotEmpty()) {
                val (parentId, parentPath) = pending.removeLast()
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val docId = c.getString(COL_ID) ?: continue
                        val name = c.getString(COL_NAME).orEmpty()
                        val mime = c.getString(COL_MIME).orEmpty()
                        val path = PlaylistFileParser.normalize(if (parentPath.isEmpty()) name else "$parentPath/$name")
                        val modified = if (c.isNull(COL_MODIFIED)) 0L else c.getLong(COL_MODIFIED)
                        when {
                            mime == DocumentsContract.Document.MIME_TYPE_DIR -> pending.add(docId to path)
                            // Checked before isAudio: M3U mime types start with "audio/".
                            PlaylistFileParser.isPlaylistFile(mime, name) ->
                                playlists += AudioDoc(docId, name, modified, path)
                            isAudio(mime, name) -> result += AudioDoc(docId, name, modified, path)
                        }
                    }
                }
            }
            return Listing(result, playlists)
        }

        /** Mirrors one folder's M3U/M3U8/PLS files as read-only `localpl:` playlists; returns their ids. */
        private suspend fun syncLocalPlaylists(
            treeUri: Uri,
            listing: Listing,
            pathToId: Map<String, String>,
        ): Set<String> {
            val nameToIds = pathToId.entries.groupBy({ it.key.substringAfterLast('/') }, { it.value })
            val keepIds = HashSet<String>()
            listing.playlists.forEach { doc ->
                val content =
                    runCatching {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, doc.documentId)
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    }.getOrNull() ?: return@forEach
                val dir = doc.relativePath.substringBeforeLast('/', "")
                val songIds =
                    PlaylistFileParser.parseEntries(doc.name, content)
                        .mapNotNull { PlaylistFileParser.resolve(it, dir, pathToId, nameToIds) }
                        .distinct()
                val id = LOCAL_PLAYLIST_PREFIX + doc.documentId
                val now = System.currentTimeMillis()
                playlistDao.replaceLocalPlaylist(
                    PlaylistEntity(
                        id = id,
                        name = doc.name.substringBeforeLast('.'),
                        description = doc.relativePath,
                        artworkUrl = null,
                        trackCount = songIds.size,
                        isOwned = false,
                        providerId = LOCAL,
                        createdAt = doc.lastModified.takeIf { it > 0 } ?: now,
                        updatedAt = now,
                    ),
                    songIds,
                )
                keepIds += id
            }
            return keepIds
        }

        /**
         * Reads [doc]'s tags and cover. If the file can't be read at all (e.g. the folder's permission
         * was just removed), the song keeps its [previous] details and is marked to be read again on the
         * next scan, instead of being saved as a bare filename with no cover.
         */
        private fun readTags(
            treeUri: Uri,
            doc: AudioDoc,
            id: String,
            previous: SongEntity?,
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
                val picture = retriever.embeddedPicture
                artworkPath = picture?.let { saveArtwork(id, it) }
                // Cover removed from the file since the last scan: drop the old extracted copy.
                if (picture == null) runCatching { artworkFileForId(id).delete() }
            } catch (e: Exception) {
                // Unreadable: keep what we had (or filename fallbacks), and retry on the next scan.
                return (previous ?: fallbackEntity(doc, id, uri)).copy(cachedAt = RETRY_NEXT_SCAN)
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

        private fun fallbackEntity(
            doc: AudioDoc,
            id: String,
            uri: Uri,
        ) = SongEntity(
            id = id,
            title = doc.name.substringBeforeLast('.'),
            artist = UNKNOWN_ARTIST,
            album = UNKNOWN_ALBUM,
            durationMs = 0L,
            artworkUrl = null,
            streamUrl = uri.toString(),
            providerId = LOCAL,
            cachedAt = doc.lastModified,
        )

        /**
         * Covers extracted from local files. Not cacheDir: Android may clear it at any time, and an
         * unchanged file is never re-read. Not filesDir: Auto Backup would upload every cover and can
         * pass its 25 MB quota, which skips the whole backup. Covers can always be re-extracted.
         */
        private fun artworkDir(): File = File(context.noBackupFilesDir, ARTWORK_DIR)

        /** True when the song has no cover, or its cover file is in [artworkDir] and still exists. */
        private fun hasUsableArtwork(song: SongEntity): Boolean {
            val file = song.artworkUrl?.let { url -> runCatching { Uri.parse(url).path }.getOrNull() }?.let(::File)
            return when {
                song.artworkUrl == null -> true
                file == null -> false
                else -> file.parentFile == artworkDir() && file.isFile
            }
        }

        private fun artworkFileForId(id: String): File {
            val dir = artworkDir().apply { mkdirs() }
            val filename = UUID.nameUUIDFromBytes(id.toByteArray()).toString() + ".img"
            return File(dir, filename)
        }

        private fun saveArtwork(
            id: String,
            bytes: ByteArray,
        ): String? =
            runCatching {
                val file = artworkFileForId(id)
                file.writeBytes(bytes)
                Uri.fromFile(file).toString()
            }.getOrNull()

        companion object {
            const val LOCAL = "local"
            const val LOCAL_PLAYLIST_PREFIX = "localpl:"
            private const val ARTWORK_DIR = "local_artwork"
            private const val UNKNOWN_ARTIST = "Unknown Artist"
            private const val UNKNOWN_ALBUM = "Unknown Album"
            private const val PROGRESS_EVERY = 10

            /** Files read at the same time. More barely helps: storage, not CPU, is the limit. */
            private const val PARALLEL_READS = 4

            /** cached_at for a file whose tags couldn't be read: never matches a real date, so it's retried. */
            private const val RETRY_NEXT_SCAN = 0L
            private const val COL_ID = 0
            private const val COL_NAME = 1
            private const val COL_MIME = 2
            private const val COL_MODIFIED = 3
            private const val DB_CHUNK = 500
            private val AUDIO_EXTENSIONS =
                setOf("flac", "mp3", "wav", "m4a", "aac", "ogg", "oga", "opus", "alac", "weba", "mka")

            /** ExoPlayer has no AIFF reader: listing these would only add songs that fail to play. */
            private val UNPLAYABLE_EXTENSIONS = setOf("aiff", "aif", "aifc")

            fun isAudio(
                mime: String,
                name: String,
            ): Boolean {
                val extension = name.substringAfterLast('.', "").lowercase()
                val unplayable = extension in UNPLAYABLE_EXTENSIONS || mime.contains("aiff")
                return !unplayable && (mime.startsWith("audio/") || extension in AUDIO_EXTENSIONS)
            }
        }
    }
