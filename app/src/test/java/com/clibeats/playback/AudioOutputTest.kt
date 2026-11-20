package com.clibeats.playback

import android.media.AudioDeviceInfo
import com.clibeats.domain.model.AudioOutput
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioOutputTest {
    @Test
    fun `the built-in speaker is the phone speaker, not the phone model`() {
        assertThat(audioOutputFor(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Pixel 8"))
            .isEqualTo(AudioOutput.PHONE_SPEAKER)
    }

    @Test
    fun `bluetooth devices show their own name`() {
        assertThat(audioOutputFor(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Galaxy Buds2 Pro"))
            .isEqualTo(AudioOutput("Galaxy Buds2 Pro", AudioOutput.Kind.BLUETOOTH))
        assertThat(audioOutputFor(AudioDeviceInfo.TYPE_BLE_HEADSET, " "))
            .isEqualTo(AudioOutput("Bluetooth", AudioOutput.Kind.BLUETOOTH))
    }

    @Test
    fun `wired and usb headphones`() {
        assertThat(audioOutputFor(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "h2w"))
            .isEqualTo(AudioOutput("Wired headphones", AudioOutput.Kind.WIRED))
        assertThat(audioOutputFor(AudioDeviceInfo.TYPE_USB_HEADSET, "USB-C Earphones"))
            .isEqualTo(AudioOutput("USB-C Earphones", AudioOutput.Kind.WIRED))
    }

    @Test
    fun `anything else keeps its name`() {
        assertThat(audioOutputFor(AudioDeviceInfo.TYPE_USB_DEVICE, "FiiO KA3"))
            .isEqualTo(AudioOutput("FiiO KA3", AudioOutput.Kind.OTHER))
        assertThat(audioOutputFor(AudioDeviceInfo.TYPE_HDMI, null))
            .isEqualTo(AudioOutput("External audio", AudioOutput.Kind.OTHER))
    }
}
