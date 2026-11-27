@file:Suppress("ForbiddenImport")

package com.clibeats.data.discord

import com.clibeats.data.preferences.AppPreferences
import com.clibeats.domain.repository.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the current queue/playback into a Discord activity and hands it to the SDK gateway.
 *
 * Off by default. Building and previewing the card works without the SDK, but [publish] only does
 * something when an official Social SDK bridge is present. No Discord credentials or user tokens
 * are involved anywhere in this class.
 */
@Singleton
class DiscordPresenceManager
    @Inject
    constructor(
        private val preferences: AppPreferences,
        playbackRepository: PlaybackRepository,
        private val gateway: DiscordPresenceGateway,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val state: StateFlow<DiscordPresenceState> =
            combine(
                preferences.discordPresenceEnabled,
                playbackRepository.playbackState,
            ) { enabled, playback ->
                DiscordPresenceState(
                    enabled = enabled,
                    sdkStatus = gateway.sdkStatus,
                    activity = playback.toDiscordActivity(),
                )
            }.distinctUntilChanged()
                .stateIn(
                    scope,
                    SharingStarted.Eagerly,
                    DiscordPresenceState(sdkStatus = gateway.sdkStatus),
                )

        init {
            scope.launch {
                state.collect { presence ->
                    gateway.publish(if (presence.enabled) presence.activity else null)
                }
            }
        }

        fun setEnabled(enabled: Boolean) {
            scope.launch { preferences.setDiscordPresenceEnabled(enabled) }
        }
    }
