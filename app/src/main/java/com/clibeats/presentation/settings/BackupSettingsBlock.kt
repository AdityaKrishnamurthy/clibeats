@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.clibeats.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.theme.LocalAccentColor

@Suppress("LongParameterList")
@Composable
internal fun BackupSettingsBlock(
    mirrorBackupsToDownloads: Boolean,
    exchangeMessage: String?,
    onToggleMirrorBackups: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onShareSnapshot: () -> Unit,
    onConfirmClearBackups: () -> Unit,
) {
    val accent = LocalAccentColor.current
    TuiBlock(title = "Playlist Exchange", isActive = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                ) {
                    Text("EXPORT")
                }
                Button(
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                ) {
                    Text("IMPORT")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onShareSnapshot,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                ) {
                    Text("SHARE BACKUP")
                }
                OutlinedButton(onClick = onConfirmClearBackups) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete saved backups")
                }
            }
            MirrorDownloadsToggleRow(
                mirrorBackupsToDownloads = mirrorBackupsToDownloads,
                onToggle = onToggleMirrorBackups,
            )
            Text(
                text =
                    "A daily snapshot is kept inside app storage. Enable Download/CLIBeats mirroring to " +
                        "preserve backups across uninstalls.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            exchangeMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
            }
        }
    }
}

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
                text = "MIRROR TO DOWNLOADS/",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
            Text(
                text = "Also write plaintext clibeats-backup.json to Download/CLIBeats/",
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
