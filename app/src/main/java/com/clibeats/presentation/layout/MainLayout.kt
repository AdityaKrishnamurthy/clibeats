@file:Suppress(
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:function-naming",
)

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.imageLoader
import coil.request.ImageRequest
import com.clibeats.presentation.component.ARTWORK_FULL_PX
import com.clibeats.presentation.component.PlayerBar
import com.clibeats.presentation.component.TrackArtwork
import com.clibeats.presentation.component.artworkUrlFor
import com.clibeats.presentation.player.DownloadBanner
import com.clibeats.presentation.player.NowPlayingScreen
import com.clibeats.presentation.player.PlayerViewModel
import com.clibeats.presentation.theme.CliBeatsDestructive
import com.clibeats.presentation.theme.CliBeatsDivider
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.LocalAccentColor
import com.clibeats.presentation.update.UpdateStatusBar

/**
 * Root layout shell for CLIBeats.
 *
 * Contains:
 * - Adaptive navigation (Rail on compact/medium, Drawer on expanded)
 * - Content area slot for screen content
 * - Bottom slot for persistent PlayerBar
 *
 * Per UI-SPEC: Active nav item = CliBeatsAccent. No pill/bubble indicator.
 */
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun MainLayout(
    selectedDestination: NavDestination = NavDestination.Home,
    onDestinationSelected: (NavDestination) -> Unit = {},
    playerViewModel: PlayerViewModel = hiltViewModel(),
    content: @Composable () -> Unit = {},
) {
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                buildNavItems(
                    selectedDestination = selectedDestination,
                    onDestinationSelected = onDestinationSelected,
                )
            },
            navigationSuiteColors = NavigationSuiteDefaults.colors(
                navigationRailContainerColor = CliBeatsSurface,
                navigationDrawerContainerColor = CliBeatsSurface,
                navigationBarContainerColor = CliBeatsSurface,
            ),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            // No top bar: screens start right under the status bar.
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                // Update download progress (only while an update is downloading or ready to install).
                UpdateStatusBar()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    content()
                }

                // ── Persistent Player Bar (own scope: 500ms ticks don't recompose the screen)
                PlayerBarHost(
                    playerViewModel,
                    onExpandClick = { showNowPlaying = true },
                    onQueueClick = { onDestinationSelected(NavDestination.Queue) },
                )
            }
        }

        // ── Full-screen Now Playing overlay (slides up over everything) ──
        AnimatedVisibility(
            visible = showNowPlaying,
            enter = slideInVertically(tween(NOW_PLAYING_ANIM_MS)) { it },
            exit = slideOutVertically(tween(NOW_PLAYING_ANIM_MS)) { it },
        ) {
            // Registered after the screens below, so back closes Now Playing before the Queue or a tab.
            BackHandler { showNowPlaying = false }
            NowPlayingScreen(
                viewModel = playerViewModel,
                onClose = { showNowPlaying = false },
                onOpenQueue = {
                    showNowPlaying = false
                    onDestinationSelected(NavDestination.Queue)
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
    val playbackState by playerViewModel.playbackState.collectAsState()
    val playerMessage by playerViewModel.playerMessage.collectAsState()
    val queueSize = playerViewModel.queue.collectAsState().value.size
    val notice by playerViewModel.notice.collectAsState()
    val downloadBanner by playerViewModel.downloadBanner.collectAsState()
    // Errors (a song that couldn't play) win; live download progress supersedes plain "Downloading ..." notices.
    val banner =
        when {
            playerMessage != null -> DownloadBanner(text = playerMessage!!, isError = true)
            notice != null && (downloadBanner == null || !notice!!.startsWith("Downloading ")) ->
                DownloadBanner(text = notice!!)
            else -> downloadBanner
        }
    PlayerMessageLine(banner)
    // Prefetch the high-res cover as soon as a track starts, so Now Playing opens with it ready.
    val context = LocalContext.current
    val artworkUrl = playbackState.currentTrack?.artworkUrl
    LaunchedEffect(artworkUrl) {
        val hiRes = artworkUrlFor(artworkUrl, ARTWORK_FULL_PX) ?: return@LaunchedEffect
        context.imageLoader.enqueue(ImageRequest.Builder(context).data(hiRes).build())
    }
    // Player-reported duration first: local files may have no duration in metadata.
    val trackDuration = playbackState.durationMs.takeIf { it > 0 } ?: playbackState.currentTrack?.durationMs ?: 0L
    val progress =
        if (trackDuration > 0L) {
            (playbackState.positionMs.toFloat() / trackDuration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    PlayerBar(
        trackTitle = playbackState.currentTrack?.title ?: "Not playing",
        artist = playbackState.currentTrack?.artist ?: "",
        isPlaying = playbackState.isPlaying,
        progress = progress,
        currentMs = playbackState.positionMs,
        totalMs = trackDuration,
        artworkContent = playbackState.currentTrack?.artworkUrl?.let { url ->
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

/** One-line notice above the player bar, e.g. playback errors, queue confirmations, or download progress. */
@Suppress("FunctionNaming")
@Composable
private fun PlayerMessageLine(banner: DownloadBanner?) {
    // Keep the last banner while fading out so the line doesn't empty mid-animation.
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
    selectedDestination: NavDestination,
    onDestinationSelected: (NavDestination) -> Unit,
) {
    NavDestination.mainTabs.forEach { destination ->
        item(
            selected = destination == selectedDestination,
            onClick = { onDestinationSelected(destination) },
            icon = {
                // Label below already names the tab; a description here would be read twice.
                Icon(imageVector = destination.icon, contentDescription = null)
            },
            // Six tabs: small single-line labels so "Playlists"/"Settings" fit at 360dp.
            label = {
                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                )
            },
        )
    }
}

/** "Playing from": autoplay's radio, else where the queue was started ("Search", a playlist), else nothing. */
internal fun playingFromLabel(state: com.clibeats.domain.model.PlaybackState): String? =
    when {
        state.currentTrack == null -> null
        state.fromRadio -> "∞ Radio"
        else -> state.queueSource
    }
