@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.clibeats.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clibeats.presentation.component.TuiButton
import com.clibeats.presentation.theme.LocalAccentColor

/** Settings → Backup: the automatic daily backup, the copy kept in Downloads, and your own copies. */
@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun BackupSettingsBlock(
    lastBackupAt: Long?,
    onBackUpNow: () -> Unit,
    mirrorBackupsToDownloads: Boolean,
    exchangeMessage: String?,
    onToggleMirrorBackups: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onShareSnapshot: () -> Unit,
) {
    val accent = LocalAccentColor.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsPanel(label = "Automatic backup") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lastBackupAt?.let { "Last saved ${backupTime(it)}" } ?: "Not saved yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Once a day, each replacing the one before",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TuiButton(text = "BACK UP NOW", onClick = onBackUpNow)
            }
            Text(
                text =
                    "Saves playlists, ♥ Favorites, saved albums & artists, play history (Library → Online, " +
                        "Most played, Recently played), which songs are downloaded, and every setting. Not the " +
                        "music files, caches or lyrics.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingsSwitchRow(
                icon = null,
                title = "Keep a copy in Downloads",
                summary =
                    "Download/CLIBeats/clibeats-backup-DDMMYYYY.json — survives uninstalling; setup offers " +
                        "to restore it. Anyone with file access can read it.",
                checked = mirrorBackupsToDownloads,
                onCheckedChange = onToggleMirrorBackups,
            )
        }
        SettingsGroup(
            label = "Your copies",
            rows =
                listOf(
                    {
                        SettingsNavRow(
                            Icons.Outlined.SaveAlt,
                            "Save a copy…",
                            "Downloads, Drive, a USB stick",
                            onExport,
                        )
                    },
                    {
                        SettingsNavRow(
                            Icons.Outlined.Restore,
                            "Restore…",
                            "Pick a backup file to bring everything back (downloads are offered again)",
                            onImport,
                        )
                    },
                    {
                        SettingsNavRow(
                            Icons.Outlined.Share,
                            "Share",
                            "Send the backup to another phone or app",
                            onShareSnapshot,
                        )
                    },
                ),
        )
        exchangeMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = accent,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** "today 3:12 PM", "yesterday 9:05 AM" or "12 Nov, 9:05 AM". */
internal fun backupTime(epochMs: Long): String {
    val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(epochMs))
    val days =
        java.util.concurrent.TimeUnit.MILLISECONDS.toDays(
            startOfDay(System.currentTimeMillis()) - startOfDay(epochMs),
        )
    return when (days) {
        0L -> "today $time"
        1L -> "yesterday $time"
        else -> {
            val day = java.text.SimpleDateFormat("d MMM, ", java.util.Locale.getDefault())
            day.format(java.util.Date(epochMs)) + time
        }
    }
}

private fun startOfDay(epochMs: Long): Long =
    java.util.Calendar.getInstance().apply {
        timeInMillis = epochMs
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
