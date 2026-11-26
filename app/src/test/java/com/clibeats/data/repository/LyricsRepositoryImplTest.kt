@file:Suppress("ForbiddenImport")

package com.clibeats.data.repository

import com.clibeats.data.local.dao.LyricsCacheDao
import com.clibeats.data.local.entity.LyricsCacheEntity
import com.clibeats.data.lyrics.LrcLibApi
import com.clibeats.data.lyrics.LrcLibDto
import com.clibeats.domain.model.LyricLine
import com.clibeats.domain.model.Track
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

class LyricsRepositoryImplTest {
    private val api: LrcLibApi = mock()
    private val dao: LyricsCacheDao = mock()
    private val repository = LyricsRepositoryImpl(api, dao)

    private fun track(
        id: String = "s1",
        title: String = "Midnight City (Official Video)",
        album: String = "Hurry Up",
    ) = Track(
        id = id,
        title = title,
        artist = "M83",
        album = album,
        durationMs = 243_000L,
        artworkUrl = null,
        streamUrl = null,
        providerId = "youtube_music",
    )

    @Test
    fun `returns cached synced lyrics without hitting api`() =
        runTest {
            whenever(dao.getById("s1")).thenReturn(
                LyricsCacheEntity(
                    songId = "s1",
                    syncedLrc = "[00:10.00]Waiting in a car",
                    plainLyrics = "Waiting in a car",
                    fetchedAt = 1_000L,
                ),
            )

            val result = repository.getLyrics(track())

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrThrow()).containsExactly(LyricLine(10_000L, "Waiting in a car"))
            verify(api, never()).getLyrics(any(), any(), any(), any())
        }

    @Test
    fun `fetches cleans title caches and parses synced lyrics on cache miss`() =
        runTest {
            whenever(dao.getById("s1")).thenReturn(null)
            whenever(
                api.getLyrics(
                    trackName = "Midnight City",
                    artistName = "M83",
                    albumName = "Hurry Up",
                    duration = 243,
                ),
            ).thenReturn(
                LrcLibDto(
                    syncedLyrics = "[00:05.50]Line one\n[00:10.00]Line two",
                    plainLyrics = "Line one\nLine two",
                ),
            )

            val result = repository.getLyrics(track())

            assertThat(result.getOrThrow()).containsExactly(
                LyricLine(5_500L, "Line one"),
                LyricLine(10_000L, "Line two"),
            ).inOrder()
            verify(dao).insert(
                argThat {
                    songId == "s1" && syncedLrc?.contains("Line one") == true && plainLyrics == "Line one\nLine two"
                },
            )
        }

    @Test
    fun `returns failure when api fails`() =
        runTest {
            whenever(dao.getById("s1")).thenReturn(null)
            whenever(api.getLyrics(any(), any(), any(), any())).thenAnswer { throw IOException("offline") }

            val result = repository.getLyrics(track(album = ""))

            assertThat(result.isFailure).isTrue()
        }
}
