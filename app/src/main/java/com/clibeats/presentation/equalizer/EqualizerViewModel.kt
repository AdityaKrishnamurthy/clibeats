package com.clibeats.presentation.equalizer

import android.content.Context
import androidx.lifecycle.ViewModel
import com.clibeats.domain.model.EqualizerPreset
import com.clibeats.domain.model.EqualizerState
import com.clibeats.playback.EqualizerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel
    @Inject
    constructor(
        private val controller: EqualizerController,
    ) : ViewModel() {
        val state: StateFlow<EqualizerState> = controller.state

        fun setEnabled(enabled: Boolean) = controller.setEnabled(enabled)

        fun setPreset(preset: EqualizerPreset) = controller.setPreset(preset)

        fun stepBand(
            index: Int,
            deltaDb: Float,
        ) {
            controller.setBand(index, state.value.gainsDb.getOrNull(index)?.plus(deltaDb) ?: deltaDb)
        }

        fun stepPreamp(deltaDb: Float) {
            controller.setPreamp(state.value.preampDb + deltaDb)
        }

        fun reset() {
            controller.setPreset(EqualizerPreset.FLAT)
            controller.setPreamp(0f)
        }

        fun openSystemPanel(context: Context): Boolean {
            val intent = controller.systemPanelIntent() ?: return false
            return runCatching { context.startActivity(intent) }.isSuccess
        }
    }
