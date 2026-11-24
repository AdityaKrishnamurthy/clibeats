package com.clibeats.data.importer

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportifyFilesTest {
    private val header =
        "Track URI,ISRC,Track Name,Album Name,Artist Name(s),Release Date,Duration (ms),Popularity"

    private fun csv(vararg rows: String) = (listOf(header) + rows).joinToString("\r\n") + "\r\n"

    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            files.forEach { (name, text) ->
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun parse_readsQuotedFieldsCommasScriptsAndArtistList() {
        val text =
            "﻿" +
                csv(
                    "spotify:track:1,JP1,\"星空花\",\"Hoshizora\",\"MIRA\",2019-07-03,238586,4",
                    "spotify:track:2,X,\"Film Two\",\"Film Two, Vol. 1\"," +
                        "\"D. Iyer;Vinay Prakash\",2016,257706,1",
                    "spotify:track:3,X,\"Say \"\"Hi\"\"\",\"A\",\"B\",2020,1000,1",
                )

        val playlist = ExportifyCsv.parse("anime", text).single()

        assertThat(playlist.songs).hasSize(3)
        assertThat(playlist.songs[0]).isEqualTo(SpotifySong("星空花", listOf("MIRA"), "Hoshizora", 238_586, isrc = "JP1"))
        assertThat(playlist.songs[1].album).isEqualTo("Film Two, Vol. 1")
        assertThat(playlist.songs[1].artists).containsExactly("D. Iyer", "Vinay Prakash").inOrder()
        assertThat(playlist.songs[2].title).isEqualTo("Say \"Hi\"")
    }

    @Test
    fun parse_tuneMyMusicFile_splitsPlaylistsAndSkipsFollowedArtists() {
        val text =
            "\uFEFFTrack name,Artist name,Album,Playlist name,Type,ISRC\n" +
                "\"Song A\",\"Singer One,Chorus\",\"Album A\",\"Mix\",\"Playlist\",\"ISRC1\"\n" +
                "\"Some Channel\",\"\",\"\",\"My Subscriptions (Artists)\",\"Artist\",\"\"\n" +
                "\"Song B\",\"Singer Two\",\"Album B\",\"Other\",\"Playlist\",\"ISRC2\"\n" +
                "\"A video title\",\"\",\"\",\"Liked videos\",\"Favorite\",\"\"\n"

        val playlists = ExportifyCsv.parse("My Library", text)

        assertThat(playlists.map { it.name }).containsExactly("Mix", "Other", "Liked videos").inOrder()
        assertThat(playlists[0].songs.single().artists).containsExactly("Singer One", "Chorus").inOrder()
        assertThat(playlists[0].songs.single().isrc).isEqualTo("ISRC1")
        assertThat(playlists[2].songs.single().artists).isEmpty()
    }

    @Test
    fun parse_rejectsFilesWithoutExportifyColumns() {
        assertThrows(NotAnExportifyFileException::class.java) {
            ExportifyCsv.parse("x", "name,price\napple,3\n")
        }
    }

    @Test
    fun playlistName_comesFromTheFileName() {
        assertThat(ExportifyCsv.playlistName("My_Top_Mix_2024.csv")).isEqualTo("My Top Mix 2024")
        assertThat(ExportifyCsv.playlistName("spotify_playlists/ಹ.csv")).isEqualTo("ಹ")
    }

    @Test
    fun read_singleCsv() {
        val bytes = csv("u,i,Song,Album,Artist,d,1000,1").toByteArray()

        val playlists = ExportifyFiles.read(ByteArrayInputStream(bytes), "AM.csv")

        assertThat(playlists.single().name).isEqualTo("AM")
    }

    @Test
    fun read_zipWithManyCsvs_skipsEmptyAndNonPlaylistFiles() {
        val bytes =
            zip(
                "anime.csv" to csv("u,i,星空花,Hoshizora,MIRA,d,238586,4"),
                "Favorites.csv" to csv("u,i,A,B,C,d,1,1", "u,i,D,E,F,d,2,2"),
                "Saved_Albums.csv" to csv(),
                "notes.txt" to "hello",
                "other.csv" to "a,b\n1,2\n",
                "__MACOSX/._anime.csv" to "junk",
            )

        val playlists = ExportifyFiles.read(ByteArrayInputStream(bytes), "spotify_playlists.zip")

        assertThat(playlists.map { it.name }).containsExactly("Favorites", "anime").inOrder()
        assertThat(playlists.first { it.name == "Favorites" }.songs).hasSize(2)
    }

    @Test
    fun read_zipWithNothingImportable_explainsWhy() {
        val bytes = zip("Saved_Albums.csv" to csv())

        assertThrows(NotAnExportifyFileException::class.java) {
            ExportifyFiles.read(ByteArrayInputStream(bytes), "x.zip")
        }
    }
}
