@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.clibeats.presentation.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.clibeats.presentation.theme.Accent
import com.clibeats.presentation.theme.CliBeatsTextSecondary

/**
 * Custom accent picker: hue, saturation and brightness sliders with a live preview, or type a `#RRGGBB` code.
 * Brightness stops short of black so the accent stays visible on the dark theme.
 */
@Suppress("LongMethod")
@Composable
internal fun CustomAccentDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onApply: (Color) -> Unit,
    title: String = "> accent --custom",
) {
    val start = remember { FloatArray(HSV_SIZE).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var brightness by remember { mutableFloatStateOf(start[2].coerceAtLeast(MIN_BRIGHTNESS)) }
    val color = Color.hsv(hue, saturation, brightness)
    var hexText by remember { mutableStateOf(Accent.toHex(color)) }

    fun changed(
        h: Float = hue,
        s: Float = saturation,
        v: Float = brightness,
    ) {
        hue = h
        saturation = s
        brightness = v
        hexText = Accent.toHex(Color.hsv(h, s, v))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, color),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleSmall, color = color) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(PREVIEW_DP.dp)
                            .background(color, RoundedCornerShape(4.dp))
                            .semantics { contentDescription = "Preview" },
                    )
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { text ->
                            hexText = text.uppercase().take(HEX_LENGTH)
                            Accent.parseHex(hexText)?.let { typed ->
                                val hsv = FloatArray(HSV_SIZE)
                                android.graphics.Color.colorToHSV(typed.toArgb(), hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                brightness = hsv[2].coerceAtLeast(MIN_BRIGHTNESS)
                            }
                        },
                        label = { Text("Hex") },
                        singleLine = true,
                        isError = Accent.parseHex(hexText) == null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                GradientSlider(
                    label = "HUE",
                    value = hue / MAX_HUE,
                    brush = Brush.horizontalGradient(hueSpectrum()),
                    onChange = { changed(h = it * MAX_HUE) },
                )
                GradientSlider(
                    label = "SATURATION",
                    value = saturation,
                    brush =
                        Brush.horizontalGradient(
                            listOf(Color.hsv(hue, 0f, brightness), Color.hsv(hue, 1f, brightness)),
                        ),
                    onChange = { changed(s = it) },
                )
                GradientSlider(
                    label = "BRIGHTNESS",
                    value = (brightness - MIN_BRIGHTNESS) / (1f - MIN_BRIGHTNESS),
                    brush =
                        Brush.horizontalGradient(
                            listOf(Color.hsv(hue, saturation, MIN_BRIGHTNESS), Color.hsv(hue, saturation, 1f)),
                        ),
                    onChange = { changed(v = MIN_BRIGHTNESS + it * (1f - MIN_BRIGHTNESS)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(color) }) { Text("[ APPLY ]", color = color) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("[ CANCEL ]", color = CliBeatsTextSecondary) }
        },
    )
}

/** A labelled bar painted with [brush]; touch or drag anywhere on it to set [value] (0–1). */
@Composable
private fun GradientSlider(
    label: String,
    value: Float,
    brush: Brush,
    onChange: (Float) -> Unit,
) {
    val update by rememberUpdatedState(onChange)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CliBeatsTextSecondary)
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(SLIDER_TOUCH_DP.dp)
                    .semantics {
                        contentDescription = label
                        progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
                        setProgress { target ->
                            update(target.coerceIn(0f, 1f))
                            true
                        }
                    }.pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            update((down.position.x / size.width).coerceIn(0f, 1f))
                            do {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                val held = change?.pressed == true
                                if (held) {
                                    change.consume()
                                    update((change.position.x / size.width).coerceIn(0f, 1f))
                                }
                            } while (held)
                        }
                    },
        ) {
            val barHeight = BAR_DP.dp.toPx()
            val top = (size.height - barHeight) / 2
            drawRoundRect(
                brush = brush,
                topLeft = Offset(0f, top),
                size = Size(size.width, barHeight),
                cornerRadius = CornerRadius(barHeight / 2),
            )
            val center = Offset(value.coerceIn(0f, 1f) * size.width, size.height / 2)
            drawCircle(Color.White, radius = THUMB_DP.dp.toPx(), center = center)
            drawCircle(
                Color.Black.copy(alpha = THUMB_RING_ALPHA),
                radius = THUMB_DP.dp.toPx(),
                center = center,
                style = Stroke(2.dp.toPx()),
            )
        }
    }
}

/** Fully saturated colours around the hue circle (red → yellow → green → cyan → blue → magenta → red). */
internal fun hueSpectrum(): List<Color> = (0..HUE_STOPS).map { Color.hsv(it * MAX_HUE / HUE_STOPS, 1f, 1f) }

private const val HSV_SIZE = 3
private const val MAX_HUE = 360f
private const val HUE_STOPS = 6
private const val MIN_BRIGHTNESS = 0.35f
private const val HEX_LENGTH = 7
private const val PREVIEW_DP = 48
private const val SLIDER_TOUCH_DP = 28
private const val BAR_DP = 12
private const val THUMB_DP = 9
private const val THUMB_RING_ALPHA = 0.5f
