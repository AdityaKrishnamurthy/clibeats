@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "ForbiddenImport")

package com.clibeats.presentation.settings

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clibeats.data.scan.ScanStatus
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.component.TuiButton
import com.clibeats.presentation.component.TuiExpandable
import com.clibeats.presentation.theme.LocalAccentColor

private const val LOCAL_HINT =
    "Tags (title / artist / album) are read from the files. FLAC, MP3, WAV, M4A, OGG, OPUS."

internal fun folderLabel(treeUri: String): String =
    runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(treeUri)).substringAfter(':').ifBlank { "/" } }
        .getOrDefault(treeUri)

private fun trackCountLabel(count: Int): String = if (count == 1) "1 track in library" else "$count tracks in library"

@Suppress("LongParameterList")
@Composable
internal fun LocalLibrarySettingsBlock(
    localFolders: List<String>,
    scanStatus: ScanStatus,
    localTrackCount: Int,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRescan: () -> Unit,
) {
    TuiBlock(title = "Local Music", isActive = true) {
        TuiExpandable(
            summary =
                if (localFolders.isEmpty()) {
                    "No folders · add local music"
                } else {
                    "${localFolders.size} folders · $localTrackCount tracks"
                },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (localFolders.isEmpty()) {
                    Text(
                        text = "FOLDERS : none chosen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                localFolders.forEach { folder ->
                    LocalFolderRow(folder = folder, onRemoveFolder = onRemoveFolder)
                }
                LocalScanStatusText(
                    localFolders = localFolders,
                    scanStatus = scanStatus,
                    localTrackCount = localTrackCount,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TuiButton(
                        text = "+ ADD FOLDER",
                        onClick = onAddFolder,
                        modifier = Modifier.weight(1f),
                    )
                    TuiButton(
                        text = "RESCAN",
                        onClick = onRescan,
                        enabled = localFolders.isNotEmpty() && scanStatus !is ScanStatus.Scanning,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalFolderRow(
    folder: String,
    onRemoveFolder: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "▸ ${folderLabel(folder)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "[ x ]",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier =
                Modifier
                    .semantics { contentDescription = "Remove folder ${folderLabel(folder)}" }
                    .clickable(role = Role.Button) { onRemoveFolder(folder) }
                    .padding(8.dp),
        )
    }
}

@Composable
private fun LocalScanStatusText(
    localFolders: List<String>,
    scanStatus: ScanStatus,
    localTrackCount: Int,
) {
    val accent = LocalAccentColor.current
    val text =
        when (scanStatus) {
            is ScanStatus.Idle, is ScanStatus.Done ->
                if (localFolders.isEmpty()) LOCAL_HINT else trackCountLabel(localTrackCount)
            is ScanStatus.Scanning ->
                if (scanStatus.total == 0) {
                    "Scanning..."
                } else {
                    "Scanning ${scanStatus.processed}/${scanStatus.total}..."
                }
            is ScanStatus.Failed -> "Scan failed: ${scanStatus.message}"
        }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (scanStatus is ScanStatus.Failed) MaterialTheme.colorScheme.error else accent,
    )
}
