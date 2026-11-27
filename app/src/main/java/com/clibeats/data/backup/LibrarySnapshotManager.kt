@file:Suppress("ForbiddenImport")

package com.clibeats.data.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.clibeats.data.playlist.PlaylistExchangeManager
import com.clibeats.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps a rolling `clibeats-backup.json` of the whole library, so a reinstall (or a signing-key change
 * that forces an uninstall) no longer means losing everything. Two copies:
 *
 * - `files/backups/` inside the app: part of Android's own backup, so it comes back with a restore.
 * - `Download/CLIBeats/clibeats-backup-DDMMYYYY.json` on Android 10+: survives an uninstall. A new install can't
 *   read it directly (it doesn't own the file any more), so onboarding opens the file picker on it.
 *
 * A snapshot is written at most once a day and never for an empty library, so a wipe or a failed scan
 * can't overwrite a good backup. Each day's backup replaces the one before: there is only ever one file.
 */
@Singleton
class LibrarySnapshotManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val exchange: PlaylistExchangeManager,
        private val appPreferences: AppPreferences,
    ) {
        private val mutex = Mutex()

        private val folder: File
            get() = context.filesDir.resolve(FOLDER_NAME)

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
                    // Never write an empty library (first run, wiped library): it would replace a good backup.
                    val nothing =
                        file.playlists.isEmpty() && file.albums.isEmpty() && file.artists.isEmpty() &&
                            file.history.isEmpty() && file.downloads.isEmpty()
                    if (nothing) return@runCatching null
                    // One backup only: today's replaces yesterday's (an older spare copy is removed).
                    withContext(Dispatchers.IO) { if (previousFile.isFile) previousFile.delete() }
                    exchange.exportToFile(latest).getOrThrow()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        appPreferences.mirrorBackupsToDownloads.first()
                    ) {
                        // Best effort: the internal copy is already safe.
                        runCatching { writePublicCopy(latest) }
                    }
                    latest
                }
            }

        /**
         * Copies [source] to `Download/CLIBeats/clibeats-backup-DDMMYYYY.json` (today's date), first removing this
         * install's earlier copies there, so the folder only ever holds the latest backup. A copy left by a previous
         * install isn't visible to this one (Android keeps it), so that one stays and is still restorable.
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        private suspend fun writePublicCopy(source: File) =
            withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                deletePublicCopies()
                val target =
                    resolver.insert(
                        collection,
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, datedBackupName())
                            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, PUBLIC_RELATIVE_PATH)
                        },
                    ) ?: error("Couldn't create the backup in Downloads")
                resolver.openOutputStream(target, "wt")?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                } ?: error("Couldn't write the backup in Downloads")
            }

        /**
         * Deletes the stored backups: the copies inside the app and the ones this install saved in Downloads
         * (the current folder and the older `Download/CLIBeats/`). Copies left by an earlier install belong to
         * it, so Android only lets a file manager delete those. Returns how many files were removed.
         */
        suspend fun clearBackups(): Int =
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    var removed = listOf(latestFile, previousFile).count { it.isFile && it.delete() }
                    context.cacheDir.resolve(SHARED_FOLDER).deleteRecursively()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) removed += deletePublicCopies()
                    removed
                }
            }

        @RequiresApi(Build.VERSION_CODES.Q)
        private fun deletePublicCopies(): Int {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val ids =
                resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.RELATIVE_PATH} IN (?, ?) AND " +
                        "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                    arrayOf(PUBLIC_RELATIVE_PATH, OLD_PUBLIC_RELATIVE_PATH, "clibeats-backup%"),
                    null,
                )?.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }.orEmpty()
            return ids.count { id ->
                runCatching { resolver.delete(ContentUris.withAppendedId(collection, id), null, null) > 0 }
                    .getOrDefault(false)
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
                shared.listFiles()?.forEach { it.delete() }
                val target = File(shared, datedBackupName())
                latest.copyTo(target, overwrite = true)
                target
            }

        companion object {
            private const val FOLDER_NAME = "backups"
            private const val SHARED_FOLDER = "shared"
            private const val SNAPSHOT_INTERVAL_MS = 24 * 60 * 60 * 1000L

            /** Where the uninstall-proof copy goes, as MediaStore stores it (trailing slash included). */
            val PUBLIC_RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/CLIBeats/"

            /** Where versions 2.5.2 to 2.8.1 put it; cleared along with the current folder. */
            private val OLD_PUBLIC_RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/CLIBeats/Playlists/"

            /** `clibeats-backup-27112026.json`: today's date as day, month, year. */
            fun datedBackupName(now: java.util.Date = java.util.Date()): String =
                "clibeats-backup-" + java.text.SimpleDateFormat("ddMMyyyy", java.util.Locale.US).format(now) + ".json"
        }
    }
