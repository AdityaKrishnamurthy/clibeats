@file:Suppress("ForbiddenImport") // data layer: the rule is meant for presentation

package com.clibeats.data.link

import com.clibeats.data.importer.PlaylistImporter
import com.clibeats.data.importer.SpotifyPlaylist
import com.clibeats.data.provider.YouTubeMusicProvider
import com.clibeats.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** Links handed to CLIBeats by Android (opened by default, or shared), waiting for the app's screen. */
@Singleton
class IncomingLinks
    @Inject
    constructor() {
        // Replayed once so a link that arrives while the app starts up isn't lost.
        private val _links = MutableSharedFlow<MusicLink>(replay = 1, extraBufferCapacity = 1)
        val links: SharedFlow<MusicLink> = _links.asSharedFlow()

        fun offer(link: MusicLink) {
            _links.tryEmit(link)
        }

        /** Called once a link is handled, so it isn't replayed to the next screen that listens. */
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        fun handled() = _links.resetReplayCache()
    }

/** What a link turns into. */
sealed interface LinkTarget {
    data class Play(
        val tracks: List<Track>,
    ) : LinkTarget

    /** A Spotify playlist or album: imported like an Exportify file. */
    data class Import(
        val playlist: SpotifyPlaylist,
    ) : LinkTarget

    data class Failed(
        val message: String,
    ) : LinkTarget
}

@Singleton
class LinkResolver
    @Inject
    constructor(
        private val youtube: YouTubeMusicProvider,
        private val importer: PlaylistImporter,
        private val http: OkHttpClient,
    ) {
        suspend fun resolve(link: MusicLink): LinkTarget =
            withContext(Dispatchers.IO) {
                when (link) {
                    is MusicLink.YouTubeSong ->
                        youtube.songById(link.videoId)?.let { LinkTarget.Play(listOf(it)) }
                            ?: LinkTarget.Failed(NO_CONNECTION)
                    is MusicLink.YouTubePage -> youtubePage(link.browseId)
                    is MusicLink.SpotifyTrack -> spotifyTrack(link.id)
                    is MusicLink.SpotifyCollection ->
                        spotify(link.kind, link.id)?.let { LinkTarget.Import(it) }
                            ?: LinkTarget.Failed(SPOTIFY_UNREADABLE)
                    is MusicLink.SpotifyShort ->
                        follow(link.url)?.let { resolve(it) } ?: LinkTarget.Failed(SPOTIFY_UNREADABLE)
                }
            }

        private suspend fun youtubePage(browseId: String): LinkTarget {
            val page = youtube.page(browseId) ?: return LinkTarget.Failed(NO_CONNECTION)
            return if (page.second.isEmpty()) {
                LinkTarget.Failed("That playlist is empty or private.")
            } else {
                LinkTarget.Play(page.second)
            }
        }

        /** A Spotify song is found on YouTube Music exactly like a song in an imported playlist. */
        private suspend fun spotifyTrack(id: String): LinkTarget {
            val song = spotify("track", id)?.songs?.firstOrNull() ?: return LinkTarget.Failed(SPOTIFY_UNREADABLE)
            return importer.findSong(song)?.let { LinkTarget.Play(listOf(it)) }
                ?: LinkTarget.Failed("Couldn't find \"${song.title}\" on YouTube Music.")
        }

        /** A Spotify track, album or playlist read from its embed page (blocking; call off the main thread). */
        fun spotify(
            kind: String,
            id: String,
        ): SpotifyPlaylist? = fetch(SpotifyEmbed.url(kind, id))?.second?.let(SpotifyEmbed::parse)

        /** A short link's real address: where it redirects to, or the Spotify link in the page it serves. */
        private fun follow(url: String): MusicLink? {
            val (finalUrl, body) = fetch(url) ?: return null
            return MusicLink.parse(finalUrl)?.takeIf { it !is MusicLink.SpotifyShort }
                ?: SPOTIFY_URL.find(body)?.value?.let(MusicLink::parse)
        }

        /** The final address (after redirects) and the page; null when it can't be loaded. */
        private fun fetch(url: String): Pair<String, String>? =
            runCatching {
                val request = Request.Builder().url(url).header("User-Agent", BROWSER).build()
                http.newCall(request).execute().use { response ->
                    val page = response.body?.string().orEmpty()
                    if (response.isSuccessful) response.request.url.toString() to page else null
                }
            }.getOrNull()

        private companion object {
            const val NO_CONNECTION = "Couldn't open that link. Check your internet connection."
            const val SPOTIFY_UNREADABLE = "Couldn't read that Spotify link. Check your internet connection."

            // Spotify serves the embed page to browsers.
            const val BROWSER =
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36"
            val SPOTIFY_URL =
                Regex("""https://open\.spotify\.com/(?:intl-[a-z-]+/)?(?:track|album|playlist)/[A-Za-z0-9]{22}""")
        }
    }
