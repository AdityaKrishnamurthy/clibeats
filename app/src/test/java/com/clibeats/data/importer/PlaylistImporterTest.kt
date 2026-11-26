@file:Suppress("ForbiddenImport")

package com.clibeats.data.importer

import android.content.Context
import com.clibeats.data.local.dao.PlaylistDao
import com.clibeats.data.local.dao.SongDao
import com.clibeats.data.provider.YouTubeMusicProvider
import com.clibeats.domain.model.Playlist
import com.clibeats.domain.model.Track
import com.clibeats.domain.provider.ProviderResult
import com.clibeats.domain.repository.PlaylistRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PlaylistImporterTest {
    private val context: Context = mock()
    private val youtube: YouTubeMusicProvider = mock()
    private val playlistRepository: PlaylistRepository = mock()
    private val playlistDao: PlaylistDao = mock()
    private val songDao: SongDao = mock()
    private lateinit var importer: PlaylistImporter

    private fun track(
        id: String,
        title: String = "Track $id",
        artist: String = "Artist $id",
        durationMs: Long = 180_000L,
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = "Album",
        durationMs = durationMs,
        artworkUrl = null,
        streamUrl = "https://expiring.example/$id",
        providerId = "youtube_music",
    )

    @Before
    fun setUp() {
        whenever(context.packageName).thenReturn("com.clibeats")
        whenever(context.packageManager).thenReturn(mock())
        importer = PlaylistImporter(context, youtube, playlistRepository, playlistDao, songDao)
    }

    @After
    fun tearDown() {
        importer.cancel()
    }

    @Test
    fun `existingNames excludes non-owned and local file playlists`() =
        runTest {
            whenever(playlistRepository.getAllPlaylistsAsFlow()).thenReturn(
                flowOf(
                    listOf(
                        Playlist(
                            "p1",
                            "Workout",
                            description = null,
                            artworkUrl = null,
                            trackCount = 3,
                            isOwned = true,
                            providerId = "local",
                        ),
                        Playlist(
                            "localpl:m3u",
                            "Disk Mix",
                            description = null,
                            artworkUrl = null,
                            trackCount = 2,
                            isOwned = true,
                            providerId = "local",
                        ),
                        Playlist(
                            "yt1",
                            "Community",
                            description = null,
                            artworkUrl = null,
                            trackCount = 5,
                            isOwned = false,
                            providerId = "youtube_music",
                        ),
                    ),
                ),
            )

            val names = importer.existingNames()

            assertThat(names).containsExactly("Workout")
        }

    @Test
    fun `start matches songs preserves order and replaces or deduplicates names`() =
        runTest {
            whenever(playlistRepository.getAllPlaylistsAsFlow()).thenReturn(
                flowOf(
                    listOf(
                        Playlist(
                            "old_1",
                            "Road Trip",
                            description = null,
                            artworkUrl = null,
                            trackCount = 1,
                            isOwned = true,
                            providerId = "local",
                        ),
                    ),
                ),
            )
            whenever(playlistRepository.createAppPlaylist(any(), any())).thenReturn("new_pl_1")
            val candidate1 = track("yt_a", title = "Midnight City", artist = "M83", durationMs = 243_000L)
            val candidate2 = track("yt_b", title = "Intro", artist = "The xx", durationMs = 127_000L)
            whenever(youtube.search(any(), any())).thenReturn(
                ProviderResult.Success(listOf(candidate1, candidate2)),
            )
            whenever(youtube.searchIncludingVideos(any(), any())).thenReturn(
                ProviderResult.Success(listOf(candidate1, candidate2)),
            )

            val source =
                SpotifyPlaylist(
                    name = "Road Trip",
                    songs =
                        listOf(
                            SpotifySong("Midnight City", listOf("M83"), "Hurry Up", 243_000L),
                            SpotifySong("Intro", listOf("The xx"), "xx", 127_000L),
                        ),
                )

            importer.start(listOf(source), replaceExisting = true, allowClosest = false)

            val terminal =
                withContext(Dispatchers.Default) {
                    withTimeout(5_000L) {
                        var state = importer.state.value
                        while (state !is ImportState.Finished) {
                            delay(25L)
                            state = importer.state.value
                        }
                        state
                    }
                }

            assertThat(terminal.cancelled).isFalse()
            val pl = terminal.playlists.single()
            assertThat(pl.matched.sortedBy { it.index }.mapNotNull { it.match?.id })
                .containsExactly("yt_a", "yt_b")
                .inOrder()
            verify(playlistRepository).deletePlaylist("old_1")
            verify(playlistRepository).createAppPlaylist(eq("Road Trip"), eq(listOf("yt_a", "yt_b")))
            // Expiring streamUrl stripped before persistence.
            verify(songDao).insertAllIfAbsent(any())
            verify(playlistDao, never()).reorderPlaylistSongs(any(), any())
        }

    @Test
    fun `uniqueName appends incrementing suffix on collision`() {
        assertThat(PlaylistImporter.uniqueName("Nihon", setOf("Nihon", "Nihon (2)"))).isEqualTo("Nihon (3)")
        assertThat(PlaylistImporter.uniqueName("Fresh", setOf("Other"))).isEqualTo("Fresh")
    }
}
