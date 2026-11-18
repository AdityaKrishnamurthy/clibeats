@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    ) : ViewModel() {
        private val _folderName = MutableStateFlow<String?>(null)
        val folderName: StateFlow<String?> = _folderName.asStateFlow()

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
