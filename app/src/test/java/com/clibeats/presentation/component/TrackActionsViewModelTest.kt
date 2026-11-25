@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.component

import com.clibeats.data.download.TrackDownloadManager
import com.clibeats.domain.model.Track
import com.clibeats.domain.repository.LibraryRepository
import com.clibeats.domain.repository.PlaybackRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TrackActionsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val library: LibraryRepository = mock()
    private val playback: PlaybackRepository = mock()
    private val downloadManager: TrackDownloadManager = mock()
    private val notices = Notices()
    private lateinit var viewModel: TrackActionsViewModel
    private val track = Track("1", "Title", "Artist", "Album", 180_000L, null, null, "youtube_music")

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        whenever(downloadManager.downloadedTrackIds).thenReturn(MutableStateFlow(emptySet()))
        whenever(downloadManager.downloads).thenReturn(MutableStateFlow(emptyMap()))
        whenever(downloadManager.getDownloadedTracks()).thenReturn(flowOf(emptyList()))
        viewModel = TrackActionsViewModel(library, playback, downloadManager, notices)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `each action does its job and posts a confirmation`() =
        runTest(dispatcher) {
            val shown = mutableListOf<String>()
            val job = backgroundScope.launch { notices.messages.toList(shown) }

            viewModel.addToQueue(track)
            viewModel.playNext(track)
            viewModel.favorite(track)
            viewModel.download(track)

            verify(playback).addToQueue(track)
            verify(playback).playNext(track)
            verify(library).addToFavorites(track)
            verify(downloadManager).downloadTrack(track)
            assertThat(shown).containsExactly(
                "Added to queue",
                "Playing next",
                "♥ Added to favorites",
                "Downloading \u201cTitle\u201d",
            ).inOrder()
            job.cancel()
        }

    @Test
    fun `removeDownload calls manager and posts notice`() =
        runTest(dispatcher) {
            val shown = mutableListOf<String>()
            val job = backgroundScope.launch { notices.messages.toList(shown) }

            viewModel.removeDownload(track)

            verify(downloadManager).deleteDownload(track.id)
            assertThat(shown).contains("Removed download")
            job.cancel()
        }
}
