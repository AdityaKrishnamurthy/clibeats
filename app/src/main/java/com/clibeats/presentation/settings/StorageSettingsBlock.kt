@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "MagicNumber", "ForbiddenImport")

package com.clibeats.presentation.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clibeats.data.cache.CacheUsage
import com.clibeats.data.local.dao.DownloadedSongItem
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.presentation.component.TuiButton
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

private fun trimZeros(number: String): String = if ('.' in number) number.trimEnd('0').trimEnd('.') else number

/** Settings → Storage & cache: the two caches (with their limits) and saved lyrics. Downloads are separate. */
@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun StorageSettingsBlock(
    cacheUsage: CacheUsage,
    artworkCacheMb: Int,
    songCacheMb: Int,
    downloadedBytes: Long,
    onArtworkLimitChange: (Int) -> Unit,
    onSongLimitChange: (Int) -> Unit,
    onClearArtworkCache: () -> Unit,
    onClearSongCache: () -> Unit,
    lyrics: LyricsStorage = LyricsStorage(),
) {
    val accent = LocalAccentColor.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsPanel {
            Text(
                text = "Cache and lyrics use",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatSize(cacheUsage.artworkBytes + cacheUsage.songBytes + lyrics.bytes),
                style = MaterialTheme.typography.headlineSmall,
                color = accent,
            )
            Text(
                text =
                    "Songs ${formatSize(cacheUsage.songBytes)} \u00b7 Covers ${formatSize(cacheUsage.artworkBytes)} " +
                        "\u00b7 Lyrics ${formatSize(lyrics.bytes)}. Downloads (${formatSize(downloadedBytes)}) are " +
                        "separate: Settings \u2192 Downloads.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsPanel(label = "Cache") {
            CacheRow(
                label = "Songs",
                hint = "kept while streaming, so replays use no data",
                usedBytes = cacheUsage.songBytes,
                options = AppPreferences.SONG_CACHE_OPTIONS_MB,
                selectedMb = songCacheMb,
                onSelect = onSongLimitChange,
                onClear = onClearSongCache,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            CacheRow(
                label = "Cover art",
                hint = "album and song pictures",
                usedBytes = cacheUsage.artworkBytes,
                options = AppPreferences.ARTWORK_CACHE_OPTIONS_MB,
                selectedMb = artworkCacheMb,
                onSelect = onArtworkLimitChange,
                onClear = onClearArtworkCache,
            )
            Text(
                text =
                    "When a cache is full, what you haven't played or seen for the longest is removed " +
                        "automatically to make room. Clearing or filling a cache never touches your downloads " +
                        "or their covers.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsPanel(label = "Lyrics") { LyricsRow(lyrics) }
    }
}

/** Settings → Downloads: every downloaded song, with removing one or all. Separate from the caches. */
@Composable
internal fun DownloadsSettingsBlock(
    downloadedItems: List<DownloadedSongItem>,
    downloadedBytes: Long,
    onDeleteDownload: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    val accent = LocalAccentColor.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsPanel {
            Text(
                text = if (downloadedItems.size == 1) "1 song" else "${downloadedItems.size} songs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = formatSize(downloadedBytes), style = MaterialTheme.typography.headlineSmall, color = accent)
            Text(
                text =
                    "Saved for offline with their covers and lyrics. No size limit, and nothing here is removed " +
                        "unless you remove it.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (downloadedItems.isNotEmpty()) {
                TuiButton(
                    text = "DELETE ALL DOWNLOADS",
                    onClick = onClearAll,
                    modifier = Modifier.semantics { contentDescription = "Delete all downloaded songs" },
                )
            }
        }
        if (downloadedItems.isNotEmpty()) {
            SettingsPanel(label = "Songs") {
                downloadedItems.forEach { item ->
                    ManageDownloadItemRow(item = item, onDelete = { onDeleteDownload(item.songId) })
                }
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

/** A size line and a small text button at its end (Clear, Manage...). */
@Suppress("LongParameterList")
@Composable
private fun SizeLine(
    title: String,
    value: String,
    action: String?,
    actionColor: Color,
    actionDescription: String,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (action != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.semantics { contentDescription = actionDescription },
            ) {
                Text(action, style = MaterialTheme.typography.labelMedium, color = actionColor)
            }
        }
    }
}

private fun thousands(n: Int): String = String.format(java.util.Locale.US, "%,d", n)

@Composable
private fun LyricsRow(lyrics: LyricsStorage) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SizeLine(
            title = "${thousands(lyrics.songs)} of ${thousands(lyrics.limit)} songs",
            value = formatSize(lyrics.bytes),
            action = if (lyrics.songs > 0) "Clear" else null,
            actionColor = MaterialTheme.colorScheme.error,
            actionDescription = "Clear saved lyrics",
            onAction = lyrics.onClear,
        )
        UsageBar(fraction = if (lyrics.limit > 0) lyrics.songs.toFloat() / lyrics.limit else 0f)
        Text(
            text =
                "Past ${thousands(lyrics.limit)} songs the oldest are dropped. Downloaded songs always keep theirs.",
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
    hint: String,
    usedBytes: Long,
    options: List<Int>,
    selectedMb: Int,
    onSelect: (Int) -> Unit,
    onClear: () -> Unit,
) {
    val accent = LocalAccentColor.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SizeLine(
            title = label,
            value = "${formatSize(usedBytes)} of ${formatMb(selectedMb)} \u00b7 $hint",
            action = "Clear",
            actionColor = MaterialTheme.colorScheme.error,
            actionDescription = "Clear ${label.lowercase()} cache",
            onAction = onClear,
        )
        UsageBar(fraction = usedBytes.toFloat() / (selectedMb * BYTES_PER_MB).coerceAtLeast(1L))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { mb ->
                val isSelected = mb == selectedMb
                val shape = RoundedCornerShape(50)
                Text(
                    text = formatMb(mb),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) readableOn(accent) else MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .clip(shape)
                            .background(if (isSelected) accent else Color.Transparent)
                            .border(1.dp, if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant, shape)
                            .semantics { selected = isSelected }
                            .clickable(role = Role.RadioButton) { onSelect(mb) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
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
