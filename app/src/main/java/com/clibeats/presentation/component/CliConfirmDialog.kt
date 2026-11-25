@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.clibeats.presentation.component

import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor

/**
 * Asks before anything that clears or deletes in bulk, in the app's terminal look: the title is the command
 * that's about to run (`> rm -r downloads/`), the text says what it does, and the red button runs it.
 */
@Composable
fun CliConfirmDialog(
    command: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, LocalAccentColor.current),
        title = { Text("> $command", style = MaterialTheme.typography.titleMedium) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = CliBeatsTextSecondary) } },
        containerColor = CliBeatsSurface,
    )
}
