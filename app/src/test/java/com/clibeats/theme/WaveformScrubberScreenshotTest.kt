package com.clibeats.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.clibeats.playback.waveform.Loudness
import com.clibeats.presentation.player.WaveformScrubber
import com.clibeats.presentation.theme.CliBeatsTheme
import org.junit.Rule
import org.junit.Test
import kotlin.math.sin

class WaveformScrubberScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    /** First half measured (a song with a quiet intro building up), second half not heard yet. */
    @Test
    fun waveform_partlyMeasured() {
        val levels =
            FloatArray(Loudness.BAR_COUNT) { i ->
                if (i >= Loudness.BAR_COUNT / 2) Float.NaN else 0.35f + 0.5f * (i / 32f) + 0.1f * sin(i * 0.9f)
            }
        paparazzi.snapshot {
            CliBeatsTheme {
                Box(Modifier.background(MaterialTheme.colorScheme.background).padding(12.dp)) {
                    WaveformScrubber(
                        trackKey = "song",
                        positionMs = 60_000,
                        durationMs = 200_000,
                        onSeek = {},
                        heights = Loudness.normalize(levels),
                    ) {}
                }
            }
        }
    }
}
