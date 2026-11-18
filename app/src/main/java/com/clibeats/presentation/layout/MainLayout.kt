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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.imageLoader
import coil.request.ImageRequest
import com.clibeats.presentation.component.ARTWORK_FULL_PX
import com.clibeats.presentation.component.PlayerBar
import com.clibeats.presentation.component.TrackArtwork
import com.clibeats.presentation.component.artworkUrlFor
import com.clibeats.presentation.player.NowPlayingScreen
import com.clibeats.presentation.player.PlayerViewModel
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsDivider
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsTextSecondary

/**
 * Root layout shell for CLIBeats.
 *
 * Contains:
 * - Adaptive navigation (Rail on compact/medium, Drawer on expanded)
 * - TopAppBar (48dp, flat, no elevation)
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
    BackHandler(enabled = showNowPlaying) { showNowPlaying = false }

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
            Column(modifier = Modifier.fillMaxSize()) {
                CliBeatsTopAppBar(
                    onSearchClick = { onDestinationSelected(NavDestination.Search) },
                )

                HorizontalDivider(
                    thickness = 1.dp,
                    color = CliBeatsDivider,
                )

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
                )
            }
        }

        // ── Full-screen Now Playing overlay (slides up over everything) ──
        AnimatedVisibility(
            visible = showNowPlaying,
            enter = slideInVertically(tween(NOW_PLAYING_ANIM_MS)) { it },
            exit = slideOutVertically(tween(NOW_PLAYING_ANIM_MS)) { it },
        ) {
            NowPlayingScreen(
                viewModel = playerViewModel,
                onClose = { showNowPlaying = false },
                onOpenQueue = {
                    showNowPlaying = false
                    onDestinationSelected(NavDestination.Queue)
                },
            )
        }

        // ── Repeat/shuffle feedback, above the mini player and over Now Playing ──
        PlayerToast(
            playerViewModel = playerViewModel,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = TOAST_BOTTOM_DP.dp),
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun PlayerToast(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val message by playerViewModel.toast.collectAsState()
    // Keep the last text while fading out so the box doesn't empty mid-animation.
    val last = remember { arrayOf("") }
    message?.let { last[0] = it }
    val shown = last[0]
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(TOAST_FADE_MS)),
        exit = fadeOut(tween(TOAST_FADE_MS)),
        modifier = modifier,
    ) {
        Text(
            text = "[ $shown ]",
            style = MaterialTheme.typography.labelMedium,
            color = CliBeatsAccent,
            modifier = Modifier
                .semantics { liveRegion = LiveRegionMode.Polite }
                .background(CliBeatsSurface)
                .border(1.dp, CliBeatsAccent)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

private const val NOW_PLAYING_ANIM_MS = 250
private const val TOAST_FADE_MS = 120
private const val TOAST_BOTTOM_DP = 160

@Suppress("FunctionNaming")
@Composable
private fun PlayerBarHost(
    playerViewModel: PlayerViewModel,
    onExpandClick: () -> Unit,
) {
    val playbackState by playerViewModel.playbackState.collectAsState()
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
        repeatMode = playbackState.repeatMode,
        onRepeatClick = playerViewModel::onRepeatClick,
        onExpandClick = onExpandClick,
    )
}

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

@Suppress("FunctionNaming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CliBeatsTopAppBar(onSearchClick: () -> Unit = {}) {
    TopAppBar(
        modifier = Modifier.height(48.dp),
        title = {
            Text(
                text = "CLIBeats",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        navigationIcon = {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "Open navigation",
                    tint = CliBeatsTextSecondary,
                )
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Open search",
                    tint = CliBeatsTextSecondary,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = CliBeatsSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        scrollBehavior = null,
    )
}
