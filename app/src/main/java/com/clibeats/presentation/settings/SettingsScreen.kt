@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "MagicNumber", "ForbiddenImport")

package com.clibeats.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clibeats.BuildConfig
import com.clibeats.presentation.component.CliConfirmDialog
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.theme.LocalAccentColor
import com.clibeats.presentation.update.UpdateSection

private const val GITHUB_URL = "https://github.com/Omprakash-p06/clibeats"
private const val ADITYA_GITHUB_URL = "https://github.com/AdityaKrishnamurthy"
private const val OMPRAKASH_GITHUB_URL = "https://github.com/Omprakash-p06"
private const val PLAYLIST_FILE_NAME = "clibeats.json"
private val IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")
private const val CURSOR_BLINK_MS = 1_100
private const val HALF_CYCLE = 0.5f
private val blinkEasing = Easing { fraction -> if (fraction < HALF_CYCLE) 0f else 1f }

@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exchangeMessage by viewModel.exchangeMessage.collectAsStateWithLifecycle()
    val localFolders by viewModel.localMusicFolders.collectAsStateWithLifecycle()
    val scanStatus by viewModel.scanStatus.collectAsStateWithLifecycle()
    val cacheUsage by viewModel.cacheUsage.collectAsStateWithLifecycle()
    val artworkCacheMb by viewModel.artworkCacheMb.collectAsStateWithLifecycle()
    val downloadedBytes by viewModel.downloadedBytes.collectAsStateWithLifecycle()
    val lyricsCount by viewModel.lyricsCount.collectAsStateWithLifecycle()
    val lyricsBytes by viewModel.lyricsBytes.collectAsStateWithLifecycle()
    val downloadedItems by viewModel.downloadedSongItems.collectAsStateWithLifecycle()
    val shareFile by viewModel.shareFile.collectAsStateWithLifecycle()
    val localTrackCount by viewModel.localTrackCount.collectAsStateWithLifecycle()

    var folderToRemove by remember { mutableStateOf<String?>(null) }
    var confirmClearDownloads by remember { mutableStateOf(false) }
    var confirmClearArtwork by remember { mutableStateOf(false) }
    var confirmClearSongs by remember { mutableStateOf(false) }
    var confirmClearLyrics by remember { mutableStateOf(false) }
    var showManageDownloads by remember { mutableStateOf(false) }

    val accent = LocalAccentColor.current
    val context = LocalContext.current

    folderToRemove?.let { folder ->
        CliConfirmDialog(
            command = "rm \"${folderLabel(folder)}\"",
            message =
                "\"${folderLabel(folder)}\" and its songs will be removed from your library. " +
                    "The files on your phone are not deleted.",
            confirmLabel = "Remove",
            onConfirm = {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(folder),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                viewModel.removeLocalFolder(folder)
            },
            onDismiss = { folderToRemove = null },
        )
    }
    if (confirmClearDownloads) {
        CliConfirmDialog(
            command = "rm -r downloads/",
            message =
                "Delete every downloaded song? The files are removed from your phone and the songs stream " +
                    "again when played.",
            confirmLabel = "Delete all",
            onConfirm = viewModel::clearAllDownloads,
            onDismiss = { confirmClearDownloads = false },
        )
    }
    if (confirmClearLyrics) {
        CliConfirmDialog(
            command = "rm -r cache/lyrics",
            message =
                "Delete saved lyrics for $lyricsCount songs (${formatSize(lyricsBytes)})? Downloaded songs keep " +
                    "theirs; the rest are looked up again when you open them.",
            confirmLabel = "Clear",
            onConfirm = viewModel::clearLyrics,
            onDismiss = { confirmClearLyrics = false },
        )
    }
    if (confirmClearArtwork) {
        CliConfirmDialog(
            command = "rm -r cache/artwork",
            message =
                "Delete all saved cover images (${formatSize(cacheUsage.artworkBytes)})? " +
                    "They download again when needed.",
            confirmLabel = "Clear",
            onConfirm = viewModel::clearArtworkCache,
            onDismiss = { confirmClearArtwork = false },
        )
    }
    if (confirmClearSongs) {
        CliConfirmDialog(
            command = "rm -r cache/songs",
            message =
                "Delete all songs saved while streaming (${formatSize(cacheUsage.songBytes)})? " +
                    "They stream again next time. Your downloads aren't touched.",
            confirmLabel = "Clear",
            onConfirm = viewModel::clearSongCache,
            onDismiss = { confirmClearSongs = false },
        )
    }
    if (showManageDownloads) {
        ManageDownloadsDialog(
            downloadedItems = downloadedItems,
            onDeleteDownload = viewModel::deleteDownload,
            onClearAll = {
                showManageDownloads = false
                confirmClearDownloads = true
            },
            onDismiss = { showManageDownloads = false },
        )
    }

    LaunchedEffect(shareFile) {
        val file = shareFile ?: return@LaunchedEffect
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "CLIBeats library backup")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(Intent.createChooser(send, "Share CLIBeats backup"))
        }
        viewModel.clearShareFile()
    }

    val pickFolder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                viewModel.onLocalFolderPicked(uri)
            }
        }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) viewModel.exportPlaylists(uri)
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.importPlaylists(uri)
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val themePalette by viewModel.themePalette.collectAsStateWithLifecycle()
        ThemeSettingsBlock(
            uiState = uiState,
            palette = themePalette,
            onPaletteChange = viewModel::setThemePalette,
            onThemeModeChange = viewModel::setThemeMode,
            onAccentColorChange = viewModel::setAccentColor,
            onCustomAccentChange = viewModel::setCustomAccent,
        )
        LocalLibrarySettingsBlock(
            localFolders = localFolders,
            scanStatus = scanStatus,
            localTrackCount = localTrackCount,
            onAddFolder = { pickFolder.launch(null) },
            onRemoveFolder = { folderToRemove = it },
            onRescan = viewModel::rescanLocalMusic,
        )
        GesturesSettingsBlock()
        StorageSettingsBlock(
            cacheUsage = cacheUsage,
            artworkCacheMb = artworkCacheMb,
            songCacheMb = uiState.cacheMaxMb,
            downloadedBytes = downloadedBytes,
            downloadedCount = downloadedItems.size,
            onArtworkLimitChange = viewModel::setArtworkCacheMb,
            onSongLimitChange = viewModel::setSongCacheMb,
            onClearArtworkCache = { confirmClearArtwork = true },
            onClearSongCache = { confirmClearSongs = true },
            onManageDownloads = { showManageDownloads = true },
            onClearDownloads = { confirmClearDownloads = true },
            lyrics =
                LyricsStorage(
                    songs = lyricsCount,
                    bytes = lyricsBytes,
                    limit = viewModel.lyricsLimit,
                    onClear = { confirmClearLyrics = true },
                ),
        )
        TuiBlock(title = "Audio Quality", isActive = true) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setHighQualityStreaming(!uiState.highQualityStreaming) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "HIGH QUALITY STREAMING",
                        style = MaterialTheme.typography.titleSmall,
                        color = accent,
                    )
                    Text(
                        text = "Prefer high-bitrate audio streams",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (uiState.highQualityStreaming) "[ ON ]" else "[ OFF ]",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (uiState.highQualityStreaming) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.refreshLastBackup() }
        BackupSettingsBlock(
            lastBackupAt = lastBackupAt,
            onBackUpNow = viewModel::backUpNow,
            mirrorBackupsToDownloads = uiState.mirrorBackupsToDownloads,
            exchangeMessage = exchangeMessage,
            onToggleMirrorBackups = viewModel::setMirrorBackupsToDownloads,
            onExport = { exportLauncher.launch(PLAYLIST_FILE_NAME) },
            onImport = { importLauncher.launch(IMPORT_MIME_TYPES) },
            onShareSnapshot = viewModel::shareSnapshot,
        )
        UpdateSection()
        AboutSettingsBlock()
    }
}

@Composable
private fun AboutSettingsBlock() {
    val context = LocalContext.current
    TuiBlock(title = "About", isActive = true) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "CLIBeats v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Developed by",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DeveloperLine(name = "Omprakash Panda", url = OMPRAKASH_GITHUB_URL)
            DeveloperLine(name = "Aditya Krishnamurthy", url = ADITYA_GITHUB_URL, cursor = true)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Source: github.com/Omprakash-p06/clibeats · MIT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .semantics { contentDescription = "Source code on GitHub" }
                        .clickable(role = Role.Button) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))) }
                        }.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun DeveloperLine(
    name: String,
    url: String,
    cursor: Boolean = false,
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(CURSOR_BLINK_MS, easing = blinkEasing), RepeatMode.Restart),
        label = "blink",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .semantics { contentDescription = "$name on GitHub" }
                .clickable(role = Role.Button) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }.padding(vertical = 4.dp),
    ) {
        Text(
            text = "> ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = accent,
            textDecoration = TextDecoration.Underline,
            maxLines = 1,
        )
        if (cursor) {
            Text(text = " ▌", style = MaterialTheme.typography.bodyMedium, color = accent.copy(alpha = blink))
        }
    }
}
