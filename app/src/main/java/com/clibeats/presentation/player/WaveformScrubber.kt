@file:Suppress("ktlint:standard:function-naming")

package com.clibeats.presentation.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
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
 * anywhere else seeks once.
 *
 * [heights] is the song's real loudness per bar (see WaveformRepository). Bars not measured yet (NaN, or no
 * [heights] at all) show a stable placeholder pattern, smaller and dimmer, and grow into their real height
 * once measured.
 */
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")
@Composable
fun WaveformScrubber(
    trackKey: String?,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    heights: FloatArray? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = CliBeatsAccent
    // Played bars in the text colour (white on dark themes), the rest dimmed; the accent marks only the scrub.
    val playedColor = CliBeatsTextPrimary.copy(alpha = PLAYED_BAR_ALPHA)
    val idle = CliBeatsTextSecondary.copy(alpha = IDLE_BAR_ALPHA)
    val liveMarker = CliBeatsTextPrimary.copy(alpha = LIVE_MARKER_ALPHA)
    val placeholder = remember(trackKey) { waveformFor(trackKey) }
    val target = remember(placeholder, heights) { barTargets(placeholder, heights) }
    val bars = animatedBars(target)
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
            bars.forEachIndexed { index, bar ->
                val amplitude = bar.height
                val center = (index + HALF) / bars.size
                val played = center <= live
                val inScrubRange = scrub != null && center in minOf(live, scrub)..maxOf(live, scrub)
                val base =
                    when {
                        inScrubRange -> accent.copy(alpha = SCRUB_RANGE_ALPHA)
                        played -> playedColor
                        else -> idle
                    }
                // Placeholder bars are dimmer; a bar brightens as it turns real.
                val realness = PLACEHOLDER_ALPHA + (1f - PLACEHOLDER_ALPHA) * bar.measured
                val color = base.copy(alpha = base.alpha * realness)
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

/** One bar to draw: its height (0..1) and how "real" it is (0 = placeholder, 1 = measured; fades between). */
internal data class BarTarget(
    val height: Float,
    val measured: Float,
)

/** Real heights where measured, the placeholder pattern (scaled down) everywhere else. */
internal fun barTargets(
    placeholder: List<Float>,
    heights: FloatArray?,
): List<BarTarget> =
    placeholder.mapIndexed { i, fake ->
        val real = heights?.getOrNull(i)?.takeUnless { it.isNaN() }
        if (real != null) BarTarget(real, 1f) else BarTarget(fake * PLACEHOLDER_SCALE, 0f)
    }

/** Bars glide from their old height to the new one (a new song, or bars turning real) instead of jumping. */
@Composable
private fun animatedBars(target: List<BarTarget>): List<BarTarget> {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        val t = progress.value
        from = lerpBars(from, to, t)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(BAR_ANIM_MS))
    }
    return lerpBars(from, to, progress.value)
}

private fun lerpBars(
    from: List<BarTarget>,
    to: List<BarTarget>,
    t: Float,
): List<BarTarget> =
    if (t >= 1f || from.size != to.size) {
        to
    } else {
        to.mapIndexed { i, b ->
            val a = from[i]
            BarTarget(a.height + (b.height - a.height) * t, a.measured + (b.measured - a.measured) * t)
        }
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
private const val PLACEHOLDER_SCALE = 0.45f
private const val PLACEHOLDER_ALPHA = 0.45f
private const val BAR_ANIM_MS = 350
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
private const val PLAYED_BAR_ALPHA = 0.92f
private const val SCRUB_RANGE_ALPHA = 0.7f
private const val LIVE_MARKER_ALPHA = 0.7f
private const val CHIP_ALPHA = 0.85f
private const val MS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
