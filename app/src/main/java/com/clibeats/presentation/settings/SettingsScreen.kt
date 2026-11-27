@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "MagicNumber", "ForbiddenImport")

package com.clibeats.presentation.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clibeats.BuildConfig
import com.clibeats.presentation.component.CliConfirmDialog
import com.clibeats.presentation.theme.CliBeatsThemeMode
import com.clibeats.presentation.theme.LocalAccentColor
import com.clibeats.presentation.update.UpdateSection

private const val GITHUB_URL = "https://github.com/Omprakash-p06/clibeats"
private const val ADITYA_GITHUB_URL = "https://github.com/AdityaKrishnamurthy"
private const val OMPRAKASH_GITHUB_URL = "https://github.com/Omprakash-p06"
private const val PLAYLIST_FILE_NAME = "clibeats.json"
private val IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")
private const val CURSOR_BLINK_MS = 1_100
private const val PAGE_MS = 220
private const val HALF_CYCLE = 0.5f
private val blinkEasing = Easing { fraction -> if (fraction < HALF_CYCLE) 0f else 1f }

@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    backToListRequested: Boolean = false,
    onBackToListHandled: () -> Unit = {},
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

    val themePalette by viewModel.themePalette.collectAsStateWithLifecycle()
    val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshLastBackup() }
    val gesturesViewModel: GesturesViewModel = hiltViewModel()
    val gestures by gesturesViewModel.gestures.collectAsStateWithLifecycle()
    val discordViewModel: DiscordPresenceViewModel = hiltViewModel()
    val discord by discordViewModel.state.collectAsStateWithLifecycle()

    // The main list, or one section's page over it. Back (or tapping Settings again) returns to the list.
    var section by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    BackHandler(enabled = section != null) { section = null }
    LaunchedEffect(backToListRequested) {
        if (backToListRequested) {
            section = null
            onBackToListHandled()
        }
    }

    AnimatedContent(
        targetState = section,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        transitionSpec = {
            val forward = targetState != null
            val enter =
                slideInHorizontally(tween(PAGE_MS)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(PAGE_MS))
            val exit =
                slideOutHorizontally(tween(PAGE_MS)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(PAGE_MS))
            enter togetherWith exit
        },
        label = "settings-page",
    ) { shown ->
        if (shown == null) {
            SettingsList(
                summary =
                    SettingsSummary(
                        theme = "${themePalette.displayName} \u00b7 ${uiState.themeMode.displayName}",
                        accent = uiState.accentColor.displayName,
                        localMusic = localMusicSummary(localFolders.size, localTrackCount),
                        gestures =
                            "Swipe right: ${gestures.songSwipeRight.label} \u00b7 " +
                                "left: ${gestures.songSwipeLeft.label}",
                        storage =
                            formatSize(cacheUsage.artworkBytes + cacheUsage.songBytes + lyricsBytes) +
                                " used \u00b7 songs ${formatMb(uiState.cacheMaxMb)}, " +
                                "covers ${formatMb(artworkCacheMb)}",
                        downloads =
                            if (downloadedItems.isEmpty()) {
                                "Nothing downloaded yet"
                            } else {
                                "${downloadedItems.size} songs \u00b7 ${formatSize(downloadedBytes)}"
                            },
                        discord = if (discord.enabled) "On \u00b7 Listening to CLIBeats" else "Off",
                        backup = lastBackupAt?.let { "Last saved ${backupTime(it)}" } ?: "Not saved yet",
                    ),
                palette = themePalette,
                shownDark = uiState.themeMode.resolve(isSystemInDarkTheme()) != CliBeatsThemeMode.LIGHT,
                highQualityStreaming = uiState.highQualityStreaming,
                onHighQualityChange = viewModel::setHighQualityStreaming,
                onOpen = { section = it },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                SettingsPageHeader(title = shown.title, onBack = { section = null })
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                ) {
                    when (shown) {
                        SettingsSection.APPEARANCE ->
                            ThemeSettingsBlock(
                                uiState = uiState,
                                palette = themePalette,
                                onPaletteChange = viewModel::setThemePalette,
                                onThemeModeChange = viewModel::setThemeMode,
                                onAccentColorChange = viewModel::setAccentColor,
                                onCustomAccentChange = viewModel::setCustomAccent,
                            )
                        SettingsSection.LOCAL_MUSIC ->
                            LocalLibrarySettingsBlock(
                                localFolders = localFolders,
                                scanStatus = scanStatus,
                                localTrackCount = localTrackCount,
                                onAddFolder = { pickFolder.launch(null) },
                                onRemoveFolder = { folderToRemove = it },
                                onRescan = viewModel::rescanLocalMusic,
                            )
                        SettingsSection.GESTURES -> GesturesSettingsBlock(gesturesViewModel)
                        SettingsSection.STORAGE ->
                            StorageSettingsBlock(
                                cacheUsage = cacheUsage,
                                artworkCacheMb = artworkCacheMb,
                                songCacheMb = uiState.cacheMaxMb,
                                downloadedBytes = downloadedBytes,
                                onArtworkLimitChange = viewModel::setArtworkCacheMb,
                                onSongLimitChange = viewModel::setSongCacheMb,
                                onClearArtworkCache = { confirmClearArtwork = true },
                                onClearSongCache = { confirmClearSongs = true },
                                lyrics =
                                    LyricsStorage(
                                        songs = lyricsCount,
                                        bytes = lyricsBytes,
                                        limit = viewModel.lyricsLimit,
                                        onClear = { confirmClearLyrics = true },
                                    ),
                            )
                        SettingsSection.BACKUP ->
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
                        SettingsSection.DOWNLOADS ->
                            DownloadsSettingsBlock(
                                downloadedItems = downloadedItems,
                                downloadedBytes = downloadedBytes,
                                onDeleteDownload = viewModel::deleteDownload,
                                onClearAll = { confirmClearDownloads = true },
                            )
                        SettingsSection.DISCORD -> DiscordSettingsBlock(discordViewModel)
                        SettingsSection.UPDATES -> UpdateSection()
                    }
                }
            }
        }
    }
}

private fun localMusicSummary(
    folders: Int,
    tracks: Int,
): String =
    when (folders) {
        0 -> "No folders chosen"
        1 -> "1 folder \u00b7 $tracks ${if (tracks == 1) "song" else "songs"}"
        else -> "$folders folders \u00b7 $tracks ${if (tracks == 1) "song" else "songs"}"
    }

/** What each row of the main list says is set now. */
@Suppress("LongParameterList")
private class SettingsSummary(
    val theme: String,
    val accent: String,
    val localMusic: String,
    val gestures: String,
    val storage: String,
    val downloads: String,
    val backup: String,
    val discord: String,
)

/** Settings' main list: the theme up top, then each section in a few groups, then About. */
@Suppress("LongParameterList", "LongMethod")
@Composable
private fun SettingsList(
    summary: SettingsSummary,
    palette: com.clibeats.presentation.theme.ThemePalette,
    shownDark: Boolean,
    highQualityStreaming: Boolean,
    onHighQualityChange: (Boolean) -> Unit,
    onOpen: (SettingsSection) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp),
        )
        ThemeCard(
            palette = palette,
            shownDark = shownDark,
            theme = summary.theme,
            accent = summary.accent,
            onClick = { onOpen(SettingsSection.APPEARANCE) },
        )
        SettingsGroup(
            label = "Listening",
            rows =
                listOf(
                    {
                        SettingsSwitchRow(
                            icon = Icons.Outlined.GraphicEq,
                            title = "High quality streaming",
                            summary = "Prefer high-bitrate audio (uses more data)",
                            checked = highQualityStreaming,
                            onCheckedChange = onHighQualityChange,
                        )
                    },
                    {
                        SettingsNavRow(Icons.Outlined.Swipe, "Gestures", summary.gestures) {
                            onOpen(SettingsSection.GESTURES)
                        }
                    },
                    {
                        SettingsNavRow(Icons.Outlined.Folder, "Local music", summary.localMusic) {
                            onOpen(SettingsSection.LOCAL_MUSIC)
                        }
                    },
                ),
        )
        SettingsGroup(
            label = "Your data",
            rows =
                listOf(
                    {
                        SettingsNavRow(Icons.Outlined.Storage, "Storage & cache", summary.storage) {
                            onOpen(SettingsSection.STORAGE)
                        }
                    },
                    {
                        SettingsNavRow(Icons.Outlined.DownloadForOffline, "Downloads", summary.downloads) {
                            onOpen(SettingsSection.DOWNLOADS)
                        }
                    },
                    {
                        SettingsNavRow(Icons.Outlined.Backup, "Backup", summary.backup) {
                            onOpen(SettingsSection.BACKUP)
                        }
                    },
                ),
        )
        SettingsGroup(
            label = "App",
            rows =
                listOf(
                    {
                        SettingsNavRow(Icons.Outlined.Headphones, "Discord", summary.discord) {
                            onOpen(SettingsSection.DISCORD)
                        }
                    },
                    {
                        SettingsNavRow(
                            Icons.Outlined.SystemUpdate,
                            "Updates",
                            "CLIBeats v${BuildConfig.VERSION_NAME} \u00b7 check for a new version",
                        ) { onOpen(SettingsSection.UPDATES) }
                    },
                ),
        )
        AboutSettingsBlock()
    }
}

/** The current theme, big, at the top of Settings: its colours, name and accent; opens Appearance. */
@Composable
private fun ThemeCard(
    palette: com.clibeats.presentation.theme.ThemePalette,
    shownDark: Boolean,
    theme: String,
    accent: String,
    onClick: () -> Unit,
) {
    val accentColor = LocalAccentColor.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(SettingsShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, accentColor, SettingsShape)
                .clickable(role = Role.Button, onClickLabel = "Change theme", onClick = onClick)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .background(if (shownDark) darkOrb(palette) else lightOrb(palette), SettingsShape)
                    .border(1.dp, accentColor, SettingsShape),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "> THEME",
                style = MaterialTheme.typography.titleMedium,
                color = accentColor,
            )
            Text(text = theme, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "Accent: $accent \u00b7 tap to change colours",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = "[ OPEN ]", style = MaterialTheme.typography.labelMedium, color = accentColor)
    }
}

@Composable
private fun AboutSettingsBlock() {
    val context = LocalContext.current
    SettingsPanel(label = "About") {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Source: github.com/Omprakash-p06/clibeats",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Source code on GitHub" }
                            .clickable(role = Role.Button) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                                }
                            }.padding(vertical = 4.dp),
                )
                // Copies the repository link, to paste it anywhere.
                val clipboard = LocalClipboardManager.current
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(GITHUB_URL))
                        Toast.makeText(context, "GitHub link copied", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy GitHub link",
                        tint = LocalAccentColor.current,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
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
