@file:Suppress("ForbiddenImport")

package com.clibeats.data.importer

import com.clibeats.data.provider.api.InnerTubeHeaderInterceptor
import com.clibeats.data.provider.dto.BrowseRequest
import com.clibeats.data.provider.dto.SearchRequest
import com.clibeats.data.provider.mapper.playlistTracks
import com.clibeats.data.provider.mapper.toAlbumHits
import com.clibeats.data.provider.mapper.toTrackList
import com.clibeats.di.NetworkModule
import com.clibeats.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Measures import accuracy against the real YouTube Music, with the app's own search steps and matcher.
 * Skipped unless pointed at Exportify files:
 *
 * ```
 * CLIBEATS_IMPORT_CSV_DIR=/path/to/unzipped/exportify  CLIBEATS_IMPORT_PLAYLISTS="mix.csv,favorites.csv"
 * CLIBEATS_IMPORT_REPORT=/path/report.txt  ./gradlew testDebugUnitTest --tests "*ImportAccuracyLiveTest*"
 * ```
 *
 * The report lists every song with what it matched (or the top results it rejected), for review by a person:
 * only a listener can tell a wrong match from a right one.
 */
@Suppress("LongMethod") // a single end-to-end check, kept in one readable function
class ImportAccuracyLiveTest {
    @Test
    fun importPlaylists_reportMatches() {
        val dir = System.getenv("CLIBEATS_IMPORT_CSV_DIR")
        assumeTrue("Set CLIBEATS_IMPORT_CSV_DIR to run the live import check", dir != null)
        val names = System.getenv("CLIBEATS_IMPORT_PLAYLISTS")?.split(',')?.map { it.trim() }.orEmpty()
        val report = File(System.getenv("CLIBEATS_IMPORT_REPORT") ?: "build/import-report.txt")
        // Search results depend on the region; match the phone's (e.g. CLIBEATS_IMPORT_REGION=IN).
        System.getenv("CLIBEATS_IMPORT_REGION")?.let { Locale.setDefault(Locale("en", it)) }

        val api =
            NetworkModule.provideInnerTubeApi(
                NetworkModule.provideInnerTubeOkHttpClient(InnerTubeHeaderInterceptor()),
                NetworkModule.provideJson(),
            )
        // Same steps as the app (PlaylistImporter), without the provider's playback dependencies.
        val search =
            SongSearch(
                search = { query, songsOnly ->
                    // Retries like the app's searchWithRetry, so a rate-limited request isn't scored as a miss.
                    var result: List<Track>? = null
                    for (attempt in 1..ATTEMPTS) {
                        result =
                            runCatching {
                                val request = SearchRequest.forQuery(query, filterSongs = songsOnly)
                                api.search(request).toTrackList().take(RESULTS)
                            }.getOrNull()
                        if (result != null) break
                        delay(RETRY_MS * attempt)
                    }
                    result
                },
                searchAlbums = { query ->
                    runCatching { api.search(SearchRequest.forAlbums(query)).toAlbumHits() }.getOrNull()
                },
                albumTracks = { album ->
                    runCatching {
                        api.browse(BrowseRequest.page(album.browseId)).playlistTracks().map {
                            it.copy(
                                artist = it.artist.ifBlank { album.artist },
                                album = it.album.ifBlank { album.title },
                                artworkUrl = it.artworkUrl ?: album.artworkUrl,
                            )
                        }
                    }.getOrNull()
                },
            )
        val allowClosest = System.getenv("CLIBEATS_IMPORT_CLOSEST") == "1"

        val out = StringBuilder()
        runBlocking(Dispatchers.IO) {
            for (name in names) {
                for (playlist in ExportifyCsv.parse(name, File(dir, name).readText())) {
                    val started = System.currentTimeMillis()
                    val gate = Semaphore(PARALLEL)
                    val outcomes =
                        playlist.songs
                            .map { song -> async { gate.withPermit { song to search.find(song, allowClosest) } } }
                            .awaitAll()
                    val seconds = (System.currentTimeMillis() - started) / MS_PER_S
                    val found = outcomes.count { it.second.match != null }
                    out.appendLine("### ${playlist.name}: $found of ${outcomes.size} found in ${seconds}s")
                    outcomes.forEachIndexed { i, (song, outcome) ->
                        val spotify = "${song.title} | ${song.artistLine} | ${song.album} | ${time(song.durationMs)}"
                        out.appendLine("${i + 1}. SP: $spotify")
                        val match = outcome.match
                        if (match != null) {
                            val label = if (outcome.closest) " (closest version)" else ""
                            out.appendLine("   OK$label: ${describe(match)}")
                        } else {
                            out.appendLine("   MISS${if (!outcome.reached) " (no connection)" else ""}; top results:")
                            outcome.seen.distinctBy { it.id }.take(TOP_REJECTED).forEach {
                                out.appendLine("     - ${describe(it)}")
                            }
                        }
                    }
                }
            }
        }
        report.writeText(out.toString())
    }

    private fun describe(t: Track) = "${t.title} | ${t.artist} | ${t.album} | ${time(t.durationMs)} | ${t.id}"

    private fun time(ms: Long) = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

    private companion object {
        const val PARALLEL = 3
        const val RESULTS = 10
        const val ATTEMPTS = 3
        const val RETRY_MS = 2_000L
        const val TOP_REJECTED = 6
        const val MS_PER_S = 1000
    }
}
