@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.component.TuiButton
import com.clibeats.presentation.theme.LocalAccentColor

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
    TuiBlock(title = "Backup", isActive = true) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 1. The automatic backup and when it last ran.
            Column {
                Text("AUTOMATIC BACKUP · DAILY", style = MaterialTheme.typography.titleSmall, color = accent)
                Text(
                    text =
                        lastBackupAt?.let { "Last saved ${backupTime(it)}" }
                            ?: "Not saved yet — it saves once a day when you open the app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        "Saves your playlists, ♥ Favorites, saved albums & artists, play history " +
                            "(Most played, Recently played), which songs are downloaded, and all your settings. " +
                            "Each day's backup replaces the last. The music files, caches and lyrics aren't " +
                            "included.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                TuiButton(
                    text = "BACK UP NOW",
                    onClick = onBackUpNow,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            // 2. Keeping a copy that survives uninstalling.
            MirrorDownloadsToggleRow(
                mirrorBackupsToDownloads = mirrorBackupsToDownloads,
                onToggle = onToggleMirrorBackups,
            )
            // 3. Your own copies: save, restore, send.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("YOUR COPIES", style = MaterialTheme.typography.titleSmall, color = accent)
                BackupAction("SAVE A COPY…", "Save the backup file anywhere: Downloads, Drive, a USB stick", onExport)
                BackupAction(
                    "RESTORE…",
                    "Pick a backup file to bring everything back (downloads are offered again)",
                    onImport,
                )
                BackupAction("SHARE", "Send the backup to another phone or app", onShareSnapshot)
            }
            exchangeMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One backup action: a button and a line saying what it does. */
@Composable
private fun BackupAction(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TuiButton(text = label, onClick = onClick)
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
    }
}

/** "today 3:12 PM", "yesterday 9:05 AM" or "12 Nov, 9:05 AM". */
private fun backupTime(epochMs: Long): String {
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

@Composable
private fun MirrorDownloadsToggleRow(
    mirrorBackupsToDownloads: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val accent = LocalAccentColor.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(!mirrorBackupsToDownloads) }
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = "KEEP A COPY IN DOWNLOADS",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
            Text(
                text =
                    "Also saves it to Download/CLIBeats (named with the date), so it survives uninstalling the app " +
                        "(restore it from setup). It's a readable file anyone with file access can open.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (mirrorBackupsToDownloads) "[ ON ]" else "[ OFF ]",
            style = MaterialTheme.typography.labelMedium,
            color = if (mirrorBackupsToDownloads) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
