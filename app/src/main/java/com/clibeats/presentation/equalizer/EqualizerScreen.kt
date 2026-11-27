@file:Suppress(
    "ktlint:standard:function-naming",
    "FunctionNaming",
    "MagicNumber",
    "LongMethod",
    "ArgumentListWrapping",
    "MaxLineLength",
    "MaximumLineLength",
)

package com.clibeats.presentation.equalizer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clibeats.domain.model.EQUALIZER_BANDS_HZ
import com.clibeats.domain.model.EqualizerPreset
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.component.TuiButton
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor
import java.util.Locale

/** Full-screen terminal equalizer: presets, ten bands, pre-amp and the system EQ panel. */
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

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
            )
        }

        TuiBlock(title = "Equalizer", isActive = state.enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "10-BAND AUDIO EFFECT",
                        style = MaterialTheme.typography.titleSmall,
                        color = accent,
                    )
                    Text(
                        text =
                            if (state.enabled) {
                                "Attached to the current audio session"
                            } else {
                                "Turn on to shape playback; works with downloaded and streamed songs"
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TuiButton(
                    text = if (state.enabled) "TURN OFF" else "TURN ON",
                    onClick = { viewModel.setEnabled(!state.enabled) },
                )
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
                            onClick = { viewModel.setPreset(preset) },
                        )
                    }
                    PresetChip(
                        preset = EqualizerPreset.CUSTOM,
                        selected = state.preset == EqualizerPreset.CUSTOM,
                        onClick = {},
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TuiButton(text = "RESET", onClick = viewModel::reset)
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

        TuiBlock(title = "10-Band Equalizer", isActive = state.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EQUALIZER_BANDS_HZ.forEachIndexed { index, frequency ->
                    BandRow(
                        frequencyHz = frequency,
                        gainDb = state.gainsDb.getOrElse(index) { 0f },
                        enabled = state.enabled,
                        onChange = { delta -> viewModel.stepBand(index, delta) },
                    )
                }
            }
        }

        TuiBlock(title = "Output", isActive = state.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StepRow(
                    label = "PRE-AMP",
                    detail = "Lower this if boosted bands distort",
                    value = formatDb(state.preampDb),
                    enabled = state.enabled,
                    onChange = viewModel::stepPreamp,
                )
                Text(
                    text =
                        "DynamicsProcessing with a limiter on Android 9+; the platform Equalizer on Android 8. " +
                            "The phone's own EQ apps can bind through the standard audio-session broadcast.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .semantics {
                    this.selected = selected
                    contentDescription = "${preset.label} preset"
                },
    )
}

@Composable
private fun BandRow(
    frequencyHz: Int,
    gainDb: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = formatFrequency(frequencyHz),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TuiButton(
            text = "−",
            enabled = enabled,
            onClick = { onChange(-STEP_DB) },
        )
        Text(
            text = formatDb(gainDb),
            style = MaterialTheme.typography.labelMedium,
            color = if (gainDb == 0f) MaterialTheme.colorScheme.onSurfaceVariant else LocalAccentColor.current,
            modifier = Modifier.width(76.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        TuiButton(
            text = "+",
            enabled = enabled,
            onClick = { onChange(STEP_DB) },
        )
    }
}

@Composable
private fun StepRow(
    label: String,
    detail: String,
    value: String,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TuiButton(text = "−", enabled = enabled, onClick = { onChange(-PREAMP_STEP_DB) })
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = LocalAccentColor.current,
            modifier = Modifier.width(76.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        TuiButton(text = "+", enabled = enabled, onClick = { onChange(PREAMP_STEP_DB) })
    }
}

private fun formatFrequency(hz: Int): String = if (hz >= 1_000) "${hz / 1_000} kHz" else "$hz Hz"

private fun formatDb(value: Float): String = String.format(Locale.US, "%+.1f dB", value)

private const val STEP_DB = 1f
private const val PREAMP_STEP_DB = 0.5f
