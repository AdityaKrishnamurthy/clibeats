@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:multiline-expression-wrapping",
    "MagicNumber",
)

package com.clibeats.presentation.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsSurfaceVariant
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Persistent bottom player bar — always visible across all main screens.
 *
 * Terminal-style panel:
 * - Rendered as a [TuiBlock] titled "Playing" that highlights when active
 * - Controls: SkipPrevious · PlayArrow/Pause (accent) · SkipNext · Repeat
 * - Seekable progress bar with `m:ss / m:ss` timestamp (Spotify TUI style)
 *
 * @param progress     0.0 – 1.0 progress fraction (for display)
 * @param currentMs    Actual current position in ms (for timestamp display)
 * @param totalMs      Track duration in ms (for timestamp display + seek calc)
 * @param onSeek       Called with target position ms when user taps/drags bar
 */
@Suppress("FunctionNaming", "LongMethod", "LongParameterList")
@Composable
fun PlayerBar(
    trackTitle: String = "Nothing Playing",
    artist: String = "",
    isPlaying: Boolean = false,
    progress: Float = 0f,
    currentMs: Long = 0L,
    totalMs: Long = 0L,
    artworkContent: (@Composable () -> Unit)? = null,
    onPlayPauseClick: () -> Unit = {},
    onSkipNextClick: () -> Unit = {},
    onSkipPreviousClick: () -> Unit = {},
    // Songs waiting after the current one; shown as a small count on the queue button.
    upNextCount: Int = 0,
    onQueueClick: () -> Unit = {},
    onSeek: (positionMs: Long) -> Unit = {},
    modifier: Modifier = Modifier,
    onExpandClick: () -> Unit = {},
) {
    val accent = LocalAccentColor.current
    val playPauseDescription = if (isPlaying) "Pause $trackTitle" else "Play $trackTitle"

    TuiBlock(
        title = "Playing",
        isActive = isPlaying,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Artwork + title: tap opens the full-screen Now Playing view
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClickLabel = "Open now playing", onClick = onExpandClick),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(36.dp)) {
                        artworkContent?.invoke()
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = trackTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CliBeatsTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (artist.isNotBlank()) {
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = CliBeatsTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onSkipPreviousClick,
                        modifier = Modifier.semantics { contentDescription = "Skip previous" },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SkipPrevious,
                            contentDescription = null,
                            tint = CliBeatsTextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.semantics { contentDescription = playPauseDescription },
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    IconButton(
                        onClick = onSkipNextClick,
                        modifier = Modifier.semantics { contentDescription = "Skip next" },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SkipNext,
                            contentDescription = null,
                            tint = CliBeatsTextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    IconButton(
                        onClick = onQueueClick,
                        modifier = Modifier.semantics { contentDescription = "Queue, $upNextCount up next" },
                    ) {
                        BadgedBox(
                            badge = {
                                if (upNextCount > 0) {
                                    Badge(containerColor = accent, contentColor = CliBeatsBackground) {
                                        Text(if (upNextCount > MAX_BADGE) "$MAX_BADGE+" else "$upNextCount")
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.QueueMusic,
                                contentDescription = null,
                                tint = CliBeatsTextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            MiniSeekBar(progress = progress, currentMs = currentMs, totalMs = totalMs, onSeek = onSeek)
        }
    }
}

/**
 * Elapsed time · seek bar · duration. Touch anywhere on the bar and it starts scrubbing right away; while held,
 * a bubble above the finger (and the elapsed time) shows the exact second it will jump to, and letting go
 * seeks there. Positions snap to whole seconds, so what you see is exactly where it goes.
 */
@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
private fun MiniSeekBar(
    progress: Float,
    currentMs: Long,
    totalMs: Long,
    onSeek: (Long) -> Unit,
) {
    val accent = LocalAccentColor.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val total by rememberUpdatedState(totalMs)
    val seek by rememberUpdatedState(onSeek)
    // Where the finger is (while held), then where playback was asked to go until it gets there.
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var pendingMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(pendingMs) {
        if (pendingMs != null) {
            delay(PENDING_TIMEOUT_MS)
            pendingMs = null
        }
    }
    LaunchedEffect(currentMs) {
        pendingMs?.let { if (abs(currentMs - it) < CATCH_UP_MS) pendingMs = null }
    }

    val shownMs = scrubMs ?: pendingMs ?: currentMs
    val fraction =
        if (totalMs > 0L) (shownMs.toFloat() / totalMs).coerceIn(0f, 1f) else progress.coerceIn(0f, 1f)
    val scrubbing = scrubMs != null

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = formatPlayerTime(shownMs),
            style = MaterialTheme.typography.labelSmall,
            color = if (scrubbing) accent else CliBeatsTextSecondary,
            modifier = Modifier.padding(end = 6.dp),
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(SEEK_TOUCH_DP.dp)
                    .semantics {
                        contentDescription = "Seek"
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                        setProgress { target ->
                            if (total > 0L) seek((target * total).toLong())
                            total > 0L
                        }
                    }.pointerInput(Unit) {
                        fun msAt(x: Float): Long {
                            val raw = (x / size.width).coerceIn(0f, 1f) * total
                            return ((raw / MS_PER_SECOND).roundToLong() * MS_PER_SECOND).coerceIn(0L, total)
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            if (total <= 0L) return@awaitEachGesture
                            down.consume()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            var target = msAt(down.position.x)
                            scrubMs = target
                            do {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                val held = change?.pressed == true
                                if (held) {
                                    change.consume()
                                    target = msAt(change.position.x)
                                    scrubMs = target
                                }
                            } while (held)
                            seek(target)
                            pendingMs = target
                            scrubMs = null
                        }
                    },
            contentAlignment = Alignment.CenterStart,
        ) {
            val trackColor = CliBeatsSurfaceVariant
            Canvas(modifier = Modifier.fillMaxWidth().height(SEEK_TOUCH_DP.dp)) {
                val y = size.height / 2
                val stroke = (if (scrubbing) BAR_ACTIVE_DP else BAR_DP).dp.toPx()
                val x = fraction * size.width
                val thumb = (if (scrubbing) THUMB_ACTIVE_DP else THUMB_DP).dp.toPx()
                drawLine(trackColor, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
                drawLine(accent, Offset(0f, y), Offset(x, y), stroke, StrokeCap.Round)
                drawCircle(accent, radius = thumb, center = Offset(x, y))
            }
            if (scrubbing) {
                // Time bubble that follows the finger, kept inside the bar's width.
                val gapPx = with(density) { BUBBLE_GAP_DP.dp.roundToPx() }
                Text(
                    text = formatPlayerTime(shownMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier =
                        Modifier
                            .zIndex(1f)
                            .layout { measurable, constraints ->
                                val chip = measurable.measure(Constraints())
                                val width = constraints.maxWidth
                                layout(0, 0) {
                                    val x =
                                        (fraction * width - chip.width / 2f)
                                            .roundToInt()
                                            .coerceIn(0, (width - chip.width).coerceAtLeast(0))
                                    // This node sits at the bar's vertical centre: lift the bubble above the bar.
                                    chip.place(x, -chip.height - gapPx - SEEK_TOUCH_DP.dp.roundToPx() / 2)
                                }
                            },
                )
            }
        }
        Text(
            text = formatPlayerTime(totalMs),
            style = MaterialTheme.typography.labelSmall,
            color = CliBeatsTextSecondary,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

private const val SEEK_TOUCH_DP = 24
private const val BAR_DP = 3
private const val BAR_ACTIVE_DP = 5
private const val THUMB_DP = 4
private const val THUMB_ACTIVE_DP = 7
private const val BUBBLE_GAP_DP = 2
private const val MS_PER_SECOND = 1_000L
private const val PENDING_TIMEOUT_MS = 1_500L
private const val CATCH_UP_MS = 1_500L

/** Format milliseconds as `m:ss` or `h:mm:ss`. */
private fun formatPlayerTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1_000L
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private const val MAX_BADGE = 99
