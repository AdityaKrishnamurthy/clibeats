@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:multiline-expression-wrapping",
    "MagicNumber",
)

package com.clibeats.presentation.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.LocalAccentColor

/**
 * TUI-styled text field that matches the TuiBlock aesthetic.
 *
 * Renders as a [TuiBlock] with the label in its top border and a `> ` prompt inside.
 *
 * Border glows with the current accent colour only when the field has focus.
 *
 * @param value         Current text value
 * @param onValueChange Called when the user changes the text
 * @param label         Label embedded in the top border (like TuiBlock title)
 * @param placeholder   Placeholder shown when [value] is empty
 * @param modifier      Outer modifier
 * @param keyboardOptions     Forwarded to [BasicTextField]
 * @param keyboardActions     Forwarded to [BasicTextField]
 * @param trailingContent     Optional trailing icon slot (e.g., clear button)
 */
@Suppress("FunctionNaming", "LongParameterList", "LongMethod")
@Composable
fun TuiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Input",
    placeholder: String = "",
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingContent: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    val accent = LocalAccentColor.current
    var isFocused by remember { mutableStateOf(false) }

    // Same frame as TuiBlock: the label sits in the top border, so there is no separate header row.
    TuiBlock(
        title = label,
        isActive = isFocused,
        containerColor = CliBeatsSurface,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        // ── Input Row ────────────────────────────────────────────────────────
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { isFocused = it.isFocused },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // TUI prompt character
                    Text(
                        text = "> ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = accent,
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        // Placeholder
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        innerTextField()
                    }
                    trailingContent?.invoke()
                }
            },
        )
    }
}
