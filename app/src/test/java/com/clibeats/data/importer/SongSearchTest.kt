package com.clibeats.data.importer

import com.clibeats.domain.model.Track
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SongSearchTest {
    private val song = SpotifySong("Night Sky", listOf("Lumen"), "Night Sky", 174_000)
    private val track = Track("ok", "Night Sky", "Lumen", "Night Sky", 175_000, null, null, "youtube_music")

    @Test
    fun aRefusedSearch_isTriedAgain_beforeTheSongCountsAsNotFound() =
        runTest {
            var calls = 0
            // YouTube Music refuses every search the first time round (throttled), then answers.
            val search =
                SongSearch(search = { _, _ -> if (++calls <= SEARCHES_PER_ROUND) null else listOf(track) })

            val outcome = search.find(song)

            assertThat(outcome.match?.id).isEqualTo("ok")
            assertThat(outcome.reached).isTrue()
        }

    @Test
    fun queries_dropTheExcludeOperator_butKeepHyphenatedWords() {
        val search = SongSearch(search = { _, _ -> emptyList() })

        assertThat(search.plain("Ondu Haadu -Reprised Version Kiran")).isEqualTo("Ondu Haadu Reprised Version Kiran")
        assertThat(search.plain("Song - Live Artist")).isEqualTo("Song - Live Artist")
        assertThat(search.plain("Spider-Man K-pop")).isEqualTo("Spider-Man K-pop")
    }

    @Test
    fun noConnectionAtAll_isReportedAsNotReached() =
        runTest {
            val outcome = SongSearch(search = { _, _ -> null }, searchAlbums = { null }).find(song)

            assertThat(outcome.match).isNull()
            assertThat(outcome.reached).isFalse()
        }

    private companion object {
        // Song queries for this song (title + artist, title) and its video query; the album step needs none.
        const val SEARCHES_PER_ROUND = 3
    }
}
