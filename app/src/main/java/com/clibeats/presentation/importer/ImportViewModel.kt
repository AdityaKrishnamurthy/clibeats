@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.data.importer.ExportifyFiles
import com.clibeats.data.importer.ImportState
import com.clibeats.data.importer.NotAnExportifyFileException
import com.clibeats.data.importer.PlaylistImporter
import com.clibeats.data.importer.SpotifyPlaylist
import com.clibeats.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The steps before the import runs: picking a file and choosing which playlists in it to bring in. */
data class ImportSetup(
    val reading: Boolean = false,
    val error: String? = null,
    /** Playlists found in the picked file; null until a file is read. */
    val found: List<SpotifyPlaylist>? = null,
    val selected: Set<Int> = emptySet(),
    /** Names that already exist among your playlists; non-empty shows the Replace / Keep both question. */
    val conflicts: List<String> = emptyList(),
    /** "Use the closest version when the exact one isn't on YouTube Music" (off by default). */
    val allowClosest: Boolean = false,
)

/** The "Find" sheet for a song that didn't match. */
data class ManualPick(
    val playlistIndex: Int,
    val songIndex: Int,
    val query: String,
    val searching: Boolean = true,
    val results: List<Track> = emptyList(),
)

@Suppress("TooManyFunctions") // one small handler per screen action
@HiltViewModel
class ImportViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val importer: PlaylistImporter,
    ) : ViewModel() {
        val importState: StateFlow<ImportState> = importer.state

        private val _setup = MutableStateFlow(ImportSetup())
        val setup: StateFlow<ImportSetup> = _setup.asStateFlow()

        private val _manual = MutableStateFlow<ManualPick?>(null)
        val manual: StateFlow<ManualPick?> = _manual.asStateFlow()

        fun onFilePicked(uri: Uri) {
            _setup.value = ImportSetup(reading = true)
            viewModelScope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val name = displayName(uri)
                            context.contentResolver.openInputStream(uri)?.use { ExportifyFiles.read(it, name) }
                                ?: throw NotAnExportifyFileException("Couldn't open that file.")
                        }
                    }
                _setup.value =
                    result.fold(
                        onSuccess = { ImportSetup(found = it, selected = it.indices.toSet()) },
                        onFailure = { e ->
                            ImportSetup(
                                error =
                                    (e as? NotAnExportifyFileException)?.message
                                        ?: "Couldn't read that file. Pick the .csv or .zip that Exportify downloaded.",
                            )
                        },
                    )
            }
        }

        fun toggle(index: Int) =
            _setup.update { s ->
                s.copy(selected = if (index in s.selected) s.selected - index else s.selected + index)
            }

        fun setAllowClosest(allow: Boolean) = _setup.update { it.copy(allowClosest = allow) }

        fun selectAll(all: Boolean) {
            _setup.update { s -> s.copy(selected = if (all) s.found.orEmpty().indices.toSet() else emptySet()) }
        }

        /** Back from the playlist list to the instructions. */
        fun reset() {
            _setup.value = ImportSetup()
        }

        /** Import tapped: asks about duplicates first when a chosen playlist's name is already used. */
        fun onImportClick() {
            viewModelScope.launch {
                val chosen = chosen()
                val taken = importer.existingNames()
                val conflicts = chosen.map { it.name }.filter { it in taken }
                if (conflicts.isEmpty()) {
                    start(replaceExisting = false)
                } else {
                    _setup.update { it.copy(conflicts = conflicts) }
                }
            }
        }

        fun start(replaceExisting: Boolean) {
            importer.start(chosen(), replaceExisting, allowClosest = _setup.value.allowClosest)
            _setup.value = ImportSetup()
        }

        fun dismissConflicts() = _setup.update { it.copy(conflicts = emptyList()) }

        fun cancel() = importer.cancel()

        fun done() = importer.dismiss()

        fun openManual(
            playlistIndex: Int,
            songIndex: Int,
            query: String,
        ) {
            _manual.value = ManualPick(playlistIndex, songIndex, query)
            search(query)
        }

        fun search(query: String) {
            val pick = _manual.value ?: return
            _manual.value = pick.copy(query = query, searching = true)
            viewModelScope.launch {
                val results = runCatching { importer.searchCandidates(query) }.getOrDefault(emptyList())
                _manual.update { if (it?.query == query) it.copy(searching = false, results = results) else it }
            }
        }

        fun pick(track: Track) {
            val pick = _manual.value ?: return
            _manual.value = null
            viewModelScope.launch { importer.resolveManually(pick.playlistIndex, pick.songIndex, track) }
        }

        fun closeManual() {
            _manual.value = null
        }

        private fun chosen(): List<SpotifyPlaylist> {
            val s = _setup.value
            return s.found.orEmpty().filterIndexed { i, _ -> i in s.selected }
        }

        private fun displayName(uri: Uri): String =
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "playlist.csv"
    }
