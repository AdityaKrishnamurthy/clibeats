@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ThemeState(
    val mode: CliBeatsThemeMode = CliBeatsThemeMode.DARK,
    val accent: Accent = Accent.DEFAULT,
    val palette: ThemePalette = ThemePalette.CLASSIC,
)

/**
 * Survives the Activity and keeps the resolved [ThemeState] as a [StateFlow].
 * MainActivity reads this and feeds it into [CliBeatsTheme].
 */
@HiltViewModel
class ThemeViewModel
    @Inject
    constructor(
        private val appPreferences: AppPreferences,
    ) : ViewModel() {
        /** null until loaded (avoids flashing onboarding or the app on start). */
        val onboardingDone: StateFlow<Boolean?> =
            appPreferences.onboardingDone.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        val themeState: StateFlow<ThemeState> =
            combine(
                appPreferences.themeMode,
                appPreferences.accentColorName,
                appPreferences.themePalette,
            ) { modeName, accentName, paletteName ->
                ThemeState(
                    mode =
                        CliBeatsThemeMode.entries.firstOrNull { it.name == modeName }
                            ?: CliBeatsThemeMode.DARK,
                    accent = Accent.fromStored(accentName),
                    palette = ThemePalette.fromName(paletteName),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ThemeState(),
            )
    }
