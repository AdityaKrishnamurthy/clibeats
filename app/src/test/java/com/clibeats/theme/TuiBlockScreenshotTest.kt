package com.clibeats.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.component.TuiTextField
import com.clibeats.presentation.theme.CliBeatsTheme
import org.junit.Rule
import org.junit.Test

class TuiBlockScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test
    fun tuiBlock_titledBlocks() {
        paparazzi.snapshot {
            CliBeatsTheme {
                Column(
                    modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TuiBlock(title = "System Status", isActive = true) {
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("● ACTIVE PROVIDER", style = MaterialTheme.typography.labelMedium)
                                Text("[ ONLINE ]", style = MaterialTheme.typography.labelSmall)
                            }
                            Text("PROVIDER : YouTube Music", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TuiTextField(value = "", onValueChange = {}, label = "Search", placeholder = "song, artist, album")
                    TuiBlock(title = "Playback") {
                        Text("Auto-queue related songs", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
