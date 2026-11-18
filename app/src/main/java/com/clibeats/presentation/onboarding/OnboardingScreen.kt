@file:Suppress("ktlint:standard:function-naming")

package com.clibeats.presentation.onboarding

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clibeats.presentation.component.TuiBlock
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsBorderInactive
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.CliBeatsThemeMode

private const val STEP_COUNT = 3

/** First-run setup (new installs only): theme → music folder → default source. */
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun OnboardingScreen(
    currentTheme: CliBeatsThemeMode,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var useLocal by rememberSaveable { mutableStateOf(false) }
    val folderName by viewModel.folderName.collectAsState()
    val context = LocalContext.current
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                viewModel.onFolderPicked(uri)
                useLocal = true
            }
        }
    BackHandler(enabled = step > 0) { step-- }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CliBeatsBackground)
                .safeDrawingPadding()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("> clibeats --setup", style = MaterialTheme.typography.titleMedium, color = CliBeatsAccent)
        Text(
            text = "step ${step + 1}/$STEP_COUNT  " + "■".repeat(step + 1) + "□".repeat(STEP_COUNT - step - 1),
            style = MaterialTheme.typography.labelMedium,
            color = CliBeatsTextSecondary,
        )

        when (step) {
            0 ->
                TuiBlock(title = "Choose a theme", isActive = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CliBeatsThemeMode.entries.forEach { mode ->
                            Choice(
                                label = mode.displayName,
                                selected = mode == currentTheme,
                                onClick = { viewModel.setTheme(mode) },
                            )
                        }
                        Text(
                            "Preview updates live. Change it anytime in Settings.",
                            style = MaterialTheme.typography.labelSmall,
                            color = CliBeatsTextSecondary,
                        )
                    }
                }
            1 ->
                TuiBlock(title = "Where is your music?", isActive = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Pick the folder that holds your music files (FLAC, MP3, WAV, M4A...). " +
                                "Playlist files (.m3u/.m3u8) inside it are imported too.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CliBeatsTextPrimary,
                        )
                        Choice(
                            label = folderName?.let { "Folder: $it  (scanning...)" } ?: "[ CHOOSE FOLDER ]",
                            selected = folderName != null,
                            onClick = { folderPicker.launch(null) },
                        )
                        Text(
                            "Optional. You can do this later in Settings → Local Music.",
                            style = MaterialTheme.typography.labelSmall,
                            color = CliBeatsTextSecondary,
                        )
                    }
                }
            else ->
                TuiBlock(title = "Default source", isActive = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Choice("ONLINE  (YouTube Music, Internet Archive, ...)", !useLocal) { useLocal = false }
                        Choice("LOCAL  (music on this device)", useLocal) { useLocal = true }
                    }
                }
        }

        Spacer(modifier = Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step > 0) {
                NavButton("[ < BACK ]") { step-- }
            } else {
                Spacer(modifier = Modifier.height(1.dp))
            }
            val last = step == STEP_COUNT - 1
            NavButton(
                text =
                    when {
                        last -> "[ DONE ]"
                        step == 1 && folderName == null -> "[ SKIP > ]"
                        else -> "[ NEXT > ]"
                    },
                primary = true,
            ) { if (last) viewModel.finish(useLocal) else step++ }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun Choice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = (if (selected) "(•) " else "( ) ") + label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) CliBeatsAccent else CliBeatsTextPrimary,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { this.selected = selected }
                .border(1.dp, if (selected) CliBeatsAccent else CliBeatsBorderInactive)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .padding(12.dp),
    )
}

@Suppress("FunctionNaming")
@Composable
private fun NavButton(
    text: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (primary) CliBeatsAccent else CliBeatsTextSecondary,
        modifier =
            Modifier
                .border(1.dp, if (primary) CliBeatsAccent else CliBeatsBorderInactive)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
