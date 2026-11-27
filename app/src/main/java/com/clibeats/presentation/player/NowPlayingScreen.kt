@file:Suppress("ktlint:standard:function-naming", "MagicNumber")

package com.clibeats.presentation.player

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BluetoothAudio
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.clibeats.domain.model.AudioOutput
import com.clibeats.domain.model.PlaybackState
import com.clibeats.domain.model.RepeatMode
import com.clibeats.presentation.component.AddToPlaylistDialog
import com.clibeats.presentation.component.HintedControl
import com.clibeats.presentation.layout.playingFromLabel
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsBorderInactive
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor
import com.clibeats.util.ARTWORK_FULL_PX
import com.clibeats.util.ARTWORK_THUMB_PX
import com.clibeats.util.artworkUrlFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Full-screen player opened from the bottom PlayerBar. Collects state itself (ticks stay local). */
@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val state by viewModel.playbackMetadata.collectAsStateWithLifecycle()
    val positionState = viewModel.positionMs.collectAsStateWithLifecycle()
    val hint by viewModel.hint.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    val isLiked by viewModel.isCurrentLiked.collectAsStateWithLifecycle()
    val audioOutput = viewModel.audioOutput?.collectAsStateWithLifecycle()?.value
    // Coming back from the system output switcher: show the device that was picked. The switcher is a
    // system pop-up over this screen (no pause/resume), so also re-check when this window gets focus back.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAudioOutput()
        onPauseOrDispose {}
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) { if (windowFocused) viewModel.refreshAudioOutput() }
    val track = state.currentTrack

    // Match the screen to the album art (Symfonium-style): a deep tint of the cover at the top fading into
    // the theme background, and the cover's vivid colour as the accent. Fades between songs.
    val themeBackground = CliBeatsBackground
    val lightTheme = themeBackground.luminance() > LIGHT_THEME_LUMINANCE
    val artColors = rememberArtworkColors(artworkUrlFor(track?.artworkUrl, ARTWORK_THUMB_PX), lightTheme)
    val accent by animateColorAsState(
        targetValue = artColors?.accent ?: LocalAccentColor.current,
        animationSpec = tween(COLOR_FADE_MS),
        label = "accent",
    )
    val topTint by animateColorAsState(
        targetValue =
            artColors?.let { topTintTarget(it, themeBackground, lightTheme) }
                ?: themeBackground,
        animationSpec = tween(COLOR_FADE_MS),
        label = "tint",
    )

    // The quality line sits over the blurred cover under the wash (or the tint gradient on Android 11 and
    // older); pick a colour that stays readable on all of those, not just the raw album colour.
    val statsTarget =
        remember(artColors, themeBackground) {
            artColors?.let { statsColorFor(it, themeBackground, lightTheme) }
        }
    // "PLAYING FROM …": the album colour, softened toward white (black on Light) until it reads clearly.
    val fromTarget =
        remember(artColors, themeBackground) {
            artColors?.let { labelColorFor(it, themeBackground, lightTheme) }
        }
    val fromColor by animateColorAsState(
        targetValue = fromTarget ?: LocalAccentColor.current,
        animationSpec = tween(COLOR_FADE_MS),
        label = "from",
    )
    val statsColor by animateColorAsState(
        targetValue = statsTarget ?: LocalAccentColor.current,
        animationSpec = tween(COLOR_FADE_MS),
        label = "stats",
    )

    // Drag down on the artwork/header to collapse: follows the finger, snaps back if not far/fast enough.
    val dragOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissPx = with(LocalDensity.current) { DISMISS_DRAG_DP.dp.toPx() }
    val gestureSettings = com.clibeats.presentation.component.LocalGestureSettings.current
    val dragToDismiss =
        if (!gestureSettings.nowPlayingSwipeDown) {
            Modifier
        } else {
            Modifier.pointerInput(gestureSettings) {
                val tracker = VelocityTracker()
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        total = 0f
                    },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity().y
                        if (dragOffset.value > dismissPx || velocity > DISMISS_VELOCITY_PX_S) {
                            onClose()
                        } else {
                            scope.launch { dragOffset.animateTo(0f, tween(SNAP_BACK_MS)) }
                        }
                    },
                    onDragCancel = { scope.launch { dragOffset.animateTo(0f, tween(SNAP_BACK_MS)) } },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        total += dy
                        // Track our own cumulative offset: local positions shift as the screen translates.
                        tracker.addPosition(change.uptimeMillis, Offset(0f, total))
                        scope.launch { dragOffset.snapTo((dragOffset.value + dy).coerceAtLeast(0f)) }
                    },
                )
            }
        }
    val closeAction =
        Modifier.semantics {
            customActions = listOf(CustomAccessibilityAction("Close player") { onClose().let { true } })
        }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    // ── Lyrics: 0 = the big cover, 1 = lyrics (the cover shrinks into a row above the controls) ──
    // The finger moves this value directly; the snap animation runs only after letting go, so the two never
    // fight (which used to leave it stuck halfway).
    val lyricsState by viewModel.lyrics.collectAsStateWithLifecycle()
    val showTranslation by viewModel.showTranslation.collectAsStateWithLifecycle()
    val showPronunciation by viewModel.showPronunciation.collectAsStateWithLifecycle()
    val extrasLoading by viewModel.extrasLoading.collectAsStateWithLifecycle()
    val lyricsOpen by viewModel.lyricsOpen.collectAsStateWithLifecycle()
    // Opening Now Playing: back on the lyrics if they were open for this same song, otherwise on the cover.
    var lyricsProgress by remember { mutableFloatStateOf(if (viewModel.lyricsOpenOnEntry()) 1f else 0f) }
    // Reading along when the next song starts: its lyrics come up too.
    LaunchedEffect(track?.id) { if (viewModel.lyricsOpen.value) viewModel.setLyricsOpen(true) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var swiping by remember { mutableStateOf(false) }
    val showArtwork by remember { derivedStateOf { lyricsProgress < 1f || swiping } }
    val showLyrics by remember { derivedStateOf { lyricsProgress > 0f } }
    val compactHeader by remember { derivedStateOf { lyricsProgress >= COMPACT_AT } }
    val lyricsDragPx = with(LocalDensity.current) { LYRICS_DRAG_DP.dp.toPx() }

    fun settleLyrics(
        open: Boolean,
        /** How fast the progress was moving when the finger let go (per second). */
        progressVelocity: Float = 0f,
    ) {
        viewModel.setLyricsOpen(open)
        settleJob?.cancel()
        settleJob =
            scope.launch {
                animate(
                    initialValue = lyricsProgress,
                    targetValue = if (open) 1f else 0f,
                    initialVelocity = progressVelocity,
                    animationSpec = spring(dampingRatio = LYRICS_SPRING_DAMPING, stiffness = Spring.StiffnessMediumLow),
                ) { value, _ -> lyricsProgress = value }
            }
    }
    BackHandler(enabled = lyricsOpen) { settleLyrics(false) }

    // One swipe for the cover and the title area, with or without lyrics open:
    // - up switches between the cover and the lyrics, following the finger;
    // - down closes the player, as before.
    // It lives on parts of the screen that stay put while the header changes, so a drag is never cut off.
    val swipe =
        Modifier.pointerInput(gestureSettings) {
            val tracker = VelocityTracker()
            var total = 0f
            var toggling: Boolean? = null
            var startedOpen = false
            detectVerticalDragGestures(
                onDragStart = {
                    tracker.resetTracking()
                    total = 0f
                    toggling = null
                    startedOpen = lyricsOpen
                    settleJob?.cancel()
                    swiping = true
                },
                onDragEnd = {
                    swiping = false
                    val velocity = tracker.calculateVelocity().y
                    when (toggling) {
                        true ->
                            if (!gestureSettings.nowPlayingSwipeUp) {
                                settleLyrics(lyricsOpen)
                            } else {
                                val moved = if (startedOpen) 1f - lyricsProgress else lyricsProgress
                                val switch =
                                    velocity < -LYRICS_FLING_PX_S ||
                                        (moved > COMPACT_AT && velocity <= LYRICS_FLING_PX_S)
                                // Up moves the progress toward the other side: up from the cover raises it, up from
                                // the lyrics lowers it.
                                val progressVelocity = -velocity / lyricsDragPx * (if (startedOpen) -1f else 1f)
                                settleLyrics(if (switch) !startedOpen else startedOpen, progressVelocity)
                            }
                        false ->
                            if (gestureSettings.nowPlayingSwipeDown &&
                                (dragOffset.value > dismissPx || velocity > DISMISS_VELOCITY_PX_S)
                            ) {
                                onClose()
                            } else {
                                scope.launch { dragOffset.animateTo(0f, tween(SNAP_BACK_MS)) }
                            }
                        null -> settleLyrics(lyricsOpen)
                    }
                },
                onDragCancel = {
                    swiping = false
                    settleLyrics(lyricsOpen)
                    scope.launch { dragOffset.animateTo(0f, tween(SNAP_BACK_MS)) }
                },
                onVerticalDrag = { change, dy ->
                    change.consume()
                    total += dy
                    tracker.addPosition(change.uptimeMillis, Offset(0f, total))
                    if (toggling == null) toggling = dy < 0
                    // Turned off in Settings → Gestures: that direction does nothing.
                    val allowed =
                        if (toggling == true) gestureSettings.nowPlayingSwipeUp else gestureSettings.nowPlayingSwipeDown
                    if (!allowed) {
                        total = 0f
                    } else if (toggling == true) {
                        // How far up the finger has gone, as a share of the full switch.
                        val moved = (-total / lyricsDragPx).coerceIn(0f, 1f)
                        lyricsProgress = if (startedOpen) 1f - moved else moved
                    } else {
                        scope.launch { dragOffset.snapTo(total.coerceAtLeast(0f)) }
                    }
                },
            )
        }

    CompositionLocalProvider(LocalAccentColor provides accent) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = dragOffset.value }
                    // Swallow taps so the screen below doesn't receive them.
                    .clickable(enabled = false) {},
        ) {
            GlassBackground(
                artworkUrl = artworkUrlFor(track?.artworkUrl, ARTWORK_THUMB_PX),
                topTint = topTint,
                base = themeBackground,
                lightTheme = lightTheme,
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Header ──
                Column(
                    modifier = Modifier.fillMaxWidth().then(dragToDismiss).then(closeAction).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Grab handle hints that the screen can be dragged down.
                    Box(
                        Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .background(CliBeatsBorderInactive, RoundedCornerShape(2.dp)),
                    )
                }

                // The queue opens from the button under the controls; no header line, so the cover sits higher.
                Spacer(Modifier.height(4.dp))

                // ── Artwork ──
                // Two layers: the small thumbnail (already cached by the lists, so near-instant) underneath,
                // and the high-res cover on top, transparent until loaded, then crossfading in. Never blank;
                // a failed hi-res attempt (e.g. missing ytimg maxres) just keeps the thumbnail showing.
                // (Don't pass AsyncImage(placeholder=...) here: it replaces the cached-thumbnail placeholder.)
                var artFallback by remember(track?.artworkUrl) { mutableStateOf(false) }
                val artworkDescription = track?.let { "Artwork for ${it.title}" } ?: "No artwork"
                val artSize = if (artFallback) FALLBACK_ART_PX else ARTWORK_FULL_PX
                val context = LocalContext.current
                val artUrl = artworkUrlFor(track?.artworkUrl, artSize)
                val thumbUrl = artworkUrlFor(track?.artworkUrl, ARTWORK_THUMB_PX)
                val hiResRequest =
                    remember(artUrl) {
                        ImageRequest.Builder(context).data(artUrl).crossfade(ART_CROSSFADE_MS).build()
                    }
                // The cover's area takes all the height left over, so the controls and the icons below them
                // sit the same distance from the bottom on every phone; on tall screens the spare room goes
                // above and below the cover instead of piling up under the icons.
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (showLyrics) {
                        LyricsPanel(
                            state = lyricsState,
                            playback = state,
                            isLocalSong = track?.providerId == "local",
                            onSeek = viewModel::seekTo,
                            livePosition = viewModel::livePositionMs,
                            positionTicks = positionState,
                            extras =
                                LyricsExtras(
                                    showTranslation = showTranslation,
                                    showPronunciation = showPronunciation,
                                    loading = extrasLoading,
                                    onToggleTranslation = { viewModel.setShowTranslation(!showTranslation) },
                                    onTogglePronunciation = { viewModel.setShowPronunciation(!showPronunciation) },
                                ),
                            modifier =
                                Modifier.graphicsLayer {
                                    val p = lyricsProgress
                                    alpha = ((p - LYRICS_FADE_FROM) / (1f - LYRICS_FADE_FROM)).coerceIn(0f, 1f)
                                    translationY = (1f - p) * -LYRICS_SLIDE_DP.dp.toPx()
                                },
                        )
                    }
                    if (showArtwork) {
                        Box(
                            modifier =
                                Modifier
                                    // Height first: on short screens the cover shrinks, not spilling over the title.
                                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                                    // Opening lyrics: the cover sinks toward its small spot above the controls.
                                    .graphicsLayer {
                                        val p = lyricsProgress
                                        alpha = (1f - p * ART_FADE_SPEED).coerceIn(0f, 1f)
                                        val scale = 1f - (1f - ART_MIN_SCALE) * p
                                        scaleX = scale
                                        scaleY = scale
                                        translationY = size.height * ART_SINK * p
                                    }.clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, CliBeatsBorderInactive, RoundedCornerShape(8.dp))
                                    .then(swipe)
                                    .then(closeAction)
                                    .semantics { contentDescription = artworkDescription },
                        ) {
                            if (thumbUrl != null) {
                                AsyncImage(
                                    model = thumbUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            if (artUrl != null && artUrl != thumbUrl) {
                                AsyncImage(
                                    model = hiResRequest,
                                    onError = { if (!artFallback) artFallback = true },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Where it's playing from, then title / artist / quality (a small row while lyrics are open) ──
                Box(modifier = Modifier.fillMaxWidth().animateContentSize().then(swipe)) {
                    if (compactHeader) {
                        CompactNowPlaying(
                            title = track?.title ?: "Nothing playing",
                            artist = track?.artist.orEmpty(),
                            quality = compactQualityLine(state),
                            qualityColor = statsColor,
                            thumbUrl = thumbUrl,
                            onClick = { settleLyrics(false) },
                            modifier =
                                Modifier.graphicsLayer {
                                    alpha = ((lyricsProgress - COMPACT_AT) / (1f - COMPACT_AT)).coerceIn(0f, 1f)
                                },
                        )
                    } else {
                        Column(
                            modifier =
                                Modifier.fillMaxWidth().graphicsLayer {
                                    alpha = (1f - lyricsProgress / COMPACT_AT).coerceIn(0f, 1f)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            playingFromLabel(state)?.let { from ->
                                Text(
                                    text = "PLAYING FROM ${from.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fromColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                            Text(
                                text = track?.title ?: "Nothing playing",
                                style = MaterialTheme.typography.headlineSmall,
                                color = CliBeatsTextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                            if (!track?.artist.isNullOrBlank()) {
                                Text(
                                    text = track!!.artist,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = CliBeatsTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = qualityLine(state),
                                style = MaterialTheme.typography.labelSmall,
                                color = statsColor,
                                textAlign = TextAlign.Center,
                            )
                            if (audioOutput != null) {
                                val activity = LocalContext.current
                                AudioOutputChip(
                                    output = audioOutput,
                                    onClick = { viewModel.openOutputSwitcher(activity) },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Waveform seek: scrub without interrupting playback, snaps back to "now"; controls on top ──
                WaveformScrubber(
                    trackKey = track?.id,
                    positionMsProvider = { positionState.value },
                    durationMs = state.durationMs,
                    onSeek = viewModel::seekTo,
                ) {
                    TransportControls(
                        state = state,
                        hint = hint,
                        viewModel = viewModel,
                        backdropColor = themeBackground,
                        modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                    )
                }

                // A little extra room so the action icons sit clear of the waveform.
                Spacer(Modifier.height(52.dp))

                // ── Bottom actions: sleep timer · queue · add to playlist · favourite ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val sleepLabel =
                        when (val t = sleepTimer) {
                            SleepTimer.Off -> null
                            is SleepTimer.Countdown -> "${t.remainingMin}m"
                            SleepTimer.EndOfTrack -> "end"
                        }
                    Row(
                        modifier = Modifier.clickable { showSleepDialog = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Bedtime,
                            contentDescription = "Sleep timer" + (sleepLabel?.let { ": $it" } ?: ""),
                            tint = if (sleepLabel != null) CliBeatsAccent else CliBeatsTextSecondary,
                        )
                        if (sleepLabel != null) {
                            Text(" $sleepLabel", style = MaterialTheme.typography.labelSmall, color = CliBeatsAccent)
                        }
                    }
                    IconButton(onClick = { settleLyrics(!lyricsOpen) }, enabled = track != null) {
                        val hasLyrics = lyricsState != LyricsState.None && lyricsState != LyricsState.Offline
                        Icon(
                            Icons.Outlined.Lyrics,
                            contentDescription = if (lyricsOpen) "Hide lyrics" else "Show lyrics",
                            tint =
                                when {
                                    lyricsOpen -> CliBeatsAccent
                                    hasLyrics -> CliBeatsTextSecondary
                                    else -> CliBeatsTextSecondary.copy(alpha = NO_LYRICS_ALPHA)
                                },
                        )
                    }
                    IconButton(onClick = onOpenQueue) {
                        Icon(Icons.AutoMirrored.Outlined.QueueMusic, "Open queue", tint = CliBeatsTextSecondary)
                    }
                    IconButton(onClick = { showPlaylistDialog = true }, enabled = track != null) {
                        Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, "Add to playlist", tint = CliBeatsTextSecondary)
                    }
                    IconButton(onClick = viewModel::toggleLike, enabled = track != null) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isLiked) "Remove from Favorites" else "Add to Favorites",
                            tint = if (isLiked) CliBeatsAccent else CliBeatsTextSecondary,
                        )
                    }
                }
            }
        }

        if (showSleepDialog) {
            val options = SLEEP_OPTIONS
            AlertDialog(
                onDismissRequest = { showSleepDialog = false },
                modifier = Modifier.border(1.dp, CliBeatsAccent),
                title = { Text("> sleep --timer") },
                text = {
                    Column {
                        options.forEach { (label, minutes) ->
                            Text(
                                text = "[ $label ]",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setSleepTimer(minutes)
                                            showSleepDialog = false
                                        }.padding(vertical = 10.dp),
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showSleepDialog = false }) { Text("Cancel") } },
            )
        }

        if (showPlaylistDialog && track != null) {
            // Shared, themed dialog (same as long-press elsewhere), with inline "+ New playlist".
            AddToPlaylistDialog(track = track, onDismiss = { showPlaylistDialog = false })
        }
    }
}

/**
 * Now Playing while lyrics are open: a small cover, title, artist and quality in one row, with a handle above
 * it. Swipe up (or tap it) to bring the big cover back; swipe down to close the player.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun CompactNowPlaying(
    title: String,
    artist: String,
    quality: String,
    qualityColor: Color,
    thumbUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Show the cover", onClick = onClick)
                .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 28.dp, height = 3.dp).background(CliBeatsBorderInactive, RoundedCornerShape(2.dp)))
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(COMPACT_ART_DP.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, CliBeatsBorderInactive, RoundedCornerShape(6.dp)),
            ) {
                if (thumbUrl != null) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = CliBeatsTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (artist.isNotBlank()) {
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = CliBeatsTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = quality,
                    style = MaterialTheme.typography.labelSmall,
                    color = qualityColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** repeat · prev · play/pause · next · shuffle, drawn over the waveform with a dark backdrop each. */
@Suppress("FunctionNaming", "LongMethod")
@Composable
private fun TransportControls(
    state: PlaybackState,
    hint: ButtonHint?,
    viewModel: PlayerViewModel,
    backdropColor: Color,
    modifier: Modifier = Modifier,
) {
    val backdrop = Modifier.controlBackdrop(backdropColor)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HintedControl(hint = hint?.takeIf { it.target == HintTarget.REPEAT }?.text) {
            IconButton(onClick = viewModel::onRepeatClick, modifier = backdrop) {
                Icon(
                    imageVector =
                        if (state.repeatMode == RepeatMode.ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
                    contentDescription = "Repeat: ${state.repeatMode.name.lowercase()}",
                    tint = if (state.repeatMode == RepeatMode.OFF) CliBeatsTextSecondary else CliBeatsAccent,
                )
            }
        }
        IconButton(onClick = viewModel::onSkipPreviousClick, modifier = Modifier.size(56.dp).then(backdrop)) {
            Icon(
                Icons.Outlined.SkipPrevious,
                contentDescription = "Previous",
                tint = CliBeatsTextPrimary,
                modifier = Modifier.size(32.dp),
            )
        }
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .background(backdropColor, RoundedCornerShape(8.dp))
                    .border(2.dp, CliBeatsAccent, RoundedCornerShape(8.dp))
                    .clickable(onClick = viewModel::onPlayPauseClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                tint = CliBeatsAccent,
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(onClick = viewModel::onSkipNextClick, modifier = Modifier.size(56.dp).then(backdrop)) {
            Icon(Icons.Outlined.SkipNext, "Next", tint = CliBeatsTextPrimary, modifier = Modifier.size(32.dp))
        }
        HintedControl(hint = hint?.takeIf { it.target == HintTarget.SHUFFLE }?.text) {
            IconButton(onClick = viewModel::onShuffleClick, modifier = backdrop) {
                Icon(
                    Icons.Outlined.Shuffle,
                    contentDescription = if (state.shuffleEnabled) "Shuffle: on" else "Shuffle: off",
                    tint = if (state.shuffleEnabled) CliBeatsAccent else CliBeatsTextSecondary,
                )
            }
        }
    }
}

/**
 * Frosted-glass background: the cover itself, filling the screen, heavily blurred, under a wash that keeps
 * text readable and leans toward the album colour. Uses the small cached thumbnail (instant; the blur hides
 * its size). Android 11 and older can't blur, so they keep the album-tinted gradient.
 */
@Suppress("FunctionNaming")
@Composable
private fun GlassBackground(
    artworkUrl: String?,
    topTint: Color,
    base: Color,
    lightTheme: Boolean,
) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to topTint, GRADIENT_END to base)))
    if (artworkUrl == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    Crossfade(targetState = artworkUrl, animationSpec = tween(COLOR_FADE_MS), label = "glass") { url ->
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = GLASS_SCALE
                        scaleY = GLASS_SCALE
                    }.blur(GLASS_BLUR_DP.dp, BlurredEdgeTreatment.Rectangle),
        )
    }
    val wash = if (lightTheme) base else lerp(base, topTint, WASH_TINT)
    val (top, middle, bottom) = if (lightTheme) LIGHT_WASH else DARK_WASH
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to wash.copy(alpha = top),
                WASH_MIDDLE to wash.copy(alpha = middle),
                1f to base.copy(alpha = bottom),
            ),
        ),
    )
}

/** Readable colour for the quality line over the blurred cover and its wash (see [GlassBackground]). */
private fun statsColorFor(
    colors: ArtworkColors,
    themeBackground: Color,
    lightTheme: Boolean,
): Color = readableTextColor(colors.accent, colorsBehindText(colors, themeBackground, lightTheme), lightTheme)

/** The colours small text can land on: the washed, blurred cover and the album tint. */
private fun colorsBehindText(
    colors: ArtworkColors,
    themeBackground: Color,
    lightTheme: Boolean,
): List<Color> {
    val topTint = topTintTarget(colors, themeBackground, lightTheme)
    val wash = if (lightTheme) themeBackground else lerp(themeBackground, topTint, WASH_TINT)
    val washAlpha = (if (lightTheme) LIGHT_WASH else DARK_WASH).second
    return colors.coverColors.map { lerp(it, wash, washAlpha) } + topTint
}

/**
 * The "PLAYING FROM" label's colour. A vivid cover colour (hot pink, neon green) on a tinted background looks
 * harsh and can be unreadable, so it's always softened a little toward white (black on Light), and further
 * until it has [MIN_CONTRAST] against everything behind it. White (black) when nothing tinted is readable.
 */
private fun labelColorFor(
    colors: ArtworkColors,
    themeBackground: Color,
    lightTheme: Boolean,
): Color {
    val behind = colorsBehindText(colors, themeBackground, lightTheme)
    val toward = if (lightTheme) Color.Black else Color.White
    var mix = LABEL_MIN_SOFTEN
    while (mix < 1f) {
        val candidate = lerp(colors.accent, toward, mix)
        if (behind.all { contrastRatio(candidate, it) >= MIN_CONTRAST }) return candidate
        mix += LABEL_SOFTEN_STEP
    }
    return toward
}

/** The album tint blended into the theme background, as used at the top of the screen. */
private fun topTintTarget(
    colors: ArtworkColors,
    themeBackground: Color,
    lightTheme: Boolean,
): Color = lerp(themeBackground, colors.tint, if (lightTheme) LIGHT_TINT else DARK_TINT)

/** Dark disc behind a control drawn over the waveform. */
private fun Modifier.controlBackdrop(color: Color): Modifier {
    val fill = color.copy(alpha = CONTROL_BACKDROP_ALPHA)
    return background(fill, CircleShape)
}

/** `[ 🎧 GALAXY BUDS ]`-style button under the quality line: where the music plays; tap to switch. */
@Suppress("FunctionNaming")
@Composable
private fun AudioOutputChip(
    output: AudioOutput,
    onClick: () -> Unit,
) {
    val icon =
        when (output.kind) {
            AudioOutput.Kind.PHONE -> Icons.Outlined.PhoneAndroid
            AudioOutput.Kind.WIRED -> Icons.Outlined.Headphones
            AudioOutput.Kind.BLUETOOTH -> Icons.Outlined.BluetoothAudio
            AudioOutput.Kind.OTHER -> Icons.Outlined.Speaker
        }
    Row(
        modifier =
            Modifier
                .padding(top = 2.dp)
                .clickable(onClickLabel = "Change audio output", onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .semantics(mergeDescendants = true) { contentDescription = "Playing on ${output.name}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "[ ", style = MaterialTheme.typography.labelSmall, color = CliBeatsTextSecondary)
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CliBeatsTextSecondary,
            modifier = Modifier.size(OUTPUT_ICON_DP.dp),
        )
        Text(
            text = " ${output.name.uppercase()} ]",
            style = MaterialTheme.typography.labelSmall,
            color = CliBeatsTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val OUTPUT_ICON_DP = 14

/**
 * e.g. `[LOCAL] FLAC · 1076 kbps · 44.1 kHz · 24-bit`, `[ONLINE · YouTube Music] OPUS · 160 kbps · 48 kHz`, or
 * `[DOWNLOADED · YouTube Music] …` when the song plays from its offline download.
 */
internal fun qualityLine(state: PlaybackState): String {
    val providerId = state.currentTrack?.providerId ?: return ""
    val source =
        when {
            providerId == "local" -> "[LOCAL]"
            state.fromDownload -> "[DOWNLOADED · ${providerLabel(providerId)}]"
            else -> "[ONLINE · ${providerLabel(providerId)}]"
        }
    val parts =
        listOfNotNull(
            state.codec,
            state.bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" },
            state.sampleRateHz?.let {
                    hz ->
                if (hz % 1000 == 0) "${hz / 1000} kHz" else String.format(java.util.Locale.US, "%.1f kHz", hz / 1000f)
            },
            state.bitDepth?.let { "$it-bit" },
        )
    return if (parts.isEmpty()) source else "$source ${parts.joinToString(" · ")}"
}

/**
 * The same, short enough for the small row above the controls while lyrics are open:
 * `[ONLINE] OPUS 160k · 48kHz`, `[LOCAL] FLAC 1076k · 44.1kHz · 24-bit`, `[DOWNLOADED] OPUS 160k · 48kHz`.
 */
internal fun compactQualityLine(state: PlaybackState): String {
    val providerId = state.currentTrack?.providerId ?: return ""
    val source =
        when {
            providerId == "local" -> "[LOCAL]"
            state.fromDownload -> "[DOWNLOADED]"
            else -> "[ONLINE]"
        }
    val format =
        listOfNotNull(state.codec, state.bitrateKbps?.takeIf { it > 0 }?.let { "${it}k" })
            .joinToString(" ")
            .ifBlank { null }
    val parts =
        listOfNotNull(
            format,
            state.sampleRateHz?.let { hz ->
                if (hz % 1000 == 0) "${hz / 1000}kHz" else String.format(java.util.Locale.US, "%.1fkHz", hz / 1000f)
            },
            state.bitDepth?.let { "$it-bit" },
        )
    return if (parts.isEmpty()) source else "$source ${parts.joinToString(" · ")}"
}

private fun providerLabel(id: String): String =
    when (id) {
        "youtube_music" -> "YouTube Music"
        else -> id.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

private const val DISMISS_DRAG_DP = 120

// Lyrics view: how far a drag goes from cover to lyrics, when the header turns into the small row, and how
// the cover sinks and fades as it goes.
private const val LYRICS_DRAG_DP = 320
private const val LYRICS_FLING_PX_S = 1_200f
private const val LYRICS_SPRING_DAMPING = 0.86f
private const val COMPACT_AT = 0.5f
private const val LYRICS_FADE_FROM = 0.2f
private const val LYRICS_SLIDE_DP = 28
private const val ART_FADE_SPEED = 1.4f
private const val ART_MIN_SCALE = 0.55f
private const val ART_SINK = 0.5f
private const val COMPACT_ART_DP = 52
private const val NO_LYRICS_ALPHA = 0.45f
private const val DISMISS_VELOCITY_PX_S = 1_500f
private const val SNAP_BACK_MS = 200
private const val ART_CROSSFADE_MS = 450
private const val FALLBACK_ART_PX = 480

private val SLEEP_OPTIONS =
    listOf("Off" to 0, "15 min" to 15, "30 min" to 30, "45 min" to 45, "60 min" to 60, "End of track" to null)

private const val COLOR_FADE_MS = 600
private const val LIGHT_THEME_LUMINANCE = 0.5f
private const val DARK_TINT = 0.6f
private const val LIGHT_TINT = 0.28f
private const val GRADIENT_END = 0.8f
private const val CONTROL_BACKDROP_ALPHA = 0.85f
private const val GLASS_BLUR_DP = 56
private const val GLASS_SCALE = 1.15f
private const val WASH_TINT = 0.35f
private const val WASH_MIDDLE = 0.55f
private val DARK_WASH = Triple(0.42f, 0.6f, 0.9f)
private val LIGHT_WASH = Triple(0.62f, 0.74f, 0.92f)

private const val LABEL_MIN_SOFTEN = 0.3f
private const val LABEL_SOFTEN_STEP = 0.05f
