package com.clibeats.data.backup

import android.content.Context
import com.clibeats.data.playlist.PlaylistExchangeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps a rolling `clibeats-backup.json` of the whole library in a folder the user can reach, so a
 * reinstall (or a signing-key change that forces an uninstall) no longer means losing everything.
 *
 * A snapshot is written at most once a day and never when the library is empty, so a wipe or a
 * failed scan cannot overwrite the last good backup. The previous file is kept as `.prev.json`.
 */
@Singleton
class LibrarySnapshotManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val exchange: PlaylistExchangeManager,
    ) {
        private val mutex = Mutex()

        /** `Android/data/com.clibeats/files/backups` (or internal files when no external storage). */
        val folder: File
            get() = (context.getExternalFilesDir(null) ?: context.filesDir).resolve(FOLDER_NAME)

        val latestFile: File
            get() = File(folder, PlaylistExchangeManager.BACKUP_FILE_NAME)

        private val previousFile: File
            get() = File(folder, "clibeats-backup.prev.json")

        fun hasSnapshot(): Boolean = latestFile.isFile && latestFile.length() > 0L

        /** Writes a snapshot when the newest one is older than a day (or [force] is set). */
        suspend fun snapshotIfDue(force: Boolean = false): Result<File?> =
            mutex.withLock {
                runCatching {
                    val latest = latestFile
                    val fresh =
                        !force &&
                            latest.isFile &&
                            System.currentTimeMillis() - latest.lastModified() < SNAPSHOT_INTERVAL_MS
                    if (fresh) return@runCatching null

                    val file = exchange.buildFile()
                    val empty =
                        file.playlists.isEmpty() && file.albums.isEmpty() && file.artists.isEmpty()
                    // Never replace a good backup with an empty one (first run, wiped library).
                    if (empty && hasSnapshot()) return@runCatching null

                    if (latest.isFile) {
                        withContext(Dispatchers.IO) { latest.copyTo(previousFile, overwrite = true) }
                    }
                    exchange.exportToFile(latest).getOrThrow()
                    latest
                }
            }

        /**
         * Copies the latest snapshot into the cache so a `FileProvider` URI can be shared through
         * the system share sheet. Returns null when there is nothing to share yet.
         */
        suspend fun prepareShareFile(): File? =
            withContext(Dispatchers.IO) {
                val latest = latestFile.takeIf { it.isFile } ?: return@withContext null
                val shared = context.cacheDir.resolve(SHARED_FOLDER).apply { mkdirs() }
                val target = File(shared, PlaylistExchangeManager.BACKUP_FILE_NAME)
                latest.copyTo(target, overwrite = true)
                target
            }

        companion object {
            private const val FOLDER_NAME = "backups"
            private const val SHARED_FOLDER = "shared"
            private const val SNAPSHOT_INTERVAL_MS = 24 * 60 * 60 * 1000L
        }
    }
