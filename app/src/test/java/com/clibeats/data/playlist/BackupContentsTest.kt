@file:Suppress(
    "MaxLineLength",
    "MaximumLineLength",
    "ArgumentListWrapping",
    "ktlint:standard:max-line-length",
    "ktlint:standard:argument-list-wrapping",
)

package com.clibeats.data.playlist

import com.clibeats.domain.model.Track
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The full backup's history and downloads: packed, written, read back and restored without doubling. */
class BackupContentsTest {
    private fun exchange(id: String) = CliBeatsTrack("youtube_music", id, "Song $id", "Artist", "", 200_000L)

    private fun track(id: String) = Track("youtube_music:$id", "Song $id", "Artist", "", 200_000L, null, null, "youtube_music")

    @Test
    fun `history packs each song once and survives a write and read`() {
        val plays = listOf("a" to 300L, "b" to 200L, "a" to 100L, "gone" to 50L)
        val (tracks, entries) = packHistory(plays, mapOf("a" to exchange("a"), "b" to exchange("b")))
        assertThat(tracks.map { it.sourceId }).containsExactly("a", "b").inOrder()
        assertThat(entries).containsExactly(CliBeatsPlay(0, 300L), CliBeatsPlay(1, 200L), CliBeatsPlay(0, 100L)).inOrder()

        val back = CliBeatsFileCodec.decode(CliBeatsFileCodec.encode(CliBeatsFile(historyTracks = tracks, history = entries)))
        assertThat(back.history).isEqualTo(entries)
        assertThat(back.historyTracks).isEqualTo(tracks)
    }

    @Test
    fun `restoring plays keeps the counts and never doubles them`() {
        val tracks = listOf(track("a"), track("b"), null)
        val plays =
            listOf(
                CliBeatsPlay(0, 300L),
                CliBeatsPlay(1, 200L),
                CliBeatsPlay(0, 100L),
                CliBeatsPlay(2, 90L),
                CliBeatsPlay(5, 80L),
                CliBeatsPlay(0, 0L),
            )

        val first = playsToRestore(plays, tracks, emptySet())
        assertThat(first.map { it.first.id to it.second }).containsExactly(
            "youtube_music:a" to 300L,
            "youtube_music:b" to 200L,
            "youtube_music:a" to 100L,
        ).inOrder()

        // The same backup again: everything is already there.
        val have = first.mapTo(HashSet()) { it.first.id to it.second }
        assertThat(playsToRestore(plays, tracks, have)).isEmpty()
        // A duplicate play inside one backup is added once.
        assertThat(playsToRestore(listOf(CliBeatsPlay(0, 5L), CliBeatsPlay(0, 5L)), tracks, emptySet())).hasSize(1)
    }

    @Test
    fun `downloads to offer skip ones already here, local songs and repeats`() {
        val local = Track("local:x", "Local", "A", "", 1L, null, null, "local")
        val offer = downloadsToOffer(listOf(track("a"), track("b"), track("a"), local), have = setOf("youtube_music:b"))
        assertThat(offer.map { it.id }).containsExactly("youtube_music:a")
    }
}
