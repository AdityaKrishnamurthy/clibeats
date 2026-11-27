@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "MagicNumber", "ForbiddenImport")

package com.clibeats.presentation.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clibeats.data.cache.CacheUsage
import com.clibeats.data.local.dao.DownloadedSongItem
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.component.TuiExpandable
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor

private const val BYTES_PER_MB = 1048576L
private const val MB_PER_GB = 1024
private const val BYTES_PER_GB = BYTES_PER_MB * MB_PER_GB

/** Sizes in settings are MB or GB only: "0 MB", "3.4 MB", "512 MB", "1.25 GB". */
@Suppress("ReturnCount")
internal fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    if (bytes >= BYTES_PER_GB) {
        val gb = bytes.toDouble() / BYTES_PER_GB
        return "${trimZeros(String.format(java.util.Locale.US, "%.2f", gb))} GB"
    }
    val mb = bytes.toDouble() / BYTES_PER_MB
    val text = if (mb >= 100) "%.0f" else "%.1f"
    return "${trimZeros(String.format(java.util.Locale.US, text, mb.coerceAtLeast(0.1)))} MB"
}

/** A limit picked in MB: "256 MB", "1 GB", "2 GB". */
internal fun formatMb(mb: Int): String =
    if (mb >= MB_PER_GB && mb % MB_PER_GB == 0) "${mb / MB_PER_GB} GB" else formatSize(mb * BYTES_PER_MB)

private fun downloadsLabel(
    bytes: Long,
    songs: Int,
): String = "DOWNLOADS  ${formatSize(bytes)} · $songs ${if (songs == 1) "song" else "songs"}"

private fun trimZeros(number: String): String = if ('.' in number) number.trimEnd('0').trimEnd('.') else number

@Suppress("LongParameterList")
@Composable
internal fun StorageSettingsBlock(
    cacheUsage: CacheUsage,
    artworkCacheMb: Int,
    songCacheMb: Int,
    downloadedBytes: Long,
    downloadedCount: Int,
    onArtworkLimitChange: (Int) -> Unit,
    onSongLimitChange: (Int) -> Unit,
    onClearArtworkCache: () -> Unit,
    onClearSongCache: () -> Unit,
    onManageDownloads: () -> Unit,
    onClearDownloads: () -> Unit,
    lyrics: LyricsStorage = LyricsStorage(),
) {
    val accent = LocalAccentColor.current
    TuiBlock(title = "Storage and Cache", isActive = true) {
        TuiExpandable(
            summary =
                formatSize(cacheUsage.artworkBytes + cacheUsage.songBytes + downloadedBytes + lyrics.bytes) +
                    " used · caches, downloads & lyrics",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text =
                        "TOTAL USED  " +
                            formatSize(cacheUsage.artworkBytes + cacheUsage.songBytes + downloadedBytes + lyrics.bytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
                CacheRow(
                    label = "ARTWORK",
                    usedBytes = cacheUsage.artworkBytes,
                    options = AppPreferences.ARTWORK_CACHE_OPTIONS_MB,
                    selectedMb = artworkCacheMb,
                    onSelect = onArtworkLimitChange,
                    onClear = onClearArtworkCache,
                )
                CacheRow(
                    label = "SONGS",
                    usedBytes = cacheUsage.songBytes,
                    options = AppPreferences.SONG_CACHE_OPTIONS_MB,
                    selectedMb = songCacheMb,
                    onSelect = onSongLimitChange,
                    onClear = onClearSongCache,
                )
                DownloadsRow(
                    usedBytes = downloadedBytes,
                    songs = downloadedCount,
                    onManage = onManageDownloads,
                    onClearAll = onClearDownloads,
                )
                LyricsRow(lyrics)
            }
        }
    }
}

/** Saved lyrics for the Storage block: how many songs, their size, the most kept, and clearing them. */
internal class LyricsStorage(
    val songs: Int = 0,
    val bytes: Long = 0L,
    val limit: Int = 0,
    val onClear: () -> Unit = {},
)

@Composable
private fun LyricsRow(lyrics: LyricsStorage) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "LYRICS  ${formatSize(lyrics.bytes)} · ${lyrics.songs} / ${lyrics.limit} songs",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (lyrics.songs > 0) {
                Text(
                    text = "[ CLEAR ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier =
                        Modifier
                            .semantics { contentDescription = "Clear saved lyrics" }
                            .clickable(role = Role.Button, onClick = lyrics.onClear)
                            .padding(8.dp),
                )
            }
        }
        Text(
            text = "oldest dropped past ${lyrics.limit} · downloaded songs keep theirs",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongParameterList")
@Composable
private fun CacheRow(
    label: String,
    usedBytes: Long,
    options: List<Int>,
    selectedMb: Int,
    onSelect: (Int) -> Unit,
    onClear: () -> Unit,
) {
    val accent = LocalAccentColor.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$label  ${formatSize(usedBytes)} / ${formatMb(selectedMb)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "[ CLEAR ]",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .semantics { contentDescription = "Clear ${label.lowercase()} cache" }
                        .clickable(role = Role.Button, onClick = onClear)
                        .padding(8.dp),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { mb ->
                val isSelected = mb == selectedMb
                Text(
                    text = formatMb(mb),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .border(1.dp, if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant)
                            .semantics { selected = isSelected }
                            .clickable(role = Role.RadioButton) { onSelect(mb) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun DownloadsRow(
    usedBytes: Long,
    songs: Int,
    onManage: () -> Unit,
    onClearAll: () -> Unit,
) {
    val accent = LocalAccentColor.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = downloadsLabel(usedBytes, songs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (songs > 0) {
                Text(
                    text = "[ CLEAR ALL ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier =
                        Modifier
                            .semantics { contentDescription = "Delete all downloaded songs" }
                            .clickable(role = Role.Button, onClick = onClearAll)
                            .padding(8.dp),
                )
            }
        }
        Text(
            text = "↓ MANAGE",
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier =
                Modifier
                    .border(1.dp, accent)
                    .semantics { contentDescription = "Manage downloaded songs" }
                    .clickable(role = Role.Button, onClick = onManage)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Text(
            text = if (songs > 0) "saved for offline · no size limit" else "nothing downloaded yet",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ManageDownloadsDialog(
    downloadedItems: List<DownloadedSongItem>,
    onDeleteDownload: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("> storage --downloads (${downloadedItems.size})") },
        text = {
            if (downloadedItems.isEmpty()) {
                Text("No downloaded songs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(downloadedItems, key = { it.songId }) { item ->
                        ManageDownloadItemRow(item = item, onDelete = { onDeleteDownload(item.songId) })
                    }
                }
            }
        },
        confirmButton = {
            if (downloadedItems.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("CLEAR ALL", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE") }
        },
    )
}

@Composable
private fun ManageDownloadItemRow(
    item: DownloadedSongItem,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = CliBeatsTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${item.artist} · ${formatSize(item.sizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = CliBeatsTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "[ REMOVE ]",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.clickable(onClick = onDelete).padding(4.dp),
        )
    }
}
