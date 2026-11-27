@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.clibeats.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clibeats.domain.model.GestureSettings
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.theme.LocalAccentColor

/** Settings → Gestures: turn each swipe on or off, and go back to the defaults (all on). */
@Composable
internal fun GesturesSettingsBlock(viewModel: GesturesViewModel = hiltViewModel()) {
    val gestures by viewModel.gestures.collectAsStateWithLifecycle()
    val accent = LocalAccentColor.current
    TuiBlock(title = "Gestures", isActive = true) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GestureRow("SWIPE SONG RIGHT", "Add the song to the queue", gestures.songSwipeRight) {
                viewModel.update { it.copy(songSwipeRight = !it.songSwipeRight) }
            }
            GestureRow("SWIPE SONG LEFT", "Add the song to ♥ Favorites", gestures.songSwipeLeft) {
                viewModel.update { it.copy(songSwipeLeft = !it.songSwipeLeft) }
            }
            GestureRow("SWIPE BETWEEN TABS", "Swipe left / right to change tab", gestures.tabSwipe) {
                viewModel.update { it.copy(tabSwipe = !it.tabSwipe) }
            }
            GestureRow("NOW PLAYING: SWIPE DOWN", "Close Now Playing", gestures.nowPlayingSwipeDown) {
                viewModel.update { it.copy(nowPlayingSwipeDown = !it.nowPlayingSwipeDown) }
            }
            GestureRow("NOW PLAYING: SWIPE UP", "Switch between cover and lyrics", gestures.nowPlayingSwipeUp) {
                viewModel.update { it.copy(nowPlayingSwipeUp = !it.nowPlayingSwipeUp) }
            }
            if (gestures != GestureSettings()) {
                Text(
                    text = "[ RESET TO DEFAULTS ]",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier =
                        Modifier
                            .clickable(role = Role.Button, onClick = viewModel::reset)
                            .padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun GestureRow(
    title: String,
    description: String,
    on: Boolean,
    onToggle: () -> Unit,
) {
    val accent = LocalAccentColor.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Switch, onClick = onToggle),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = accent)
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (on) "[ ON ]" else "[ OFF ]",
            style = MaterialTheme.typography.labelMedium,
            color = if (on) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
