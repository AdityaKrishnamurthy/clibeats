@file:Suppress("ktlint:standard:function-naming", "ForbiddenImport")

package com.clibeats.presentation.layout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import coil.request.ImageRequest
import com.clibeats.domain.model.PlaybackState
import com.clibeats.presentation.component.PlayerBar
import com.clibeats.presentation.component.TrackArtwork
import com.clibeats.presentation.navigation.MainTab
import com.clibeats.presentation.player.DownloadBanner
import com.clibeats.presentation.player.NowPlayingScreen
import com.clibeats.presentation.player.PlayerViewModel
import com.clibeats.presentation.theme.CliBeatsDestructive
import com.clibeats.presentation.theme.CliBeatsDivider
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor
import com.clibeats.presentation.update.UpdateStatusBar
import com.clibeats.util.ARTWORK_FULL_PX
import com.clibeats.util.artworkUrlFor

@Suppress("FunctionNaming", "LongMethod", "LongParameterList")
@Composable
fun MainLayout(
    activeTab: MainTab = MainTab.HOME,
    breadcrumb: String = "~/home",
    canGoBack: Boolean = false,
    onBack: () -> Unit = {},
    onTabSelected: (MainTab) -> Unit = {},
    onQueueClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    playerViewModel: PlayerViewModel = hiltViewModel(),
    content: @Composable () -> Unit = {},
) {
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                buildNavItems(
                    activeTab = activeTab,
                    onTabSelected = onTabSelected,
                )
            },
            navigationSuiteColors =
                NavigationSuiteDefaults.colors(
                    navigationRailContainerColor = CliBeatsSurface,
                    navigationDrawerContainerColor = CliBeatsSurface,
                    navigationBarContainerColor = CliBeatsSurface,
                ),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                CliBeatsTopAppBar(
                    breadcrumb = breadcrumb,
                    canGoBack = canGoBack,
                    onBack = onBack,
                    onQueueClick = onQueueClick,
                    onSettingsClick = onSettingsClick,
                )

                HorizontalDivider(color = CliBeatsDivider, thickness = 1.dp)

                UpdateStatusBar()

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                ) {
                    content()
                }

                PlayerBarHost(
                    playerViewModel = playerViewModel,
                    onExpandClick = { showNowPlaying = true },
                    onQueueClick = onQueueClick,
                )
            }
        }

        AnimatedVisibility(
            visible = showNowPlaying,
            enter = slideInVertically(tween(NOW_PLAYING_ANIM_MS)) { it },
            exit = slideOutVertically(tween(NOW_PLAYING_ANIM_MS)) { it },
        ) {
            BackHandler { showNowPlaying = false }
            NowPlayingScreen(
                viewModel = playerViewModel,
                onClose = { showNowPlaying = false },
                onOpenQueue = {
                    showNowPlaying = false
                    onQueueClick()
                },
            )
        }
    }
}

private const val NOW_PLAYING_ANIM_MS = 250

@Suppress("FunctionNaming")
@Composable
private fun PlayerBarHost(
    playerViewModel: PlayerViewModel,
    onExpandClick: () -> Unit,
    onQueueClick: () -> Unit,
) {
    val playbackState by playerViewModel.playbackMetadata.collectAsStateWithLifecycle()
    val positionState = playerViewModel.positionMs.collectAsStateWithLifecycle()
    val playerMessage by playerViewModel.playerMessage.collectAsStateWithLifecycle()
    val queueSize = playerViewModel.queue.collectAsStateWithLifecycle().value.size
    val notice by playerViewModel.notice.collectAsStateWithLifecycle()
    val downloadBanner by playerViewModel.downloadBanner.collectAsStateWithLifecycle()
    val banner =
        when {
            playerMessage != null -> DownloadBanner(text = playerMessage!!, isError = true)
            notice != null && (downloadBanner == null || !notice!!.startsWith("Downloading ")) ->
                DownloadBanner(text = notice!!)
            else -> downloadBanner
        }
    PlayerMessageLine(banner)
    val context = LocalContext.current
    val artworkUrl = playbackState.currentTrack?.artworkUrl
    LaunchedEffect(artworkUrl) {
        val hiRes = artworkUrlFor(artworkUrl, ARTWORK_FULL_PX) ?: return@LaunchedEffect
        context.imageLoader.enqueue(ImageRequest.Builder(context).data(hiRes).build())
    }
    val trackDuration = playbackState.durationMs.takeIf { it > 0 } ?: playbackState.currentTrack?.durationMs ?: 0L
    PlayerBar(
        trackTitle = playbackState.currentTrack?.title ?: "Not playing",
        artist = playbackState.currentTrack?.artist ?: "",
        isPlaying = playbackState.isPlaying,
        positionMsProvider = { positionState.value },
        totalMs = trackDuration,
        artworkContent =
            playbackState.currentTrack?.artworkUrl?.let { url ->
                {
                    TrackArtwork(artworkUrl = url, size = 36)
                }
            },
        onPlayPauseClick = playerViewModel::onPlayPauseClick,
        onSkipNextClick = playerViewModel::onSkipNextClick,
        onSkipPreviousClick = playerViewModel::onSkipPreviousClick,
        onSeek = playerViewModel::seekTo,
        upNextCount = (queueSize - playbackState.currentIndex - 1).coerceAtLeast(0),
        onQueueClick = onQueueClick,
        onExpandClick = onExpandClick,
        source = playingFromLabel(playbackState),
    )
}

@Suppress("FunctionNaming")
@Composable
private fun PlayerMessageLine(banner: DownloadBanner?) {
    val last = remember { arrayOf(DownloadBanner(text = "", isError = true)) }
    banner?.let { last[0] = it }
    val active = last[0]
    val accent = LocalAccentColor.current
    val color = if (active.isError) CliBeatsDestructive else accent
    AnimatedVisibility(visible = banner != null, enter = fadeIn(), exit = fadeOut()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .background(CliBeatsSurface)
                    .border(1.dp, color),
        ) {
            Text(
                text = if (active.isError) "! ${active.text}" else "> ${active.text}",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            val pct = active.progressPercent
            if (pct != null) {
                if (pct > 0) {
                    LinearProgressIndicator(
                        progress = { pct / PROGRESS_SCALE },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = accent,
                        trackColor = CliBeatsDivider,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = accent,
                        trackColor = CliBeatsDivider,
                    )
                }
            }
        }
    }
}

private const val PROGRESS_SCALE = 100f

private fun NavigationSuiteScope.buildNavItems(
    activeTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
) {
    MainTab.entries.forEach { tab ->
        item(
            selected = tab == activeTab,
            onClick = { onTabSelected(tab) },
            icon = {
                Icon(imageVector = tab.icon, contentDescription = null)
            },
            label = {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                )
            },
        )
    }
}

@Suppress("FunctionNaming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CliBeatsTopAppBar(
    breadcrumb: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onQueueClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    TopAppBar(
        modifier = Modifier.height(48.dp),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = CliBeatsSurface),
        title = {
            Text(
                text = if (canGoBack) breadcrumb else "> clibeats",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            if (canGoBack) {
                Text(
                    text = "< cd ..",
                    style = MaterialTheme.typography.labelMedium,
                    color = CliBeatsTextSecondary,
                    modifier =
                        Modifier
                            .clickable(role = Role.Button, onClick = onBack)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        },
        actions = {
            IconButton(onClick = onQueueClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.QueueMusic,
                    contentDescription = "Open queue",
                    tint = CliBeatsTextSecondary,
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Open settings",
                    tint = CliBeatsTextSecondary,
                )
            }
        },
    )
}

internal fun playingFromLabel(state: PlaybackState): String? =
    when {
        state.currentTrack == null -> null
        state.fromRadio -> "∞ Radio"
        else -> state.queueSource
    }
