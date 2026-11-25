package com.clibeats.domain.radio

import com.clibeats.domain.model.Track
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RadioLanguageTest {
    private fun track(
        id: String,
        title: String,
        artist: String = "Artist",
    ) = Track(id, title, artist, "", 200_000L, null, null, "youtube_music")

    @Test
    fun `script and named languages are recognised, Latin alone is not`() {
        assertThat(RadioLanguage.languageOf("ಬೆಳಗೆದ್ದು", "ಗಾಯಕ")).isEqualTo("kannada")
        assertThat(RadioLanguage.languageOf("నీలి నీలి ఆకాశం")).isEqualTo("telugu")
        assertThat(RadioLanguage.languageOf("夜に駆ける", "YOASOBI")).isEqualTo("japanese")
        assertThat(RadioLanguage.languageOf("Srivalli (Telugu)")).isEqualTo("telugu")
        assertThat(RadioLanguage.languageOf("Kesariya", "Arijit Singh")).isNull()
        // One stray letter in another script doesn't decide the language.
        assertThat(RadioLanguage.languageOf("A very long English title ಅ")).isNull()
    }

    @Test
    fun `same language first, unknown next, other languages last, order kept inside each group`() {
        val kn1 = track("1", "ಹಾಡು ಒಂದು")
        val te = track("2", "Song (Telugu)")
        val latin = track("3", "Romanised Title")
        val kn2 = track("4", "ಹಾಡು ಎರಡು")
        val ta = track("5", "பாடல்")

        val ordered = RadioLanguage.preferSameLanguage("kannada", listOf(kn1, te, latin, kn2, ta))

        assertThat(ordered.map { it.id }).containsExactly("1", "4", "3", "2", "5").inOrder()
    }

    @Test
    fun `a Latin-titled seed takes the clear majority language of its suggestions`() {
        val seed = track("s", "Belageddu")
        val suggestions =
            List(6) { track("k$it", "ಹಾಡು $it") } + List(2) { track("t$it", "పాట $it") } + track("x", "Latin")

        assertThat(RadioLanguage.forRadio(seed, suggestions)).isEqualTo("kannada")
    }

    @Test
    fun `no clear majority means no language preference`() {
        val seed = track("s", "Belageddu")
        val suggestions = List(3) { track("k$it", "ಹಾಡು $it") } + List(3) { track("t$it", "పాట $it") }

        assertThat(RadioLanguage.forRadio(seed, suggestions)).isNull()
        assertThat(RadioLanguage.preferSameLanguage(null, suggestions)).isEqualTo(suggestions)
    }
}
