@file:Suppress("ktlint:standard:function-naming")

package com.clibeats.presentation.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import kotlin.math.abs
import kotlin.random.Random

/**
 * Waveform seek area (Poweramp-style): bars across the full width with the transport controls on top.
 *
 * Drag anywhere on it to scrub. Playback keeps running while you drag and its position stays marked;
 * coming back near it snaps the cursor onto it (with a tick), and letting go there doesn't seek. Letting go
 * anywhere else seeks once. The bars are a stable pattern per song, not decoded audio.
 */
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")
@Composable
fun WaveformScrubber(
    trackKey: String?,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = CliBeatsAccent
    val idle = CliBeatsTextSecondary.copy(alpha = IDLE_BAR_ALPHA)
    val liveMarker = CliBeatsTextPrimary.copy(alpha = LIVE_MARKER_ALPHA)
    val bars = remember(trackKey) { waveformFor(trackKey) }
    val haptics = LocalHapticFeedback.current
    val snapPx = with(LocalDensity.current) { SNAP_DP.dp.toPx() }

    val live = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val currentLive by rememberUpdatedState(live)
    val currentDuration by rememberUpdatedState(durationMs)
    var fingerFraction by remember { mutableStateOf<Float?>(null) }
    var snapped by remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(WAVEFORM_HEIGHT_DP.dp)
                .semantics { contentDescription = "Seek: drag across the waveform" }
                .pointerInput(trackKey) {
                    fun update(x: Float) {
                        val raw = (x / size.width).coerceIn(0f, 1f)
                        val nowSnapped = abs(raw - currentLive) * size.width < snapPx
                        if (nowSnapped && !snapped) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        snapped = nowSnapped
                        fingerFraction = raw
                    }
                    var x = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            x = start.x
                            update(x)
                        },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            x += dx
                            update(x)
                        },
                        onDragEnd = {
                            val target = fingerFraction
                            if (target != null && !snapped && currentDuration > 0) {
                                onSeek((target * currentDuration).toLong())
                            }
                            fingerFraction = null
                            snapped = false
                        },
                        onDragCancel = {
                            fingerFraction = null
                            snapped = false
                        },
                    )
                },
    ) {
        val scrub = fingerFraction?.let { if (snapped) live else it }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = size.width / bars.size
            val barWidth = step * BAR_FILL
            val maxHeight = size.height * BAR_MAX_HEIGHT
            bars.forEachIndexed { index, amplitude ->
                val center = (index + HALF) / bars.size
                val played = center <= live
                val inScrubRange = scrub != null && center in minOf(live, scrub)..maxOf(live, scrub)
                val color =
                    when {
                        inScrubRange -> accent.copy(alpha = SCRUB_RANGE_ALPHA)
                        played -> accent
                        else -> idle
                    }
                val height = maxHeight * amplitude
                drawRoundRect(
                    color = color,
                    topLeft = Offset(index * step + (step - barWidth) / 2, (size.height - height) / 2),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth / 2),
                )
            }
            if (scrub != null) {
                // Where playback is now (keeps moving) and where the finger is.
                drawLine(liveMarker, Offset(live * size.width, 0f), Offset(live * size.width, size.height), 1.dp.toPx())
                drawLine(accent, Offset(scrub * size.width, 0f), Offset(scrub * size.width, size.height), 2.dp.toPx())
            }
        }

        content()

        val shownMs = ((scrub ?: live) * durationMs).toLong()
        TimeChip(
            text = if (scrub != null && snapped) "${formatTime(shownMs)} ·now" else formatTime(shownMs),
            highlighted = scrub != null,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        TimeChip(text = formatTime(durationMs), highlighted = false, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Suppress("FunctionNaming")
@Composable
private fun TimeChip(
    text: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (highlighted) CliBeatsAccent else CliBeatsTextSecondary,
        modifier =
            modifier
                .background(CliBeatsBackground.copy(alpha = CHIP_ALPHA), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

internal fun formatTime(ms: Long): String {
    val totalSec = ms.coerceAtLeast(0L) / MS_PER_SECOND
    return "%d:%02d".format(totalSec / SECONDS_PER_MINUTE, totalSec % SECONDS_PER_MINUTE)
}

/** A smooth, music-like bar pattern that's the same every time for the same song. */
internal fun waveformFor(
    key: String?,
    count: Int = BAR_COUNT,
): List<Float> {
    val random = Random(key?.hashCode() ?: 0)
    var values = List(count) { MIN_AMPLITUDE + random.nextFloat() * (1f - MIN_AMPLITUDE) }
    repeat(SMOOTHING_PASSES) {
        values =
            values.indices.map { i ->
                (values[maxOf(0, i - 1)] + values[i] * 2 + values[minOf(count - 1, i + 1)]) / SMOOTHING_WEIGHT
            }
    }
    val peak = values.max()
    return values.mapIndexed { i, v ->
        // Quieter intro and outro, like most tracks.
        val edge = minOf(i, count - 1 - i).toFloat() / (count * FADE_FRACTION)
        (v / peak * minOf(1f, MIN_EDGE_LEVEL + edge)).coerceIn(MIN_AMPLITUDE, 1f)
    }
}

private const val WAVEFORM_HEIGHT_DP = 176
private const val BAR_COUNT = 64
private const val BAR_FILL = 0.62f
private const val BAR_MAX_HEIGHT = 0.92f
private const val MIN_AMPLITUDE = 0.12f
private const val SMOOTHING_PASSES = 2
private const val SMOOTHING_WEIGHT = 4f
private const val HALF = 0.5f
private const val FADE_FRACTION = 0.08f
private const val MIN_EDGE_LEVEL = 0.35f
private const val SNAP_DP = 14
private const val IDLE_BAR_ALPHA = 0.28f
private const val SCRUB_RANGE_ALPHA = 0.45f
private const val LIVE_MARKER_ALPHA = 0.7f
private const val CHIP_ALPHA = 0.85f
private const val MS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
