// ForbiddenImport: data-layer self-imports are legitimate
@file:Suppress("ForbiddenImport")

package com.clibeats.data.download

import android.content.Context
import com.clibeats.data.local.dao.DownloadedSongItem
import com.clibeats.data.local.dao.DownloadedTrackDao
import com.clibeats.data.local.dao.SongDao
import com.clibeats.data.local.entity.DownloadedTrackEntity
import com.clibeats.data.local.mapper.toDomain
import com.clibeats.data.local.mapper.toEntity
import com.clibeats.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns durable download storage paths, Room records, and cleanup for offline tracks.
 */
@Suppress("TooManyFunctions")
@Singleton
class DownloadStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val downloadedTrackDao: DownloadedTrackDao,
        private val songDao: SongDao,
    ) {
        val downloadsDir: File by lazy {
            File(context.filesDir, "downloads").apply {
                if (!exists()) mkdirs()
            }
        }

        fun hashFor(trackId: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(trackId.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun partFile(hash: String): File = File(downloadsDir, "$hash.part")

        fun chunksFile(hash: String): File = File(downloadsDir, "$hash.chunks")

        suspend fun isTrackDownloaded(songId: String): Boolean =
            withContext(Dispatchers.IO) {
                val record = downloadedTrackDao.getById(songId) ?: return@withContext false
                File(record.filePath).exists()
            }

        suspend fun existingDownload(songId: String): File? {
            val record = downloadedTrackDao.getById(songId)
            val file = record?.let { File(it.filePath) }
            return if (file?.exists() == true) {
                file
            } else {
                if (record != null) downloadedTrackDao.deleteById(songId)
                null
            }
        }

        fun getDownloadedTracksFlow(): Flow<List<DownloadedTrackEntity>> = downloadedTrackDao.getAllFlow()

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

        fun getTotalDownloadedSizeBytes(): Flow<Long> =
            downloadedTrackDao.getAllFlow()
                .map { list -> list.sumOf { File(it.filePath).takeIf(File::exists)?.length() ?: 0L } }
                .flowOn(Dispatchers.IO)

        suspend fun saveCompleted(
            track: Track,
            hash: String,
            partFile: File,
            format: DownloadFormat,
        ): File {
            chunksFile(hash).delete()
            val finalFile = File(downloadsDir, "$hash.${format.extension}")
            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.delete()
                throw IOException("Failed to finalize download file")
            }
            runCatching {
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
            }.onFailure {
                finalFile.delete()
            }.getOrThrow()
            return finalFile
        }

        fun deletePartialFiles(hash: String) {
            partFile(hash).delete()
            chunksFile(hash).delete()
        }

        suspend fun deleteTrackRecordAndFiles(trackId: String) {
            val entity = downloadedTrackDao.getById(trackId)
            downloadedTrackDao.deleteById(trackId)
            entity?.let { File(it.filePath).delete() }
            val hash = hashFor(trackId)
            downloadsDir.listFiles { _, name -> name.startsWith(hash) }?.forEach { it.delete() }
        }

        suspend fun deleteAllRecordsAndFiles() {
            downloadedTrackDao.deleteAll()
            downloadsDir.listFiles()?.forEach { it.delete() }
        }

        suspend fun removeLeftovers() {
            runCatching {
                val kept = downloadedTrackDao.getAll().map { File(it.filePath).absolutePath }.toSet()
                val cutoff = System.currentTimeMillis() - LEFTOVER_AGE_MS
                downloadsDir.listFiles()?.forEach { file ->
                    if (file.absolutePath !in kept && file.lastModified() < cutoff) file.delete()
                }
            }
        }

        companion object {
            private const val LEFTOVER_AGE_MS = 60_000L
        }
    }
