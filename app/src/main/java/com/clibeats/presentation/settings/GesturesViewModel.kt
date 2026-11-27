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
        private val playlistPicks: com.clibeats.presentation.component.PlaylistPicks,
    ) : ViewModel() {
        /** A song swiped with "Add to playlist", waiting for a playlist to be picked. */
        val playlistPick: StateFlow<com.clibeats.domain.model.Track?> = playlistPicks.requested

        fun playlistPicked() = playlistPicks.done()

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
