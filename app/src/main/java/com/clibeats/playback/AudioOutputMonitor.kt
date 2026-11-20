@file:Suppress("ForbiddenImport")

package com.clibeats.playback

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.MediaRoute2Info
import android.media.MediaRouter
import android.media.MediaRouter2
import android.media.RouteDiscoveryPreference
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
 * It reads the device this app's playback is on (Android 13+, when the phone shares it), otherwise the system
 * media route: the Bluetooth device in use, plugged-in headphones, or the phone speaker.
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

        /** Android 14+: the per-app system route, i.e. what the user picked in the output switcher. */
        private val router2: MediaRouter2? by lazy {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                runCatching { MediaRouter2.getInstance(context) }.getOrNull()
            } else {
                null
            }
        }

        init {
            // Callbacks are registered and delivered on the main thread.
            main.post {
                runCatching {
                    audioManager?.registerAudioDeviceCallback(
                        object : AudioDeviceCallback() {
                            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = onRoutingChanged()

                            override fun onAudioDevicesRemoved(gone: Array<out AudioDeviceInfo>?) = onRoutingChanged()
                        },
                        main,
                    )
                }
                runCatching {
                    // Fires when this app's playback starts, stops or is moved to another device.
                    audioManager?.registerAudioPlaybackCallback(
                        object : AudioManager.AudioPlaybackCallback() {
                            override fun onPlaybackConfigChanged(c: List<AudioPlaybackConfiguration>?) {
                                onRoutingChanged()
                            }
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) listenToSystemRoute()
                refresh()
            }
        }

        /**
         * Picking another output in Android's switcher changes this app's system route: follow it. The empty
         * discovery preference asks for no scanning, so this costs nothing.
         */
        @androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        private fun listenToSystemRoute() {
            val router = router2 ?: return
            val executor = context.mainExecutor
            runCatching {
                router.registerRouteCallback(
                    executor,
                    object : MediaRouter2.RouteCallback() {
                        override fun onRoutesUpdated(routes: List<MediaRoute2Info>) = onRoutingChanged()
                    },
                    RouteDiscoveryPreference.Builder(emptyList(), false).build(),
                )
                router.registerControllerCallback(
                    executor,
                    object : MediaRouter2.ControllerCallback() {
                        override fun onControllerUpdated(controller: MediaRouter2.RoutingController) {
                            onRoutingChanged()
                        }
                    },
                )
                router.registerTransferCallback(
                    executor,
                    object : MediaRouter2.TransferCallback() {
                        override fun onTransfer(
                            oldController: MediaRouter2.RoutingController,
                            newController: MediaRouter2.RoutingController,
                        ) = onRoutingChanged()
                    },
                )
            }
        }

        /** Something about the output changed: check now, then again while Android finishes moving the audio. */
        private fun onRoutingChanged() {
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
            return playbackDevice(am) ?: systemRoute() ?: routeOutput(am)
        }

        /**
         * Android 14+: the output this app is routed to, including a choice made in the system switcher (e.g.
         * the phone speaker while earbuds stay connected, which the older media route doesn't reflect).
         * Route types use the same numbers as AudioDeviceInfo types.
         */
        private fun systemRoute(): AudioOutput? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
            val route = router2?.systemController?.selectedRoutes?.firstOrNull()
            return route?.let { audioOutputFor(it.type, it.name?.toString()) }
        }

        /**
         * Android 13+: the device this app's audio is playing on right now, when Android shares it (some
         * phones don't). Null while paused.
         */
        private fun playbackDevice(am: AudioManager): AudioOutput? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
            val device = am.activePlaybackConfigurations.firstNotNullOfOrNull { it.audioDeviceInfo }
            return device?.let { audioOutputFor(it.type, it.productName?.toString()) }
        }

        /**
         * The system media route: a Bluetooth device when one is in use (with its name), otherwise plugged-in
         * headphones or the phone speaker. Works on every version, including phones that keep the newer
         * "which device is media using" query to themselves (it returns nothing for apps on some Android 15s).
         */
        private fun routeOutput(am: AudioManager): AudioOutput {
            val route = mediaRouter?.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)
            if (route?.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH) {
                return audioOutputFor(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, route.getName(context)?.toString())
            }
            val wired = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in WIRED_TYPES }
            return wired?.let { audioOutputFor(it.type, it.productName?.toString()) } ?: AudioOutput.PHONE_SPEAKER
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
