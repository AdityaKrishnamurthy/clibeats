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
    positionMsProvider: () -> Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = WAVEFORM_HEIGHT_DP.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = CliBeatsAccent
    val playedColor = CliBeatsTextPrimary.copy(alpha = PLAYED_BAR_ALPHA)
    val idle = CliBeatsTextSecondary.copy(alpha = IDLE_BAR_ALPHA)
    val liveMarker = CliBeatsTextPrimary.copy(alpha = LIVE_MARKER_ALPHA)
    val bars = remember(trackKey) { waveformFor(trackKey) }
    val haptics = LocalHapticFeedback.current
    val snapPx = with(LocalDensity.current) { SNAP_DP.dp.toPx() }

    val currentPositionProvider by rememberUpdatedState(positionMsProvider)
    val currentDuration by rememberUpdatedState(durationMs)
    var fingerFraction by remember { mutableStateOf<Float?>(null) }
    var snapped by remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = "Seek: drag across the waveform" }
                .pointerInput(trackKey) {
                    fun liveFractionNow(): Float {
                        val d = currentDuration
                        return if (d > 0) (currentPositionProvider().toFloat() / d).coerceIn(0f, 1f) else 0f
                    }

                    fun update(x: Float) {
                        val raw = (x / size.width).coerceIn(0f, 1f)
                        val nowSnapped = abs(raw - liveFractionNow()) * size.width < snapPx
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val live =
                if (currentDuration > 0) {
                    (currentPositionProvider().toFloat() / currentDuration).coerceIn(0f, 1f)
                } else {
                    0f
                }
            val scrub = fingerFraction?.let { if (snapped) live else it }
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
                        played -> playedColor
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
                drawLine(liveMarker, Offset(live * size.width, 0f), Offset(live * size.width, size.height), 1.dp.toPx())
                drawLine(accent, Offset(scrub * size.width, 0f), Offset(scrub * size.width, size.height), 2.dp.toPx())
            }
        }

        content()

        WaveformPositionChip(
            positionMsProvider = positionMsProvider,
            durationMs = durationMs,
            fingerFraction = fingerFraction,
            snapped = snapped,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        TimeChip(text = formatTime(durationMs), highlighted = false, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Suppress("FunctionNaming")
@Composable
private fun WaveformPositionChip(
    positionMsProvider: () -> Long,
    durationMs: Long,
    fingerFraction: Float?,
    snapped: Boolean,
    modifier: Modifier = Modifier,
) {
    val live = if (durationMs > 0) (positionMsProvider().toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val scrub = fingerFraction?.let { if (snapped) live else it }
    val shownMs = ((scrub ?: live) * durationMs).toLong()
    TimeChip(
        text = if (scrub != null && snapped) "${formatTime(shownMs)} ·now" else formatTime(shownMs),
        highlighted = scrub != null,
        modifier = modifier,
    )
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

/**
 * A music-like bar pattern, the same every time for the same song: a quiet intro, a few sections at their own
 * loudness (verse, chorus...) with gentle variation inside each, and a quiet outro. Heights stay in a calm band,
 * so there's no wall of full-height bars and no sudden holes in the middle.
 */
internal fun waveformFor(
    key: String?,
    count: Int = BAR_COUNT,
): List<Float> {
    val random = Random(key?.hashCode() ?: 0)
    // Sections: their lengths and loudness.
    val sections = SECTION_MIN + random.nextInt(SECTION_SPREAD)
    val levels = List(sections) { SECTION_LOW + random.nextFloat() * (SECTION_HIGH - SECTION_LOW) }
    var values =
        List(count) { i ->
            val position = i.toFloat() / (count - 1) * (sections - 1)
            val from = position.toInt().coerceAtMost(sections - 2)
            val t = position - from
            // Smooth step between neighbouring sections.
            val eased = smoothStep(t)
            val level = levels[from] + (levels[from + 1] - levels[from]) * eased
            level + (random.nextFloat() - HALF) * DETAIL
        }
    repeat(SMOOTHING_PASSES) {
        values =
            values.indices.map { i ->
                (values[maxOf(0, i - 1)] + values[i] * 2 + values[minOf(count - 1, i + 1)]) / SMOOTHING_WEIGHT
            }
    }
    val peak = values.max().coerceAtLeast(MIN_AMPLITUDE)
    return values.mapIndexed { i, v ->
        // Quieter intro and outro, like most tracks.
        val edge = (minOf(i, count - 1 - i).toFloat() / (count * FADE_FRACTION)).coerceAtMost(1f)
        val fade = MIN_EDGE_LEVEL + (1f - MIN_EDGE_LEVEL) * smoothStep(edge)
        (v / peak * PEAK_LEVEL * fade).coerceIn(MIN_AMPLITUDE, 1f)
    }
}

private const val WAVEFORM_HEIGHT_DP = 176
private const val BAR_COUNT = 64
private const val BAR_FILL = 0.62f
private const val BAR_MAX_HEIGHT = 0.92f
private const val MIN_AMPLITUDE = 0.12f
private const val SECTION_MIN = 4
private const val SECTION_SPREAD = 3
private const val SECTION_LOW = 0.42f
private const val SECTION_HIGH = 0.88f
private const val DETAIL = 0.42f
private const val PEAK_LEVEL = 0.95f
private const val SMOOTHING_PASSES = 1
private const val SMOOTHING_WEIGHT = 4f
private const val HALF = 0.5f
private const val FADE_FRACTION = 0.12f
private const val MIN_EDGE_LEVEL = 0.2f
private const val SNAP_DP = 14
private const val IDLE_BAR_ALPHA = 0.28f
private const val PLAYED_BAR_ALPHA = 0.92f
private const val SCRUB_RANGE_ALPHA = 0.7f
private const val LIVE_MARKER_ALPHA = 0.7f
private const val CHIP_ALPHA = 0.85f
private const val MS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60

/** Eases 0..1 in and out (slow at both ends). */
private fun smoothStep(t: Float): Float = t * t * (SMOOTH_STEP_A - 2 * t)

private const val SMOOTH_STEP_A = 3f
