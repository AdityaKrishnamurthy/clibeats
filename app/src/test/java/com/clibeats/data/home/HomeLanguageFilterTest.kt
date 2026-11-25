package com.clibeats.data.home

import com.clibeats.domain.model.CollectionKind
import com.clibeats.domain.model.HomeItem
import com.clibeats.domain.model.HomeShelf
import com.clibeats.domain.model.Track
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeLanguageFilterTest {
    private fun song(
        title: String,
        artist: String = "Artist",
    ) = HomeItem.Song(Track(title, title, artist, "", 1L, null, null, "youtube_music"))

    private fun playlist(
        title: String,
        subtitle: String = "",
    ) = HomeItem.Collection("VL$title", title, subtitle, null, CollectionKind.PLAYLIST)

    private val picked = setOf(HomeLanguage.HINDI, HomeLanguage.KANNADA, HomeLanguage.JAPANESE, HomeLanguage.ENGLISH)

    @Test
    fun `items named or written in other languages are hidden, unknown ones stay`() {
        val shelf =
            HomeShelf(
                "Trending community playlists",
                "",
                listOf(
                    playlist("Bhojpuri Hits"),
                    playlist("telugu good songs", "Yaswanth • 10M views"),
                    playlist("Sandalwood Romance"),
                    playlist("Old is gold"),
                    song("ಬೆಳಗೆದ್ದು"),
                    song("ప్రేమ"),
                    song("夜に駆ける", "YOASOBI"),
                    song("Hum Jaante Hai"),
                ),
            )

        val titles =
            HomeLanguageFilter.filter(listOf(shelf), picked).single().items.map {
                when (it) {
                    is HomeItem.Song -> it.track.title
                    is HomeItem.Collection -> it.title
                }
            }

        assertThat(titles).containsExactly("Sandalwood Romance", "Old is gold", "ಬೆಳಗೆದ್ದು", "夜に駆ける", "Hum Jaante Hai")
            .inOrder()
    }

    @Test
    fun `a row named after another language goes, and so does a row left empty`() {
        val shelves =
            listOf(
                HomeShelf("Bhojpuri Hits", "", listOf(song("Some Song"))),
                HomeShelf("Tamil corner", "", listOf(song("Song A"))),
                HomeShelf("All Telugu", "", listOf(playlist("Telugu Melodies"))),
                HomeShelf("Hindi Hits", "", listOf(playlist("Bollywood Romance"))),
            )

        val kept = HomeLanguageFilter.filter(shelves, picked).map { it.title }

        assertThat(kept).containsExactly("Hindi Hits")
    }

    @Test
    fun `Devanagari counts for the Hindi family, kanji alone for Japanese or Chinese`() {
        assertThat(HomeLanguageFilter.languageOf("आईने के सौ टुकड़े")).contains("hindi")
        assertThat(HomeLanguageFilter.languageOf("愛")).containsExactly("japanese", "chinese")
        // A named language wins over the script.
        assertThat(HomeLanguageFilter.languageOf("Bhojpuri", "आईने")).containsExactly("bhojpuri")
    }

    @Test
    fun `nothing picked hides nothing`() {
        val shelves = listOf(HomeShelf("Bhojpuri Hits", "", listOf(playlist("Bhojpuri Hits"))))
        assertThat(HomeLanguageFilter.filter(shelves, emptySet())).isEqualTo(shelves)
    }

    @Test
    fun `picker lists every language alphabetically`() {
        val labels = HomeLanguage.ordered.map { it.label }
        assertThat(labels).isEqualTo(labels.sorted())
        assertThat(HomeLanguage.ordered).containsExactlyElementsIn(HomeLanguage.entries)
    }

    @Test
    fun `scripts and names of other languages are told apart`() {
        assertThat(HomeLanguageFilter.languageOf("Кино")).containsExactly("russian", "ukrainian")
        assertThat(HomeLanguageFilter.languageOf("Top French Hits")).containsExactly("french")
        assertThat(HomeLanguageFilter.languageOf("שיר")).containsExactly("hebrew")
    }

    @Test
    fun `saved names map back to languages, skipping unknown ones`() {
        assertThat(HomeLanguage.fromNames(setOf("KANNADA", "JAPANESE", "KLINGON")))
            .containsExactly(HomeLanguage.KANNADA, HomeLanguage.JAPANESE)
    }
}
