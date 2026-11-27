@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.clibeats.presentation.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor

/**
 * A terminal-style action: a one-pixel rectangle with the label in brackets, like the
 * `[ CLEAR ]` and `[ ON ]` controls elsewhere in Settings. Material's filled, pill-shaped
 * buttons are deliberately not used here.
 *
 * The brackets are added by this component, so callers pass the bare label (e.g. `"RESCAN"`).
 */
@Composable
fun TuiButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = if (enabled) LocalAccentColor.current else CliBeatsTextSecondary.copy(alpha = DISABLED_ALPHA)
    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = BUTTON_MIN_HEIGHT)
                .border(1.dp, color)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "[ $text ]",
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private val BUTTON_MIN_HEIGHT = 40.dp
private const val DISABLED_ALPHA = 0.45f
