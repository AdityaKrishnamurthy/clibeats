@file:Suppress(
    "ktlint:standard:function-naming",
    "FunctionNaming",
    "ForbiddenImport",
    "MagicNumber",
)

package com.clibeats.presentation.settings

import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clibeats.data.discord.DiscordActivity
import com.clibeats.data.discord.DiscordSdkStatus
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.component.TuiButton
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor
import java.util.Locale

/** Settings → Discord: opt-in Rich Presence preview; publishing needs the official Social SDK. */
@Composable
internal fun DiscordSettingsBlock(viewModel: DiscordPresenceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accent = LocalAccentColor.current

    TuiBlock(title = "Discord", isActive = state.enabled) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "SHOW WHAT YOU'RE PLAYING",
                        style = MaterialTheme.typography.titleSmall,
                        color = accent,
                    )
                    Text(
                        text = "Adds a Listening activity to your Discord profile while CLIBeats plays.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TuiButton(
                    text = if (state.enabled) "TURN OFF" else "TURN ON",
                    onClick = { viewModel.setEnabled(!state.enabled) },
                )
            }
            state.activity?.let { activity ->
                DiscordPreview(activity = activity, enabled = state.enabled)
            } ?: Text(
                text = "Play a song to see the card that would be published.",
                style = MaterialTheme.typography.labelSmall,
                color = CliBeatsTextSecondary,
            )
            when (state.sdkStatus) {
                DiscordSdkStatus.READY ->
                    Text(
                        text = "[ SDK READY ] Publishing through the official Discord Social SDK.",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                DiscordSdkStatus.SDK_REQUIRED ->
                    Text(
                        text =
                            "[ SDK REQUIRED ] This build previews the card only: Discord's Android Rich " +
                                "Presence needs the official Social SDK AAR. No user token is used — self-bots " +
                                "violate Discord's Terms. See docs/DISCORD_PRESENCE.md.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
    }
}

@Composable
private fun DiscordPreview(
    activity: DiscordActivity,
    enabled: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "> now_playing",
            style = MaterialTheme.typography.labelSmall,
            color = CliBeatsTextSecondary,
        )
        Text(
            text = activity.details,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalAccentColor.current,
            maxLines = 1,
        )
        Text(
            text = activity.state,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text =
                "[ ${if (activity.isPlaying) "LISTENING" else "PAUSED"} ]  " +
                    "${formatTime(activity.positionMs)} / ${formatTime(activity.durationMs)}  " +
                    if (enabled) "" else "· preview",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1_000).toInt()
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}
