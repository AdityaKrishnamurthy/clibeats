@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.settings

import androidx.lifecycle.ViewModel
import com.clibeats.data.download.TrackDownloadManager
import com.clibeats.data.playlist.PlaylistExchangeManager
import com.clibeats.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** After restoring a backup: the songs that were downloaded there, offered for download again. */
@HiltViewModel
class RestoreDownloadsViewModel
    @Inject
    constructor(
        private val exchange: PlaylistExchangeManager,
        private val downloadManager: TrackDownloadManager,
    ) : ViewModel() {
        val pending: StateFlow<List<Track>> = exchange.pendingDownloads

        fun downloadAll(tracks: List<Track>) {
            downloadManager.downloadAll(tracks)
        }

        fun dismiss() = exchange.clearPendingDownloads()
    }
