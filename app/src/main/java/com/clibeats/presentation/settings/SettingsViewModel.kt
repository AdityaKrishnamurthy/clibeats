@file:Suppress("ForbiddenImport", "TooManyFunctions", "LongParameterList")

package com.clibeats.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.data.backup.LibrarySnapshotManager
import com.clibeats.data.cache.CacheUsage
import com.clibeats.data.cache.MediaCaches
import com.clibeats.data.download.TrackDownloadManager
import com.clibeats.data.local.dao.DownloadedSongItem
import com.clibeats.data.playlist.PlaylistExchangeManager
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.data.scan.LocalMusicScanner
import com.clibeats.data.scan.ScanStatus
import com.clibeats.domain.repository.PlaybackRepository
import com.clibeats.domain.repository.SongRepository
import com.clibeats.presentation.theme.Accent
import com.clibeats.presentation.theme.AccentColor
import com.clibeats.presentation.theme.CliBeatsThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val appPreferences: AppPreferences,
        private val playlistExchangeManager: PlaylistExchangeManager,
        private val songRepository: SongRepository,
        private val snapshotManager: LibrarySnapshotManager? = null,
        private val localMusicScanner: LocalMusicScanner? = null,
        private val mediaCaches: MediaCaches? = null,
        private val playbackRepository: PlaybackRepository? = null,
        private val downloadManager: TrackDownloadManager? = null,
        private val onlineLyrics: com.clibeats.data.lyrics.online.OnlineLyricsRepository? = null,
    ) : ViewModel() {
        /** Saved lyrics: songs, bytes, and the most kept (downloaded songs always keep theirs). */
        val lyricsCount: StateFlow<Int> =
            (onlineLyrics?.savedCount ?: flowOf(0)).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)
        val lyricsBytes: StateFlow<Long> =
            (onlineLyrics?.savedBytes ?: flowOf(0L))
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0L)
        val lyricsLimit: Int get() = onlineLyrics?.savedLimit ?: 0

        fun clearLyrics() {
            viewModelScope.launch { onlineLyrics?.clearSaved() }
        }

        val cacheUsage: StateFlow<CacheUsage> = mediaCaches?.usage ?: MutableStateFlow(CacheUsage())

        val downloadedSongItems: StateFlow<List<DownloadedSongItem>> =
            (downloadManager?.getDownloadedSongItems() ?: flowOf(emptyList()))
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

        val downloadedBytes: StateFlow<Long> =
            (downloadManager?.getTotalDownloadedSizeBytes() ?: flowOf(0L))
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0L)

        fun deleteDownload(songId: String) {
            viewModelScope.launch {
                downloadManager?.deleteDownload(songId)
            }
        }

        fun clearAllDownloads() {
            viewModelScope.launch {
                downloadManager?.deleteAllDownloads()
            }
        }

        val artworkCacheMb: StateFlow<Int> =
            appPreferences.artworkCacheMb.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 128)

        init {
            viewModelScope.launch { runCatching { mediaCaches?.refreshUsage() } }
        }

        fun setSongCacheMb(mb: Int) {
            viewModelScope.launch {
                appPreferences.setCacheMaxMb(mb)
                runCatching { mediaCaches?.setSongLimitMb(mb) }
            }
        }

        fun setArtworkCacheMb(mb: Int) {
            viewModelScope.launch {
                appPreferences.setArtworkCacheMb(mb)
                runCatching { mediaCaches?.setArtworkLimitMb(mb) }
            }
        }

        fun clearSongCache() {
            viewModelScope.launch {
                runCatching { mediaCaches?.clearSongs() }
            }
        }

        fun clearArtworkCache() {
            viewModelScope.launch { runCatching { mediaCaches?.clearArtwork() } }
        }

        val autoQueueRelated: StateFlow<Boolean> =
            appPreferences.autoQueueRelated
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)

        val localMusicFolders: StateFlow<List<String>> =
            appPreferences.localMusicTreeUris
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

        val scanStatus: StateFlow<ScanStatus> = localMusicScanner?.status ?: MutableStateFlow(ScanStatus.Idle)

        /** Songs in the local library, read from the database so it's right after a restart too. */
        val localTrackCount: StateFlow<Int> =
            songRepository.getLocalTrackCountAsFlow()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)

        fun setAutoQueueRelated(enabled: Boolean) {
            viewModelScope.launch { appPreferences.setAutoQueueRelated(enabled) }
        }

        /** Adds a picked folder (permission already persisted by the screen) and rescans all folders. */
        fun onLocalFolderPicked(treeUri: Uri) {
            viewModelScope.launch {
                appPreferences.addLocalMusicTreeUri(treeUri.toString())
                scanAllFolders()
            }
        }

        /** Removes a folder (the screen releases its permission); its songs disappear on the rescan. */
        fun removeLocalFolder(uri: String) {
            viewModelScope.launch {
                appPreferences.removeLocalMusicTreeUri(uri)
                scanAllFolders()
            }
        }

        fun rescanLocalMusic() {
            // The Rescan button re-reads every file, so tag and cover edits always show up.
            viewModelScope.launch { scanAllFolders(force = true) }
        }

        private suspend fun scanAllFolders(force: Boolean = false) {
            val folders = appPreferences.localMusicTreeUris.first().map(Uri::parse)
            runCatching { localMusicScanner?.scan(folders, force = force) }
            // Songs already in the queue still hold their old details; show the rescanned ones.
            runCatching { playbackRepository?.refreshLocalTracks() }
        }

        val uiState: StateFlow<SettingsUiState> =
            combine(
                appPreferences.cacheMaxMb,
                appPreferences.highQualityStreaming,
                appPreferences.mirrorBackupsToDownloads,
                appPreferences.themeMode,
                appPreferences.accentColorName,
            ) { cacheMaxMb, highQuality, mirrorBackups, modeName, accentName ->
                SettingsUiState(
                    cacheMaxMb = cacheMaxMb,
                    highQualityStreaming = highQuality,
                    mirrorBackupsToDownloads = mirrorBackups,
                    themeMode =
                        CliBeatsThemeMode.entries.firstOrNull { it.name == modeName }
                            ?: CliBeatsThemeMode.DARK,
                    accentColor = Accent.fromStored(accentName),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = SettingsUiState(),
            )

        private val _exchangeMessage = MutableStateFlow<String?>(null)
        val exchangeMessage: StateFlow<String?> = _exchangeMessage.asStateFlow()

        fun setMirrorBackupsToDownloads(enabled: Boolean) {
            viewModelScope.launch {
                appPreferences.setMirrorBackupsToDownloads(enabled)
            }
        }

        fun setHighQualityStreaming(enabled: Boolean) {
            viewModelScope.launch {
                appPreferences.setHighQualityStreaming(enabled)
            }
        }

        /** Settings → Theme: the palette in use. */
        val themePalette: StateFlow<com.clibeats.presentation.theme.ThemePalette> =
            appPreferences.themePalette
                .map { com.clibeats.presentation.theme.ThemePalette.fromName(it) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, com.clibeats.presentation.theme.ThemePalette.CLASSIC)

        /**
         * Picks a theme; its colour becomes the accent (Classic goes back to silver-white). The accent can
         * still be changed below afterwards.
         */
        fun setThemePalette(palette: com.clibeats.presentation.theme.ThemePalette) {
            viewModelScope.launch {
                appPreferences.setThemePalette(palette.name)
                val accent =
                    if (palette == com.clibeats.presentation.theme.ThemePalette.CLASSIC) {
                        AccentColor.WHITE.name
                    } else {
                        Accent.toHex(palette.swatch)
                    }
                appPreferences.setAccentColorName(accent)
            }
        }

        fun setThemeMode(mode: CliBeatsThemeMode) {
            viewModelScope.launch {
                appPreferences.setThemeMode(mode.name)
            }
        }

        fun setAccentColor(accent: AccentColor) {
            viewModelScope.launch {
                appPreferences.setAccentColorName(accent.name)
            }
        }

        /** A colour from the custom picker, saved as `#RRGGBB`. */
        fun setCustomAccent(color: androidx.compose.ui.graphics.Color) {
            viewModelScope.launch {
                appPreferences.setAccentColorName(Accent.toHex(color))
            }
        }

        fun exportPlaylists(target: Uri) {
            viewModelScope.launch {
                _exchangeMessage.value =
                    playlistExchangeManager.export(target).fold(
                        onSuccess = { count -> "Exported $count playlist(s)" },
                        onFailure = { e -> "Export failed: ${e.message}" },
                    )
            }
        }

        fun importPlaylists(source: Uri) {
            viewModelScope.launch {
                _exchangeMessage.value =
                    playlistExchangeManager.import(source).fold(
                        onSuccess = { count -> "Imported $count track(s)" },
                        onFailure = { e -> "Import failed: ${e.message}" },
                    )
            }
        }

        fun clearExchangeMessage() {
            _exchangeMessage.value = null
        }

        /** File handed to the share sheet; cleared once the screen has launched the chooser. */
        private val _shareFile = MutableStateFlow<File?>(null)
        val shareFile: StateFlow<File?> = _shareFile.asStateFlow()

        private val _lastBackupAt = MutableStateFlow<Long?>(null)

        /** When the automatic backup was last saved (null: none yet). */
        val lastBackupAt: StateFlow<Long?> = _lastBackupAt.asStateFlow()

        fun refreshLastBackup() {
            val manager = snapshotManager ?: return
            viewModelScope.launch {
                _lastBackupAt.value =
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        manager.latestFile.takeIf { it.isFile && it.length() > 0L }?.lastModified()
                    }
            }
        }

        /** Saves a fresh automatic backup now (it replaces the previous one). */
        fun backUpNow() {
            val manager = snapshotManager ?: return
            viewModelScope.launch {
                val saved = manager.snapshotIfDue(force = true).getOrNull()
                _exchangeMessage.value = if (saved != null) "Backup saved" else "Nothing to back up yet"
                refreshLastBackup()
            }
        }

        /** Takes a fresh snapshot and exposes it for ACTION_SEND (Drive, email, nearby, ...). */
        fun shareSnapshot() {
            val manager = snapshotManager ?: return
            viewModelScope.launch {
                manager.snapshotIfDue(force = true)
                val shared = manager.prepareShareFile()
                _shareFile.value = shared
                if (shared == null) {
                    _exchangeMessage.value =
                        "Nothing to share yet: save a song or create a playlist first"
                }
            }
        }

        /** Deletes every stored backup (inside the app and in Downloads); a new one is saved on the next start. */
        fun clearBackups() {
            val manager = snapshotManager ?: return
            viewModelScope.launch {
                val removed = manager.clearBackups()
                refreshLastBackup()
                _exchangeMessage.value =
                    when (removed) {
                        0 -> "No backups to delete"
                        1 -> "Deleted 1 backup"
                        else -> "Deleted $removed backups"
                    }
            }
        }

        fun clearShareFile() {
            _shareFile.value = null
        }
    }
