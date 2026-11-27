@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.domain.model.GestureSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Settings → Gestures: which swipes are on, shared by the whole app. */
@HiltViewModel
class GesturesViewModel
    @Inject
    constructor(
        private val appPreferences: AppPreferences,
    ) : ViewModel() {
        val gestures: StateFlow<GestureSettings> =
            appPreferences.gestures.stateIn(viewModelScope, SharingStarted.Eagerly, GestureSettings())

        fun update(change: (GestureSettings) -> GestureSettings) {
            viewModelScope.launch { appPreferences.setGestures(change(gestures.value)) }
        }

        /** Back to the default gestures (all on). */
        fun reset() {
            viewModelScope.launch { appPreferences.setGestures(GestureSettings()) }
        }
    }
