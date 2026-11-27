package com.clibeats.presentation.settings

import com.clibeats.presentation.theme.Accent
import com.clibeats.presentation.theme.CliBeatsThemeMode

data class SettingsUiState(
    val cacheMaxMb: Int = 1024,
    val highQualityStreaming: Boolean = true,
    val mirrorBackupsToDownloads: Boolean = true,
    val exchangeMessage: String? = null,
    // Theme engine
    val themeMode: CliBeatsThemeMode = CliBeatsThemeMode.DARK,
    val accentColor: Accent = Accent.DEFAULT,
)
