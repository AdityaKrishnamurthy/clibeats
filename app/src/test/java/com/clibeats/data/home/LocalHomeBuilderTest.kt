@file:Suppress("ArgumentListWrapping")

package com.clibeats.data.home

import com.clibeats.domain.model.CollectionKind
import com.clibeats.domain.model.HomeItem
import com.clibeats.domain.model.HomeShelf
import com.clibeats.domain.model.Track
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class LocalHomeBuilderTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val day = 24L * 60 * 60 * 1000

    /** 23:00 on 1 June 2026. */
    private val now = LocalDateTime.of(2026, 6, 1, 23, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun song(
        id: String,
        artist: String,
        album: String = "",
        file: String = "/Music/$artist/$album/$id.mp3",
    ) = Track("local:$id", "Song $id", artist, album, 200_000L, "file:///art/$id.jpg", file, "local")

    private fun build(
        songs: List<Track>,
        plays: List<Play> = emptyList(),
        recentlyAdded: List<Track> = emptyList(),
    ) = buildLocalHome(songs, recentlyAdded, plays, now, zone, seed = 42)

    private fun LocalHome.shelf(title: String): HomeShelf? = shelves.firstOrNull { it.title == title }

    private fun HomeShelf.cards() = items.filterIsInstance<HomeItem.Collection>()

    private fun at(
        song: Track,
        msAgo: Long,
    ) = Play(song.id, now - msAgo)

    @Test
    fun `time-of-day mix gathers what was played around this hour`() {
        val late = (1..10).map { song("n$it", "Night Artist") }
        val morning = (1..10).map { song("m$it", "Morning Artist") }
        // Late songs played around 23:00 on past days, morning songs around 08:00.
        val plays =
            late.mapIndexed { i, s -> at(s, (i + 1) * day) } +
                morning.mapIndexed { i, s -> at(s, (i + 1) * day + 15 * 60 * 60 * 1000L) }

        val home = build(late + morning, plays)

        val mixes = home.shelf("Mixes for you")!!.cards()
        val timeMix = mixes.first()
        assertThat(timeMix.title).isEqualTo("Late night mix")
        val mixSongs = home.collections.getValue(timeMix.browseId).map { it.id }
        assertThat(mixSongs).containsExactlyElementsIn(late.map { it.id })
    }

    @Test
    fun `an artist mix adds the artists played in the same sitting`() {
        val a = (1..6).map { song("a$it", "Artist A") }
        val b = (1..6).map { song("b$it", "Artist B feat. Someone") }
        val c = (1..6).map { song("c$it", "Artist C") }
        // A and B played together in one sitting; C on its own a day later.
        val plays =
            (a + b).mapIndexed { i, s -> at(s, 10 * day - i * 60_000L) } +
                c.mapIndexed { i, s -> at(s, 5 * day - i * 60_000L) }

        val home = build(a + b + c, plays)

        val mixA = home.shelf("Mixes for you")!!.cards().first { it.title == "Mix: Artist A" }
        val songs = home.collections.getValue(mixA.browseId).map { it.id }
        assertThat(songs).containsAtLeastElementsIn(a.map { it.id })
        assertThat(songs.any { it.startsWith("local:b") }).isTrue()
        assertThat(songs.none { it.startsWith("local:c") }).isTrue()
        assertThat(mixA.subtitle).contains("Artist B")
    }

    @Test
    fun `albums keep file order and recent plays come first in Jump back in`() {
        val first = listOf(song("02", "X", "Old Album"), song("01", "X", "Old Album"), song("03", "X", "Old Album"))
        val second = (1..3).map { song("s$it", "Y", "New Album") }
        val plays = listOf(at(first[0], 3 * day), at(second[0], day))

        val home = build(first + second, plays)

        val jump = home.shelf("Jump back in")!!.cards()
        assertThat(jump.map { it.title }).containsExactly("New Album", "Old Album").inOrder()
        assertThat(jump.all { it.kind == CollectionKind.ALBUM }).isTrue()
        val oldAlbum = home.collections.getValue(jump[1].browseId)
        assertThat(oldAlbum.map { it.id }).containsExactly("local:01", "local:02", "local:03").inOrder()
    }

    @Test
    fun `same album name in two folders stays two albums`() {
        val one = (1..3).map { song("p$it", "P", "Greatest Hits") }
        val two = (1..3).map { song("q$it", "Q", "Greatest Hits") }

        assertThat(build(one + two).albums).hasSize(2)
    }

    @Test
    fun `forgotten gems are songs played often but not for a month`() {
        val gem = song("gem", "G")
        val recent = song("recent", "G")
        val songs = listOf(gem, recent) + (1..6).map { song("f$it", "Filler") }
        val gemPlays = (1..3).map { at(gem, 40 * day + it) }
        val gemPlaysOthers = songs.subList(2, 5).flatMap { filler -> (1..3).map { at(filler, 45 * day + it) } }
        val recentPlays = (1..3).map { at(recent, day + it) }

        val home = build(songs, gemPlays + gemPlaysOthers + recentPlays)

        val gems = home.shelf("Forgotten gems")!!.songs.map { it.id }
        assertThat(gems).contains("local:gem")
        assertThat(gems).doesNotContain("local:recent")
    }

    @Test
    fun `never played leaves out played songs, with no history it's Discover your library`() {
        val songs = (1..8).map { song("s$it", "A") }

        val played = build(songs, listOf(at(songs[0], day)))
        assertThat(played.shelf("Never played")!!.songs.map { it.id }).doesNotContain("local:s1")

        val fresh = build(songs)
        assertThat(fresh.shelf("Never played")).isNull()
        assertThat(fresh.shelf("Discover your library")!!.songs).hasSize(8)
    }

    @Test
    fun `top artists count a song under its first artist and every card has its songs`() {
        val songs = listOf(song("1", "Main, Guest"), song("2", "Main & Other"), song("3", "Main"), song("4", "Solo"))
        val plays = songs.map { at(it, day) }

        val home = build(songs, plays)

        val top = home.shelf("Your top artists")!!.cards()
        assertThat(top.first().title).isEqualTo("Main")
        assertThat(top.first().kind).isEqualTo(CollectionKind.ARTIST)
        home.shelves.flatMap { it.cards() }.forEach { card ->
            assertThat(home.collections).containsKey(card.browseId)
        }
    }

    @Test
    fun `recently added keeps the given order and only local songs`() {
        val songs = (1..5).map { song("r$it", "A") }
        val online = Track("yt1", "Online", "B", "", 1L, null, null, "youtube_music")

        val home = build(songs, recentlyAdded = listOf(songs[4], online) + songs.take(4))

        assertThat(home.shelf("Recently added")!!.songs.map { it.id })
            .containsExactly("local:r5", "local:r1", "local:r2", "local:r3", "local:r4")
            .inOrder()
    }

    @Test
    fun `an empty library gives an empty page`() {
        val home = build(emptyList())
        assertThat(home.shelves).isEmpty()
        assertThat(home.albums).isEmpty()
    }

    @Test
    fun `part of day names`() {
        assertThat(partOfDay(7 * 60)).isEqualTo("Morning")
        assertThat(partOfDay(14 * 60)).isEqualTo("Afternoon")
        assertThat(partOfDay(19 * 60)).isEqualTo("Evening")
        assertThat(partOfDay(2 * 60)).isEqualTo("Late night")
    }

    @Test
    fun `online rows use their own ids, skip file-only rows, and add On repeat`() {
        val songs = (1..12).map { song("o$it", if (it <= 6) "Artist A" else "Artist B", album = "Album ${it % 2}") }
        val plays = songs.flatMap { s -> (1..3).map { n -> at(s, n * day) } } + songs.take(4).map { at(it, 2 * day) }
        val home =
            buildLocalHome(songs, songs, plays, now, zone, seed = 42, idPrefix = "you", online = true)

        val titles = home.shelves.map { it.title }
        assertThat(titles).containsAtLeast("Mixes for you", "Your top artists", "On repeat")
        assertThat(titles).containsNoneOf("Recently added", "Never played", "Discover your library")
        assertThat(home.collections.keys.all { it.startsWith("you:") }).isTrue()
        assertThat(home.shelf("On repeat")!!.items).isNotEmpty()
    }

    @Test
    fun `online rows are empty before anything was played`() {
        val home =
            buildLocalHome(emptyList(), emptyList(), emptyList(), now, zone, seed = 1, idPrefix = "you", online = true)
        assertThat(home.shelves).isEmpty()
    }
}
