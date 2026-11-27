@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.clibeats.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clibeats.domain.model.GestureSettings
import com.clibeats.domain.model.SwipeAction
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.theme.LocalAccentColor

/** Settings → Gestures: turn each swipe on or off, and go back to the defaults (all on). */
@Composable
internal fun GesturesSettingsBlock(viewModel: GesturesViewModel = hiltViewModel()) {
    val gestures by viewModel.gestures.collectAsStateWithLifecycle()
    val accent = LocalAccentColor.current
    TuiBlock(title = "Gestures", isActive = true) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Top right of the box: back to the default gestures.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SONG SWIPES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                val isDefault = gestures == GestureSettings()
                IconButton(onClick = viewModel::reset, enabled = !isDefault) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = "Reset gestures to default",
                        tint = if (isDefault) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else accent,
                    )
                }
            }
            SwipeActionRow("SWIPE SONG RIGHT →", gestures.songSwipeRight) { action ->
                viewModel.update { it.copy(songSwipeRight = action) }
            }
            SwipeActionRow("← SWIPE SONG LEFT", gestures.songSwipeLeft) { action ->
                viewModel.update { it.copy(songSwipeLeft = action) }
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
        }
    }
}

/** A song swipe and what it does; tap to pick another action. */
@Composable
private fun SwipeActionRow(
    title: String,
    action: SwipeAction,
    onPick: (SwipeAction) -> Unit,
) {
    val accent = LocalAccentColor.current
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.DropdownList) { open = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = accent, modifier = Modifier.weight(1f))
        Box {
            Text(
                text = "[ ${action.label} ▾ ]",
                style = MaterialTheme.typography.labelMedium,
                color = if (action == SwipeAction.OFF) MaterialTheme.colorScheme.onSurfaceVariant else accent,
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                SwipeAction.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(if (option == action) "● ${option.label}" else "   ${option.label}") },
                        onClick = {
                            open = false
                            onPick(option)
                        },
                    )
                }
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
