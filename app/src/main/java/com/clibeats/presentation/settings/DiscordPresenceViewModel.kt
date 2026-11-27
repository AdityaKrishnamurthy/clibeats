@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.settings

import androidx.lifecycle.ViewModel
import com.clibeats.data.discord.DiscordPresenceManager
import com.clibeats.data.discord.DiscordPresenceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class DiscordPresenceViewModel
    @Inject
    constructor(
        private val manager: DiscordPresenceManager,
    ) : ViewModel() {
        val state: StateFlow<DiscordPresenceState> = manager.state

        fun setEnabled(enabled: Boolean) = manager.setEnabled(enabled)
    }
