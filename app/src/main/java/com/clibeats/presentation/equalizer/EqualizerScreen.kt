@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:max-line-length",
    "FunctionNaming",
    "MagicNumber",
    "LongMethod",
    "LongParameterList",
    "LoopWithTooManyJumpStatements",
    "UnusedPrivateProperty",
    "ArgumentListWrapping",
    "MaxLineLength",
    "MaximumLineLength",
)

package com.clibeats.presentation.equalizer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clibeats.domain.model.EQUALIZER_BANDS_HZ
import com.clibeats.domain.model.EQUALIZER_MAX_GAIN_DB
import com.clibeats.domain.model.EQUALIZER_MIN_GAIN_DB
import com.clibeats.domain.model.EqualizerPreset
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.component.TuiButton
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/** Full-screen terminal equalizer: response graph, live waveform, draggable bands and presets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(
    onClose: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accent = LocalAccentColor.current
    val context = LocalContext.current
    var panelMessage by remember { mutableStateOf<String?>(null) }
    var liveGains by remember { mutableStateOf(state.gainsDb) }

    LaunchedEffect(state.gainsDb) { liveGains = state.gainsDb }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "[ < BACK ]",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                modifier =
                    Modifier
                        .clickable(role = Role.Button, onClick = onClose)
                        .padding(vertical = 8.dp, horizontal = 4.dp),
            )
            Text(
                text = "> equalizer",
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
            Text(
                text = if (state.enabled) "[ ON ]" else "[ OFF ]",
                style = MaterialTheme.typography.labelLarge,
                color = if (state.enabled) accent else CliBeatsTextSecondary,
                modifier =
                    Modifier
                        .clickable(role = Role.Switch) { viewModel.setEnabled(!state.enabled) }
                        .padding(8.dp),
            )
        }

        TuiBlock(title = "Response", isActive = state.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ResponseGraph(
                    gains = liveGains,
                    isPlaying = state.enabled,
                    accent = accent,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("31 Hz", style = MaterialTheme.typography.labelSmall, color = CliBeatsTextSecondary)
                    Text("EQ CURVE  ·  WAVEFORM", style = MaterialTheme.typography.labelSmall, color = accent)
                    Text("16 kHz", style = MaterialTheme.typography.labelSmall, color = CliBeatsTextSecondary)
                }
            }
        }

        TuiBlock(title = "10-Band Equalizer", isActive = state.enabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                EQUALIZER_BANDS_HZ.forEachIndexed { index, frequency ->
                    BandSlider(
                        frequencyHz = frequency,
                        gainDb = liveGains.getOrElse(index) { 0f },
                        accent = accent,
                        onChange = { gain ->
                            liveGains = liveGains.toMutableList().also { it[index] = gain }
                        },
                        onCommit = { gain -> viewModel.setBand(index, gain) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        TuiBlock(title = "Presets", isActive = state.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EqualizerPreset.selectable.forEach { preset ->
                        PresetChip(
                            preset = preset,
                            selected = state.preset == preset,
                            onClick = {
                                liveGains = preset.gains()
                                viewModel.setPreset(preset)
                            },
                        )
                    }
                    PresetChip(
                        preset = EqualizerPreset.CUSTOM,
                        selected = state.preset == EqualizerPreset.CUSTOM,
                        onClick = {},
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "PRE-AMP",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    StepButton("−") { viewModel.stepPreamp(-PREAMP_STEP_DB) }
                    Text(
                        text = formatDb(state.preampDb),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        modifier = Modifier.width(72.dp),
                        textAlign = TextAlign.Center,
                    )
                    StepButton("+") { viewModel.stepPreamp(PREAMP_STEP_DB) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TuiButton(
                        text = "RESET",
                        onClick = {
                            liveGains = EqualizerPreset.FLAT.gains()
                            viewModel.reset()
                        },
                    )
                    TuiButton(
                        text = "SYSTEM EQ",
                        onClick = {
                            panelMessage =
                                if (viewModel.openSystemPanel(context)) {
                                    null
                                } else {
                                    "Play something first, or this phone has no audio-effects panel."
                                }
                        },
                    )
                }
                panelMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The EQ curve over a soft grid, with a small animated waveform that moves only while the effect is on. */
@Composable
private fun ResponseGraph(
    gains: List<Float>,
    isPlaying: Boolean,
    accent: Color,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val wave = CliBeatsTextSecondary
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(WAVE_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )

    Canvas(modifier = Modifier.fillMaxWidth().height(154.dp).padding(horizontal = 4.dp, vertical = 4.dp)) {
        val left = 0f
        val right = size.width
        val top = 6.dp.toPx()
        val bottom = size.height - 6.dp.toPx()
        val center = (top + bottom) / 2f
        val half = (bottom - top) / 2f

        // Horizontal dB grid and the 0 dB line.
        listOf(-12f, -6f, 0f, 6f, 12f).forEach { db ->
            val y = center - (db / EQUALIZER_MAX_GAIN_DB) * half
            drawLine(
                color = if (db == 0f) accent.copy(alpha = 0.45f) else grid.copy(alpha = 0.35f),
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = if (db == 0f) 1.2.dp.toPx() else 0.7.dp.toPx(),
            )
        }

        // Live-looking waveform, scaled by the current curve.
        val energy = (gains.maxOrNull() ?: 0f).coerceAtLeast(0f) / EQUALIZER_MAX_GAIN_DB
        val amplitude = if (isPlaying) (8f + energy * 18f).dp.toPx() else 1.dp.toPx()
        val wavePath = Path()
        val steps = 96
        repeat(steps + 1) { i ->
            val x = left + (right - left) * i / steps
            val envelope = sin(PI * i / steps).toFloat()
            val y = center + sin(i * 0.55f + phase * 2.2f) * amplitude * envelope
            if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
        }
        drawPath(wavePath, color = wave.copy(alpha = if (isPlaying) 0.55f else 0.18f), style = Stroke(1.dp.toPx()))

        // The editable EQ curve.
        val curve = Path()
        gains.forEachIndexed { index, gain ->
            val x = left + (right - left) * index / (gains.size - 1).coerceAtLeast(1)
            val y = center - (gain / EQUALIZER_MAX_GAIN_DB) * half
            if (index == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
        }
        drawPath(curve, color = accent, style = Stroke(2.dp.toPx()))
        gains.forEachIndexed { index, gain ->
            val x = left + (right - left) * index / (gains.size - 1).coerceAtLeast(1)
            val y = center - (gain / EQUALIZER_MAX_GAIN_DB) * half
            drawRect(color = accent, topLeft = Offset(x - 2.5.dp.toPx(), y - 2.5.dp.toPx()), size = Size(5.dp.toPx(), 5.dp.toPx()))
        }
    }
}

/** A thin vertical fader; dragging updates the graph immediately, and releasing saves the band. */
@Composable
private fun BandSlider(
    frequencyHz: Int,
    gainDb: Float,
    accent: Color,
    onChange: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnCommit by rememberUpdatedState(onCommit)
    val border = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier.semantics { contentDescription = "${formatFrequency(frequencyHz)} band" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = formatGainShort(gainDb),
            style = MaterialTheme.typography.labelSmall,
            color = if (gainDb == 0f) CliBeatsTextSecondary else accent,
            fontSize = 9.sp,
            maxLines = 1,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .pointerInput(frequencyHz) {
                        val top = 7.dp.toPx()
                        val bottom = size.height - 7.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var last = gainAtY(down.position.y, top, bottom)
                            currentOnChange(last)
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                last = gainAtY(change.position.y, top, bottom)
                                currentOnChange(last)
                                change.consume()
                            }
                            currentOnCommit(last)
                        }
                    },
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp)) {
                val cx = size.width / 2f
                val top = 7.dp.toPx()
                val bottom = size.height - 7.dp.toPx()
                val zero = (top + bottom) / 2f
                val thumbY = top + (1f - (gainDb - EQUALIZER_MIN_GAIN_DB) / (EQUALIZER_MAX_GAIN_DB - EQUALIZER_MIN_GAIN_DB)) * (bottom - top)

                drawLine(border.copy(alpha = 0.6f), Offset(cx, top), Offset(cx, bottom), 1.dp.toPx())
                listOf(-6f, 0f, 6f).forEach { db ->
                    val y = top + (1f - (db - EQUALIZER_MIN_GAIN_DB) / (EQUALIZER_MAX_GAIN_DB - EQUALIZER_MIN_GAIN_DB)) * (bottom - top)
                    drawLine(
                        color = if (db == 0f) border else border.copy(alpha = 0.4f),
                        start = Offset(cx - 5.dp.toPx(), y),
                        end = Offset(cx + 5.dp.toPx(), y),
                        strokeWidth = 0.7.dp.toPx(),
                    )
                }
                drawLine(
                    color = accent.copy(alpha = 0.25f),
                    start = Offset(cx, zero.coerceAtLeast(thumbY)),
                    end = Offset(cx, zero.coerceAtMost(thumbY)),
                    strokeWidth = 6.dp.toPx(),
                )
                drawRect(
                    color = accent,
                    topLeft = Offset(cx - 10.dp.toPx(), thumbY - 3.dp.toPx()),
                    size = Size(20.dp.toPx(), 6.dp.toPx()),
                )
                drawRect(
                    color = accent,
                    topLeft = Offset(cx - 10.dp.toPx(), thumbY - 3.dp.toPx()),
                    size = Size(20.dp.toPx(), 6.dp.toPx()),
                    style = Stroke(1.dp.toPx()),
                )
            }
        }
        Text(
            text = formatFrequencyShort(frequencyHz),
            style = MaterialTheme.typography.labelSmall,
            color = CliBeatsTextSecondary,
            fontSize = 8.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun StepButton(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = "[ $label ]",
        style = MaterialTheme.typography.labelMedium,
        color = LocalAccentColor.current,
        modifier =
            Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun PresetChip(
    preset: EqualizerPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = LocalAccentColor.current
    Text(
        text = "[ ${preset.label} ]",
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .border(1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant)
                .clickable(enabled = preset != EqualizerPreset.CUSTOM, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

private fun gainAtY(
    y: Float,
    topPx: Float,
    bottomPx: Float,
): Float {
    val fraction = ((y - topPx) / (bottomPx - topPx)).coerceIn(0f, 1f)
    return EQUALIZER_MAX_GAIN_DB - fraction * (EQUALIZER_MAX_GAIN_DB - EQUALIZER_MIN_GAIN_DB)
}

private fun formatFrequency(hz: Int): String = if (hz >= 1_000) "${hz / 1_000} kHz" else "$hz Hz"

private fun formatFrequencyShort(hz: Int): String = if (hz >= 1_000) "${hz / 1_000}k" else "$hz"

private fun formatGainShort(db: Float): String = String.format(Locale.US, "%+.0f", db)

private fun formatDb(db: Float): String = String.format(Locale.US, "%+.1f dB", db)

private const val PREAMP_STEP_DB = 0.5f
private const val WAVE_PERIOD_MS = 1400
