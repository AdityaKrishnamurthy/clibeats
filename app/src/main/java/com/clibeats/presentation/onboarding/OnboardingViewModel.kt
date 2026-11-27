@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.data.backup.LibrarySnapshotManager
import com.clibeats.data.playlist.PlaylistExchangeManager
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.data.scan.LocalMusicScanner
import com.clibeats.domain.provider.ProviderRegistry
import com.clibeats.presentation.theme.CliBeatsThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val appPreferences: AppPreferences,
        private val localMusicScanner: LocalMusicScanner,
        private val snapshotManager: LibrarySnapshotManager? = null,
        private val playlistExchangeManager: PlaylistExchangeManager? = null,
    ) : ViewModel() {
        private val _folderName = MutableStateFlow<String?>(null)
        val folderName: StateFlow<String?> = _folderName.asStateFlow()

        /** True when Android's backup brought back a snapshot from the previous install. */
        private val _canRestore = MutableStateFlow(snapshotManager?.hasSnapshot() == true)
        val canRestore: StateFlow<Boolean> = _canRestore.asStateFlow()

        private val _restoreMessage = MutableStateFlow<String?>(null)
        val restoreMessage: StateFlow<String?> = _restoreMessage.asStateFlow()

        /** Restores the snapshot Android's backup brought back. */
        fun restoreSnapshot() {
            val file = snapshotManager?.latestFile ?: return
            if (!file.isFile) {
                _restoreMessage.value = "! No backup found on this device"
                _canRestore.value = false
                return
            }
            restoreFrom(Uri.fromFile(file))
        }

        /** Restores a backup file the user picked (e.g. Download/CLIBeats/clibeats-backup-27112026.json). */
        fun restoreFrom(source: Uri) {
            val exchange = playlistExchangeManager ?: return
            viewModelScope.launch {
                _restoreMessage.value =
                    exchange.import(source).fold(
                        onSuccess = { count ->
                            _canRestore.value = false
                            "[ OK ] Restored $count track(s) from backup"
                        },
                        onFailure = { error -> "! Restore failed: ${error.message}" },
                    )
            }
        }

        fun setTheme(mode: CliBeatsThemeMode) {
            viewModelScope.launch { appPreferences.setThemeMode(mode.name) }
        }

        /** Permission is persisted by the screen; save the folder and start scanning in the background. */
        fun onFolderPicked(uri: Uri) {
            _folderName.value = uri.lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: "Selected folder"
            viewModelScope.launch {
                appPreferences.addLocalMusicTreeUri(uri.toString())
                runCatching { localMusicScanner.scan(appPreferences.localMusicTreeUris.first().map(Uri::parse)) }
            }
        }

        fun finish(useLocal: Boolean) {
            viewModelScope.launch {
                appPreferences.setActiveProviderId(if (useLocal) "local" else ProviderRegistry.DEFAULT_PROVIDER_ID)
                appPreferences.setOnboardingDone()
            }
        }
    }
