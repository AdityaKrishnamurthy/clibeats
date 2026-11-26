@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "MagicNumber")

package com.clibeats.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.theme.Accent
import com.clibeats.presentation.theme.AccentColor
import com.clibeats.presentation.theme.CliBeatsThemeMode
import com.clibeats.presentation.theme.LocalAccentColor

@Composable
internal fun ThemeSettingsBlock(
    uiState: SettingsUiState,
    onThemeModeChange: (CliBeatsThemeMode) -> Unit,
    onAccentColorChange: (AccentColor) -> Unit,
    onCustomAccentChange: (Color) -> Unit,
) {
    val accent = LocalAccentColor.current
    TuiBlock(title = "Theme — ${uiState.themeMode.displayName}", isActive = true) {
        Column {
            CliBeatsThemeMode.entries.forEach { mode ->
                val isSelected = uiState.themeMode == mode
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onThemeModeChange(mode) }
                            .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isSelected) "(•) " else "( ) ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = mode.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }

    TuiBlock(title = "Accent Color — ${uiState.accentColor.displayName}", isActive = true) {
        AccentColorPicker(
            selected = uiState.accentColor,
            onSelect = onAccentColorChange,
            onCustom = onCustomAccentChange,
        )
    }
}

@Composable
private fun AccentColorPicker(
    selected: Accent,
    onSelect: (AccentColor) -> Unit,
    onCustom: (Color) -> Unit,
) {
    val currentAccent = LocalAccentColor.current
    var showCustom by remember { mutableStateOf(false) }
    val options: List<AccentColor?> = AccentColor.entries + null
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(ACCENTS_PER_ROW).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    if (option != null) {
                        AccentSwatch(
                            fill = SolidColor(option.color),
                            label = option.displayName,
                            isSelected = selected.preset == option,
                            currentAccent = currentAccent,
                            onClick = { onSelect(option) },
                        )
                    } else {
                        val isCustom = selected.preset == null
                        AccentSwatch(
                            fill = if (isCustom) SolidColor(selected.color) else CUSTOM_SWATCH_BRUSH,
                            label = "Custom",
                            isSelected = isCustom,
                            currentAccent = currentAccent,
                            onClick = { showCustom = true },
                        )
                    }
                }
                repeat(ACCENTS_PER_ROW - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
    if (showCustom) {
        CustomAccentDialog(
            initial = selected.color,
            onDismiss = { showCustom = false },
            onApply = {
                showCustom = false
                onCustom(it)
            },
        )
    }
}

private val CUSTOM_SWATCH_BRUSH = Brush.sweepGradient(hueSpectrum())

@Composable
private fun RowScope.AccentSwatch(
    fill: Brush,
    label: String,
    isSelected: Boolean,
    currentAccent: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .weight(1f)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .padding(vertical = 4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(fill)
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, currentAccent, RoundedCornerShape(4.dp))
                        } else {
                            Modifier
                        },
                    ),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) currentAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private const val ACCENTS_PER_ROW = 5
