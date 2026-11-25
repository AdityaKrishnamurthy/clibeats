@file:Suppress("ForbiddenImport")

package com.clibeats.presentation.home

import com.clibeats.data.home.HomeFeedRepository
import com.clibeats.data.network.NetworkMonitor
import com.clibeats.data.preferences.AppPreferences
import com.clibeats.domain.model.HomeFeed
import com.clibeats.domain.model.HomeItem
import com.clibeats.domain.model.HomeMood
import com.clibeats.domain.model.HomeShelf
import com.clibeats.domain.model.Track
import com.clibeats.domain.provider.MusicProvider
import com.clibeats.domain.provider.ProviderRegistry
import com.clibeats.domain.provider.ProviderResult
import com.clibeats.domain.repository.PlaybackRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var musicProvider: MusicProvider
    private lateinit var providerRegistry: ProviderRegistry
    private lateinit var appPreferences: AppPreferences
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        musicProvider = mock()
        providerRegistry = mock()
        appPreferences = mock()

        whenever(providerRegistry.getProvider(any())).thenReturn(musicProvider)
        whenever(providerRegistry.defaultProvider()).thenReturn(musicProvider)
        whenever(providerRegistry.providers).thenReturn(listOf(musicProvider))
        whenever(appPreferences.activeProviderId).thenReturn(flowOf("youtube_music"))
        whenever(appPreferences.homeView).thenReturn(flowOf(null))
        whenever(appPreferences.autoQueueRelated).thenReturn(flowOf(false))
        whenever(appPreferences.autoplayEnabled).thenReturn(flowOf(true))
        // Trending loads as soon as the ViewModel starts; tests that care stub their own result.
        runBlocking { whenever(musicProvider.trending(any())).thenReturn(ProviderResult.Success(emptyList())) }

        val networkMonitor: NetworkMonitor = mock()
        whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(true))

        viewModel = HomeViewModel(providerRegistry, appPreferences, networkMonitor)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading`() {
        assertThat(viewModel.uiState.value).isEqualTo(HomeUiState.Loading)
    }

    @Test
    fun `trending success populates tracks`() =
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            whenever(musicProvider.trending(any())).thenReturn(
                ProviderResult.Success(listOf(fakeTrack())),
            )

            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state).isInstanceOf(HomeUiState.Success::class.java)
            assertThat((state as HomeUiState.Success).tracks).hasSize(1)
            assertThat(state.tracks[0].title).isEqualTo("Trending Track")
        }

    @Test
    fun `trending error surfaces message`() =
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            whenever(musicProvider.trending(any())).thenReturn(
                ProviderResult.Error("provider offline"),
            )

            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state).isInstanceOf(HomeUiState.Error::class.java)
            assertThat((state as HomeUiState.Error).message).isEqualTo("provider offline")
        }

    @Test
    fun `trending loads once and only reloads on request`() =
        runTest {
            whenever(musicProvider.trending(any())).thenReturn(ProviderResult.Success(listOf(fakeTrack())))
            // Leaving Home and coming back re-subscribes to the state; that must not fetch again.
            repeat(2) {
                val job =
                    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
                testDispatcher.scheduler.advanceUntilIdle()
                job.cancel()
                testDispatcher.scheduler.advanceTimeBy(10_000)
            }
            verify(musicProvider, times(1)).trending(any())

            viewModel.refreshTrending()
            testDispatcher.scheduler.advanceUntilIdle()
            verify(musicProvider, times(2)).trending(any())
            assertThat(viewModel.uiState.value).isInstanceOf(HomeUiState.Success::class.java)
        }

    @Test
    fun `a failed first load is retried`() =
        runTest {
            whenever(musicProvider.trending(any()))
                .thenReturn(ProviderResult.Error("no connection yet"))
                .thenReturn(ProviderResult.Success(listOf(fakeTrack())))

            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.uiState.value).isInstanceOf(HomeUiState.Success::class.java)
        }

    @Test
    fun `activeProviderName reflects selected provider`() =
        runTest {
            whenever(musicProvider.displayName).thenReturn("YouTube Music")
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.activeProviderName.collect()
            }
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.activeProviderName.value).isEqualTo("YouTube Music")
        }

    @Test
    fun `playFromList starts a fresh radio from the tapped track when autoplay is on`() =
        runTest {
            val playbackRepository: PlaybackRepository = mock()
            val networkMonitor: NetworkMonitor = mock()
            whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(true))
            val vm =
                HomeViewModel(
                    providerRegistry = providerRegistry,
                    appPreferences = appPreferences,
                    networkMonitor = networkMonitor,
                    playbackRepository = playbackRepository,
                )
            val t1 = fakeTrack()
            val t2 = t1.copy(id = "audius:xyz", title = "Second")

            vm.playFromList(listOf(t1, t2), 1)
            testDispatcher.scheduler.advanceUntilIdle()

            verify(playbackRepository).playFrom(listOf(t2), 0, "Home")
        }

    @Test
    fun `with the home feed, the first load runs on creation and shows the feed`() =
        runTest {
            // Like the app: the first load starts immediately while the ViewModel is being created.
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val feedRepository: HomeFeedRepository = mock()
            val feed =
                HomeFeed(
                    moods = listOf(HomeMood("Relax", "relax")),
                    shelves = listOf(HomeShelf("Quick picks", "", listOf(HomeItem.Song(fakeTrack())))),
                )
            whenever(feedRepository.feed(null)).thenReturn(feed)
            val networkMonitor: NetworkMonitor = mock()
            whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(true))

            val vm =
                HomeViewModel(
                    providerRegistry = providerRegistry,
                    appPreferences = appPreferences,
                    networkMonitor = networkMonitor,
                    homeFeedRepository = feedRepository,
                )
            advanceUntilIdle()

            assertThat(vm.feed.value).isEqualTo(FeedState.Loaded(feed))
            assertThat(vm.moods.value.map { it.title }).containsExactly("Relax")
        }

    @Test
    fun `with no feed at all, Home falls back to Trending`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val feedRepository: HomeFeedRepository = mock()
            whenever(feedRepository.feed(null)).thenReturn(null)
            whenever(musicProvider.trending(any())).thenReturn(ProviderResult.Success(listOf(fakeTrack())))
            val networkMonitor: NetworkMonitor = mock()
            whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(true))

            val vm =
                HomeViewModel(
                    providerRegistry = providerRegistry,
                    appPreferences = appPreferences,
                    networkMonitor = networkMonitor,
                    homeFeedRepository = feedRepository,
                )
            advanceUntilIdle()

            assertThat(vm.feed.value).isNull()
            assertThat(vm.uiState.value).isEqualTo(HomeUiState.Success(listOf(fakeTrack())))
        }

    private fun fakeTrack(): Track =
        Track(
            id = "audius:abc",
            title = "Trending Track",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L,
            artworkUrl = null,
            streamUrl = "https://cdn.example/stream",
            providerId = "audius",
        )
}
