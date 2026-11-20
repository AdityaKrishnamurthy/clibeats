@file:Suppress("ForbiddenImport")

package com.clibeats.playback

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.MediaRouter
import android.media.MediaRouter2
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.clibeats.domain.model.AudioOutput
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks where media audio is actually playing (phone speaker, wired headphones, a Bluetooth device…).
 *
 * On Android 13+ it reads the device this app's own playback is routed to (or, while paused, the device media
 * would go to), so a connected-but-unused headset isn't shown. Older versions use the system media route.
 *
 * Android reports a new device a moment before it moves audio onto it, so after a device connects or
 * disconnects the output is re-checked a few times over ~3 s; playback route changes are followed directly.
 * Everything is driven by system callbacks: nothing polls, so it costs no battery.
 */
@Singleton
class AudioOutputMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val audioManager = context.getSystemService(AudioManager::class.java)
        private val mediaRouter = context.getSystemService(MediaRouter::class.java)
        private val main = Handler(Looper.getMainLooper())
        private val _current = MutableStateFlow(AudioOutput.PHONE_SPEAKER)
        val current: StateFlow<AudioOutput> = _current.asStateFlow()

        private val settleCheck = Runnable { refresh() }

        init {
            // Callbacks are registered and delivered on the main thread.
            main.post {
                runCatching {
                    audioManager?.registerAudioDeviceCallback(
                        object : AudioDeviceCallback() {
                            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = onDevicesChanged()

                            override fun onAudioDevicesRemoved(gone: Array<out AudioDeviceInfo>?) = onDevicesChanged()
                        },
                        main,
                    )
                }
                runCatching {
                    // Fires when this app's playback starts, stops or is moved to another device.
                    audioManager?.registerAudioPlaybackCallback(
                        object : AudioManager.AudioPlaybackCallback() {
                            override fun onPlaybackConfigChanged(c: List<AudioPlaybackConfiguration>?) = refresh()
                        },
                        main,
                    )
                }
                runCatching {
                    mediaRouter?.addCallback(
                        MediaRouter.ROUTE_TYPE_LIVE_AUDIO,
                        object : MediaRouter.SimpleCallback() {
                            override fun onRouteSelected(
                                router: MediaRouter?,
                                type: Int,
                                info: MediaRouter.RouteInfo?,
                            ) = refresh()

                            override fun onRouteChanged(
                                router: MediaRouter?,
                                info: MediaRouter.RouteInfo?,
                            ) = refresh()
                        },
                    )
                }
                refresh()
            }
        }

        /** A device connected or went away: check now, then again while Android moves the audio over. */
        private fun onDevicesChanged() {
            refresh()
            main.removeCallbacks(settleCheck)
            SETTLE_DELAYS_MS.forEach { main.postDelayed(settleCheck, it) }
        }

        /** Re-reads the output (also called when Now Playing comes back from the system switcher). */
        fun refresh() {
            _current.value = runCatching { detect() }.getOrNull() ?: AudioOutput.PHONE_SPEAKER
        }

        private fun detect(): AudioOutput? {
            val am = audioManager ?: return null
            val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            return if (modern) detectFromAttributes(am) else detectFromRoute(am)
        }

        /** Android 13+: where this app's playback is going, or where media would go while nothing plays. */
        @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun detectFromAttributes(am: AudioManager): AudioOutput? {
            val playing = am.activePlaybackConfigurations.firstNotNullOfOrNull { it.audioDeviceInfo }
            val device =
                playing ?: run {
                    val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
                    am.getAudioDevicesForAttributes(media).firstOrNull()
                }
            return device?.let { audioOutputFor(it.type, it.productName?.toString()) }
        }

        /** Older Android: the selected system route, or plugged-in headphones on the phone's own route. */
        private fun detectFromRoute(am: AudioManager): AudioOutput {
            val route = mediaRouter?.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)
            val wired = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in WIRED_TYPES }
            return when {
                route?.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH ->
                    audioOutputFor(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, route.getName(context)?.toString())
                wired != null -> audioOutputFor(wired.type, wired.productName?.toString())
                else -> AudioOutput.PHONE_SPEAKER
            }
        }

        /**
         * Opens Android's own output picker for this app: the system switcher on Android 14+, the media
         * output panel on 11–13, Bluetooth settings otherwise.
         */
        fun openSwitcher(activityContext: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val shown = runCatching { MediaRouter2.getInstance(activityContext).showSystemOutputSwitcher() }
                if (shown.getOrDefault(false)) return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val panel =
                    Intent(MEDIA_OUTPUT_PANEL)
                        .putExtra(EXTRA_PACKAGE_NAME, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { activityContext.startActivity(panel) }.isSuccess) return
            }
            runCatching {
                activityContext.startActivity(
                    Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }

        private companion object {
            /** Re-checks after a device change: Android takes up to a couple of seconds to switch audio. */
            val SETTLE_DELAYS_MS = longArrayOf(400L, 1_200L, 3_000L)
            const val MEDIA_OUTPUT_PANEL = "android.settings.panel.action.MEDIA_OUTPUT"
            const val EXTRA_PACKAGE_NAME = "com.android.settings.panel.extra.PACKAGE_NAME"
        }
    }

private val WIRED_TYPES =
    setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
    )

private val BLUETOOTH_TYPES =
    setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_HEARING_AID,
        // LE Audio (Android 12+; plain int constants, so safe to reference on older versions).
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
    )

/** Name and kind for an Android output device type; [productName] is the device's own name, if any. */
internal fun audioOutputFor(
    type: Int,
    productName: String?,
): AudioOutput {
    val name = productName?.trim()?.takeIf { it.isNotEmpty() }
    return when (type) {
        // The built-in speaker's "product name" is the phone model, which reads oddly here.
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        -> AudioOutput.PHONE_SPEAKER
        in WIRED_TYPES ->
            AudioOutput(
                if (type == AudioDeviceInfo.TYPE_USB_HEADSET) name ?: "USB headset" else "Wired headphones",
                AudioOutput.Kind.WIRED,
            )
        in BLUETOOTH_TYPES -> AudioOutput(name ?: "Bluetooth", AudioOutput.Kind.BLUETOOTH)
        else -> AudioOutput(name ?: "External audio", AudioOutput.Kind.OTHER)
    }
}
