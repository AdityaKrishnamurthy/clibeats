// ForbiddenImport: UpdateState / UpdateFrequency are the updater's UI-facing types.
@file:Suppress("ktlint:standard:function-naming", "ForbiddenImport")

package com.clibeats.presentation.update

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clibeats.data.update.UpdateFrequency
import com.clibeats.data.update.UpdateState
import com.clibeats.presentation.settings.SettingsPanel
import com.clibeats.presentation.theme.LocalAccentColor

/** Settings → Updates: auto-check frequency, manual check, and download / install. */
@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun UpdateSection(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val frequency by viewModel.frequency.collectAsStateWithLifecycle()
    val accent = LocalAccentColor.current
    val release = state.releaseOrNull()

    SettingsPanel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AUTO-CHECK",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UpdateFrequency.entries.forEach { option ->
                        val isSelected = option == frequency
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .border(1.dp, if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant)
                                    .semantics { selected = isSelected }
                                    .clickable(role = Role.RadioButton) { viewModel.setFrequency(option) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            Text(
                text =
                    when (state) {
                        is UpdateState.Checking -> "Checking GitHub..."
                        is UpdateState.UpToDate -> "[ OK ] v${viewModel.installedVersion} is the latest version"
                        else ->
                            release?.let { "> v${it.version} available (installed v${viewModel.installedVersion})" }
                                ?: "Installed v${viewModel.installedVersion}"
                    },
                style = MaterialTheme.typography.labelSmall,
                color = if (release != null) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (release != null) ReleaseNotesLink(release, accent)
            UpdateProgress(state, accent)

            val busy = state is UpdateState.Checking || state.isWorking
            Button(
                onClick = {
                    when (state) {
                        is UpdateState.ReadyToInstall -> viewModel.install()
                        is UpdateState.Available, is UpdateState.Failed ->
                            if (release != null) viewModel.download(fromPrompt = false) else viewModel.checkNow()
                        else -> viewModel.checkNow()
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
            ) {
                Text(
                    when {
                        state is UpdateState.Checking -> "CHECKING..."
                        state is UpdateState.Downloading || state is UpdateState.Verifying -> "DOWNLOADING..."
                        state is UpdateState.ReadyToInstall -> "INSTALL"
                        release != null && state is UpdateState.Failed -> "RETRY DOWNLOAD"
                        release != null && state is UpdateState.Available -> "DOWNLOAD"
                        else -> "CHECK NOW"
                    },
                )
            }
        }
    }
}
