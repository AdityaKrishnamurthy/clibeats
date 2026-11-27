@file:Suppress("ForbiddenImport")

package com.clibeats.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.domain.model.EQUALIZER_BANDS_HZ
import com.clibeats.domain.model.EqualizerPreset
import com.clibeats.domain.model.EqualizerState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * The in-app 10-band equalizer.
 *
 * Android 9+ uses [DynamicsProcessing] (EQ plus a limiter so boosted bass does not clip). Android 8.x
 * falls back to the platform [Equalizer], mapping the nearest of the ten bands onto the device's
 * hardware bands. Persistence lives in [AppPreferences]; this class only turns the state into audio
 * effects and exposes the system EQ panel intent.
 */
@Singleton
class EqualizerController
    @Inject
    constructor(
        private val player: ExoPlayer,
        private val preferences: AppPreferences,
        @ApplicationContext private val context: Context,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val _state = MutableStateFlow(EqualizerState())
        val state: StateFlow<EqualizerState> = _state.asStateFlow()

        private var dynamics: DynamicsProcessing? = null
        private var legacy: Equalizer? = null
        private var sessionId = 0

        init {
            player.addListener(
                object : Player.Listener {
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        sessionId = audioSessionId
                        apply(_state.value)
                    }
                },
            )
            sessionId = player.audioSessionId
            scope.launch {
                combine(
                    preferences.equalizerEnabled,
                    preferences.equalizerPreset,
                    preferences.equalizerGainsDb,
                    preferences.equalizerPreampDb,
                ) { enabled, preset, gains, preamp ->
                    EqualizerState(enabled = enabled, preset = preset, gainsDb = gains, preampDb = preamp)
                }.distinctUntilChanged()
                    .collect { next ->
                        _state.value = next
                        apply(next)
                    }
            }
        }

        fun setEnabled(enabled: Boolean) {
            scope.launch { preferences.setEqualizerEnabled(enabled) }
        }

        fun setPreset(preset: EqualizerPreset) {
            scope.launch { preferences.setEqualizerPreset(preset) }
        }

        fun setBand(
            index: Int,
            gainDb: Float,
        ) {
            scope.launch { preferences.setEqualizerBand(index, gainDb) }
        }

        fun setPreamp(gainDb: Float) {
            scope.launch { preferences.setEqualizerPreamp(gainDb) }
        }

        /** Opens the phone's own audio-effects panel (Wavelet, OEM EQ, …) for the current session. */
        fun systemPanelIntent(): Intent? {
            if (sessionId == 0) return null
            return Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            }
        }

        private fun apply(state: EqualizerState) {
            releaseEffects(closeSession = false)
            if (!state.enabled) {
                closeSessionBroadcast()
                return
            }
            val id = sessionId
            if (id <= 0) return

            openSessionBroadcast(id)
            val attached =
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        attachDynamics(id, state)
                    } else {
                        attachLegacy(id, state)
                    }
                }.isSuccess
            if (!attached) {
                runCatching { attachLegacy(id, state) }
                    .onFailure { releaseEffects(closeSession = true) }
            }
        }

        private fun attachDynamics(
            id: Int,
            state: EqualizerState,
        ) {
            val bandCount = EQUALIZER_BANDS_HZ.size
            val config =
                DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    CHANNELS,
                    true,
                    bandCount,
                    false,
                    0,
                    false,
                    0,
                    true,
                ).build()
            val effect = DynamicsProcessing(0, id, config)
            EQUALIZER_BANDS_HZ.forEachIndexed { index, frequency ->
                effect.setPreEqBandAllChannelsTo(
                    index,
                    DynamicsProcessing.EqBand(true, frequency.toFloat(), state.gainsDb[index]),
                )
            }
            effect.setInputGainAllChannelsTo(state.preampDb)
            effect.setLimiterAllChannelsTo(
                DynamicsProcessing.Limiter(
                    true,
                    true,
                    0,
                    LIMITER_ATTACK_MS,
                    LIMITER_RELEASE_MS,
                    LIMITER_RATIO,
                    LIMITER_THRESHOLD_DB,
                    0f,
                ),
            )
            effect.enabled = true
            dynamics = effect
        }

        private fun attachLegacy(
            id: Int,
            state: EqualizerState,
        ) {
            val effect = Equalizer(0, id)
            val range = effect.bandLevelRange
            if (range == null || range.size < 2 || effect.numberOfBands <= 0) {
                effect.release()
                return
            }
            for (band in 0 until effect.numberOfBands) {
                val centerHz = effect.getCenterFreq(band.toShort()) / 1_000
                val index = EQUALIZER_BANDS_HZ.indices.minByOrNull { abs(EQUALIZER_BANDS_HZ[it] - centerHz) } ?: 0
                val millibels = (state.gainsDb[index] * MILLIBELS_PER_DB).toInt()
                effect.setBandLevel(band.toShort(), millibels.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            }
            effect.enabled = true
            legacy = effect
        }

        private fun releaseEffects(closeSession: Boolean) {
            runCatching { dynamics?.enabled = false }
            runCatching { dynamics?.release() }
            dynamics = null
            runCatching { legacy?.enabled = false }
            runCatching { legacy?.release() }
            legacy = null
            if (closeSession) closeSessionBroadcast()
        }

        private fun openSessionBroadcast(id: Int) {
            context.sendBroadcast(
                Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, id)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                },
            )
        }

        private fun closeSessionBroadcast() {
            if (sessionId <= 0) return
            context.sendBroadcast(
                Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                },
            )
        }

        private companion object {
            const val CHANNELS = 2
            const val MILLIBELS_PER_DB = 100
            const val LIMITER_ATTACK_MS = 1f
            const val LIMITER_RELEASE_MS = 60f
            const val LIMITER_RATIO = 10f
            const val LIMITER_THRESHOLD_DB = -0.5f
        }
    }
