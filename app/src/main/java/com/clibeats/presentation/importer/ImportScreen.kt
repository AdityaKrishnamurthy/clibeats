@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "ForbiddenImport")

package com.clibeats.presentation.importer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clibeats.data.importer.ImportState
import com.clibeats.presentation.theme.CliBeatsBackground

/**
 * Import from Spotify / YouTube Music / CSV: instructions -> choose playlists -> matching progress -> summary.
 */
@Composable
fun ImportScreen(
    onClose: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val manual by viewModel.manual.collectAsStateWithLifecycle()
    val playingId by viewModel.playingId.collectAsStateWithLifecycle()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::onFilePicked)
        }
    val choosingPlaylists = setup.found != null && importState is ImportState.Idle
    BackHandler { if (choosingPlaylists) viewModel.reset() else onClose() }

    Column(modifier = Modifier.fillMaxSize().background(CliBeatsBackground)) {
        ImportTitleBar(onBack = { if (choosingPlaylists) viewModel.reset() else onClose() })
        when (val s = importState) {
            is ImportState.Running -> ImportMatchingStep(s, onCancel = viewModel::cancel)
            is ImportState.Finished ->
                ImportReviewStep(
                    state = s,
                    onFind = { p, song ->
                        val query = "${song.song.title} ${song.song.artists.firstOrNull().orEmpty()}".trim()
                        viewModel.openManual(p, song.index, query)
                    },
                    onDone = {
                        viewModel.done()
                        onClose()
                    },
                )
            ImportState.Idle ->
                if (setup.found != null) {
                    ChoosePlaylistsStep(
                        setup = setup,
                        onToggle = viewModel::toggle,
                        onSelectAll = viewModel::selectAll,
                        onAllowClosest = viewModel::setAllowClosest,
                        onImport = viewModel::onImportClick,
                    )
                } else {
                    ImportSourceStep(
                        reading = setup.reading,
                        error = setup.error,
                        onChooseFile = { picker.launch(arrayOf("*/*")) },
                    )
                }
        }
    }

    ImportDialogs(
        setup = setup,
        manual = manual,
        playingId = playingId,
        viewModel = viewModel,
    )
}

@Composable
private fun ImportDialogs(
    setup: ImportSetup,
    manual: ManualPick?,
    playingId: String?,
    viewModel: ImportViewModel,
) {
    if (setup.conflicts.isNotEmpty()) {
        ConflictDialog(
            names = setup.conflicts,
            onReplace = { viewModel.start(replaceExisting = true) },
            onKeepBoth = { viewModel.start(replaceExisting = false) },
            onDismiss = viewModel::dismissConflicts,
        )
    }
    manual?.let { pick ->
        val loadedId by viewModel.loadedId.collectAsStateWithLifecycle()
        val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
        FindMatchSheet(
            playingId = playingId,
            onPreview = viewModel::preview,
            pick = pick,
            onSearch = viewModel::search,
            onPick = viewModel::choose,
            onDismiss = viewModel::closeManual,
            seek =
                PreviewSeek(
                    loadedId = loadedId,
                    durationMs = durationMs,
                    position = viewModel::livePositionMs,
                    onSeek = viewModel::seekTo,
                ),
            onConfirm = viewModel::confirmPick,
        )
    }
}
