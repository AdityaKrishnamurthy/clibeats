@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.link

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clibeats.data.importer.ImportState
import com.clibeats.data.importer.PlaylistImporter
import com.clibeats.data.link.IncomingLinks
import com.clibeats.data.link.LinkResolver
import com.clibeats.data.link.LinkTarget
import com.clibeats.data.link.MusicLink
import com.clibeats.domain.repository.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Opens YouTube Music, YouTube and Spotify links: songs and YouTube playlists play right away, Spotify
 * playlists and albums are imported (the import screen opens over the app).
 */
@HiltViewModel
class LinkViewModel
    @Inject
    constructor(
        private val incoming: IncomingLinks,
        private val resolver: LinkResolver,
        private val importer: PlaylistImporter,
        private val playback: PlaybackRepository,
    ) : ViewModel() {
        private val _messages = Channel<String>(Channel.BUFFERED)

        /** Short notes for the user ("Opening link…", or why it couldn't be opened). */
        val messages: Flow<String> = _messages.receiveAsFlow()

        private val _showImport = MutableStateFlow(false)
        val showImport: StateFlow<Boolean> = _showImport.asStateFlow()

        init {
            viewModelScope.launch {
                incoming.links.collect { link ->
                    incoming.handled()
                    open(link)
                }
            }
        }

        fun closeImport() {
            _showImport.value = false
        }

        private suspend fun open(link: MusicLink) {
            _messages.send(if (link is MusicLink.YouTubeSong) "Opening song…" else "Opening link…")
            when (val target = resolver.resolve(link)) {
                is LinkTarget.Play -> playback.playFrom(target.tracks, 0, "Link")
                is LinkTarget.Import -> {
                    if (importer.state.value is ImportState.Running) {
                        _messages.send("An import is already running. Open the link again when it's done.")
                        return
                    }
                    if (target.playlist.songs.size >= SPOTIFY_EMBED_LIMIT) {
                        _messages.send(TOO_LONG_FOR_LINK)
                    }
                    importer.start(listOf(target.playlist), replaceExisting = false)
                    _showImport.value = true
                }
                is LinkTarget.Failed -> _messages.send(target.message)
            }
        }

        private companion object {
            const val SPOTIFY_EMBED_LIMIT = 100
            const val TOO_LONG_FOR_LINK =
                "Spotify links show up to 100 songs. For the whole playlist, import an Exportify file."
        }
    }
