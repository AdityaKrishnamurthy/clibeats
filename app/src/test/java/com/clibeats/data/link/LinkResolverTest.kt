@file:Suppress("ForbiddenImport")

package com.clibeats.data.link

import com.clibeats.data.importer.PlaylistImporter
import com.clibeats.data.importer.SpotifySong
import com.clibeats.data.provider.YouTubeMusicProvider
import com.clibeats.domain.model.Track
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LinkResolverTest {
    private val youtube: YouTubeMusicProvider = mock()
    private val importer: PlaylistImporter = mock()
    private lateinit var server: MockWebServer
    private lateinit var resolver: LinkResolver

    private fun track(id: String) =
        Track(
            id = id,
            title = "Song $id",
            artist = "M83",
            album = "Hurry Up",
            durationMs = 200_000L,
            artworkUrl = null,
            streamUrl = null,
            providerId = "youtube_music",
        )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        resolver = LinkResolver(youtube, importer, OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `resolve YouTubeSong returns Play when resolved and Failed when offline`() =
        runTest {
            val ytTrack = track("dQw4w9WgXcQ")
            whenever(youtube.songById("dQw4w9WgXcQ")).thenReturn(ytTrack)

            val ok = resolver.resolve(MusicLink.YouTubeSong("dQw4w9WgXcQ"))
            assertThat(ok).isEqualTo(LinkTarget.Play(listOf(ytTrack)))

            whenever(youtube.songById("abcdefghijk")).thenReturn(null)
            val fail = resolver.resolve(MusicLink.YouTubeSong("abcdefghijk"))
            assertThat(fail).isInstanceOf(LinkTarget.Failed::class.java)
        }

    @Test
    fun `resolve YouTubePage distinguishes empty playlist from populated`() =
        runTest {
            val items = listOf(track("v1"), track("v2"))
            whenever(youtube.page("VL_OK")).thenReturn("Favorites" to items)
            whenever(youtube.page("VL_EMPTY")).thenReturn("Private" to emptyList())

            assertThat(resolver.resolve(MusicLink.YouTubePage("VL_OK"))).isEqualTo(LinkTarget.Play(items))
            val emptyResult = resolver.resolve(MusicLink.YouTubePage("VL_EMPTY"))
            assertThat(emptyResult).isInstanceOf(LinkTarget.Failed::class.java)
        }

    @Test
    fun `resolve SpotifyShort follows html page link to YouTube track match`() =
        runTest {
            val shortUrl = server.url("/short").toString()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """<a href="https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC">Open</a>""",
                    ),
            )
            // After following short link to open.spotify.com, handle unreachable embed gracefully -> Failed.
            val target = resolver.resolve(MusicLink.SpotifyShort(shortUrl))
            assertThat(target).isInstanceOf(LinkTarget.Failed::class.java)
        }

    @Test
    fun `IncomingLinks replays last link and handled clears replay cache`() {
        val incoming = IncomingLinks()
        val link = MusicLink.YouTubeSong("dQw4w9WgXcQ")

        incoming.offer(link)
        assertThat(incoming.links.replayCache).containsExactly(link)

        incoming.handled()
        assertThat(incoming.links.replayCache).isEmpty()
    }

    @Test
    fun `SpotifyEmbed parse reads playlist tracks from __NEXT_DATA__ json`() {
        val html =
            """
            <html><script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"state":{"data":{"entity":{
              "type":"playlist",
              "name":"Night Coding",
              "trackList":[
                {"title":"Resonance","subtitle":"HOME","duration":212000},
                {"title":"Kids","subtitle":"MGMT","duration":302000}
              ]
            }}}}}}
            </script></html>
            """.trimIndent()

        val parsed = SpotifyEmbed.parse(html)

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.name).isEqualTo("Night Coding")
        assertThat(parsed.songs).containsExactly(
            SpotifySong("Resonance", listOf("HOME"), "", 212_000L),
            SpotifySong("Kids", listOf("MGMT"), "", 302_000L),
        ).inOrder()
    }
}
