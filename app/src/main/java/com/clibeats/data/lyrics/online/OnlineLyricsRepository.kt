@file:Suppress("ForbiddenImport", "TooManyFunctions", "ReturnCount", "NestedBlockDepth", "MagicNumber")

package com.clibeats.data.lyrics.online

import com.clibeats.data.local.dao.LyricsCacheDao
import com.clibeats.data.local.entity.LyricsCacheEntity
import com.clibeats.data.provider.api.InnerTubeApi
import com.clibeats.data.provider.dto.BrowseRequest
import com.clibeats.data.provider.dto.InnerTubeContext
import com.clibeats.data.provider.dto.NextRequest
import com.clibeats.data.provider.mapper.findAll
import com.clibeats.data.provider.mapper.nav
import com.clibeats.data.provider.mapper.safeString
import com.clibeats.domain.model.Lyrics
import com.clibeats.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What a lookup found: lyrics, nothing (every source answered "no"), or no answer at all (offline). */
sealed interface OnlineLyricsResult {
    data class Found(
        val lyrics: Lyrics,
    ) : OnlineLyricsResult

    data object NotFound : OnlineLyricsResult

    data object Unreachable : OnlineLyricsResult
}

/**
 * Lyrics for songs streamed from YouTube Music (and the same songs downloaded in the app), from several free
 * sources at once. Syllable-synced lyrics always win, then line-synced, then plain text:
 * - LyricsPlus: Apple Music / Musixmatch / QQ syllable timing, often with a translation and pronunciation.
 * - BetterLyrics: Apple Music syllable timing (TTML).
 * - LRCLIB: line-synced and plain lyrics, matched by length.
 * - YouTube Music's own lyrics tab: plain text, for most songs.
 * - lyrics.ovh: plain text, last resort.
 *
 * Results are saved (Room) per song, so a song played or downloaded once has its lyrics offline too.
 */
@Singleton
class OnlineLyricsRepository
    @Inject
    constructor(
        okHttpClient: OkHttpClient,
        private val lyricsCacheDao: LyricsCacheDao,
        private val innerTubeApi: InnerTubeApi,
    ) {
        private val http =
            okHttpClient
                .newBuilder()
                .callTimeout(SOURCE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()
        private val json = Json { ignoreUnknownKeys = true }
        private val memory = RecentMap<OnlineLyricsResult>(MEMORY_SIZE)

        /** Saved lyrics only (no network): what a downloaded song shows offline. */
        suspend fun cached(track: Track): Lyrics? =
            (memory.get(track.id) as? OnlineLyricsResult.Found)?.lyrics ?: readSaved(track.id)?.first

        suspend fun lyricsFor(track: Track): OnlineLyricsResult {
            memory.get(track.id)?.let { return it }
            val saved = readSaved(track.id)
            if (saved != null) {
                val (lyrics, fetchedAt) = saved
                if (lyrics != null) return OnlineLyricsResult.Found(lyrics).also { memory.put(track.id, it) }
                // "Not found" is remembered for a few days, then searched again (sources add songs).
                if (System.currentTimeMillis() - fetchedAt < NOT_FOUND_RETRY_MS) return OnlineLyricsResult.NotFound
            }
            val result = withContext(Dispatchers.IO) { search(queryFor(track)) }
            when (result) {
                is OnlineLyricsResult.Found -> save(track.id, result.lyrics)
                OnlineLyricsResult.NotFound -> saveNotFound(track.id)
                OnlineLyricsResult.Unreachable -> Unit
            }
            if (result != OnlineLyricsResult.Unreachable) memory.put(track.id, result)
            return result
        }

        /** Stores [lyrics] for [track] (e.g. after translations were added), replacing what was saved. */
        suspend fun save(
            trackId: String,
            lyrics: Lyrics,
        ) {
            memory.put(trackId, OnlineLyricsResult.Found(lyrics))
            runCatching {
                lyricsCacheDao.insert(
                    LyricsCacheEntity(
                        songId = trackId,
                        syncedLrc = json.encodeToString(Lyrics.serializer(), lyrics),
                        plainLyrics = null,
                        fetchedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }

        /** Fetches and saves [track]'s lyrics now (when it's downloaded), so they're there offline. */
        suspend fun prefetch(track: Track) {
            runCatching { lyricsFor(track) }
        }

        private suspend fun saveNotFound(trackId: String) {
            runCatching { lyricsCacheDao.insert(LyricsCacheEntity(trackId, null, null, System.currentTimeMillis())) }
        }

        private suspend fun readSaved(trackId: String): Pair<Lyrics?, Long>? {
            val row = runCatching { lyricsCacheDao.getById(trackId) }.getOrNull() ?: return null
            val lyrics =
                row.syncedLrc?.let { runCatching { json.decodeFromString(Lyrics.serializer(), it) }.getOrNull() }
            return lyrics to row.fetchedAt
        }

        // ── searching ──

        private class SourceAnswer(
            val lyrics: Lyrics?,
            /** False when the source couldn't be reached (network error, timeout), not just "no lyrics". */
            val answered: Boolean,
            val preference: Int,
        )

        /**
         * Asks every source at once and keeps the best answer. Stops early once Apple syllable lyrics arrive
         * (nothing ranks above them); otherwise waits for all, each capped at [SOURCE_TIMEOUT_MS].
         */
        internal suspend fun search(query: LyricsQuery): OnlineLyricsResult =
            coroutineScope {
                val answers = Channel<SourceAnswer>(Channel.UNLIMITED)
                val sources: List<Pair<Int, suspend () -> Lyrics?>> =
                    listOf(
                        0 to { lyricsPlus(query) },
                        1 to { betterLyrics(query) },
                        2 to { lrclib(query) },
                        YOUTUBE_PREFERENCE to { youTubeMusic(query) },
                        4 to { lyricsOvh(query) },
                    )
                val jobs =
                    sources.map { (preference, fetch) ->
                        async {
                            val answer =
                                try {
                                    val lyrics = withTimeout(SOURCE_TIMEOUT_MS) { fetch() }
                                    SourceAnswer(lyrics, answered = true, preference)
                                } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                                    // Too slow to answer: not a "no".
                                    SourceAnswer(null, answered = false, preference)
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    SourceAnswer(null, answered = false, preference)
                                }
                            answers.send(answer)
                        }
                    }
                var best: SourceAnswer? = null
                var anyAnswered = false
                val found = mutableListOf<SourceAnswer>()
                repeat(sources.size) {
                    val answer = answers.receive()
                    anyAnswered = anyAnswered || answer.answered
                    if (answer.lyrics != null) found += answer
                    if (answer.lyrics != null && (best == null || better(answer, best!!))) best = answer
                    if (best?.lyrics?.let(::rank) == RANK_APPLE_SYLLABLE) {
                        jobs.forEach { it.cancel() }
                        return@coroutineScope OnlineLyricsResult.Found(best!!.lyrics!!)
                    }
                }
                val chosen = choose(found, best)
                chosen?.let { OnlineLyricsResult.Found(it) }
                    ?: if (anyAnswered) OnlineLyricsResult.NotFound else OnlineLyricsResult.Unreachable
            }

        /**
         * The final pick. YouTube Music's own lyrics belong to the exact video playing, so when it has them, only
         * versions sharing its words count (title, artist and length can't tell a song's English and Japanese
         * versions apart); among those the best-timed wins, and YouTube's text itself when none match.
         * Without them, non-Apple syllable lyrics are checked against the other sources instead.
         */
        private fun choose(
            found: List<SourceAnswer>,
            best: SourceAnswer?,
        ): Lyrics? {
            val reference =
                found.firstOrNull { it.preference == YOUTUBE_PREFERENCE }?.lyrics
                    ?: return best?.lyrics?.let { checkedAgainstOthers(it, found.mapNotNull(SourceAnswer::lyrics)) }
            val matching =
                found.filter { answer ->
                    val lyrics = answer.lyrics ?: return@filter false
                    lyrics === reference || similarity(lyrics, reference) >= MIN_SIMILARITY
                }
            return matching.reduceOrNull { a, b -> if (better(b, a)) b else a }?.lyrics ?: reference
        }

        private fun better(
            a: SourceAnswer,
            b: SourceAnswer,
        ): Boolean {
            val ra = rank(a.lyrics!!)
            val rb = rank(b.lyrics!!)
            return ra > rb || (ra == rb && a.preference < b.preference)
        }

        private suspend fun lyricsPlus(query: LyricsQuery): Lyrics? {
            for (server in LYRICS_PLUS_SERVERS) {
                val url =
                    "$server/v2/lyrics/get".toHttpUrl().newBuilder()
                        .addQueryParameter("title", query.title)
                        .addQueryParameter("artist", query.artist)
                        .apply {
                            if (query.album.isNotBlank()) addQueryParameter("album", query.album)
                            if (query.durationSec > 0) addQueryParameter("duration", query.durationSec.toString())
                        }.build()
                val body = get(url.toString()) ?: continue
                parseLyricsPlus(body, query.artist)?.let { return it }
            }
            return null
        }

        private suspend fun betterLyrics(query: LyricsQuery): Lyrics? {
            val url =
                "https://lyrics-api.boidu.dev/getLyrics".toHttpUrl().newBuilder()
                    .addQueryParameter("s", query.title)
                    .addQueryParameter("a", query.artist)
                    .apply { if (query.durationSec > 0) addQueryParameter("d", query.durationSec.toString()) }
                    .build()
            val ttml = get(url.toString())?.let(::betterLyricsTtml) ?: return null
            // A different version (live, extended) has different timing: skip it.
            val length = ttmlDurationSec(ttml)
            if (query.durationSec > 0 && length != null && !closeEnough(length, query.durationSec)) {
                return null
            }
            return parseTtml(ttml, "BetterLyrics · Apple")
        }

        private suspend fun lrclib(query: LyricsQuery): Lyrics? {
            if (query.durationSec > 0) {
                val exact =
                    "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                        .addQueryParameter("track_name", query.title)
                        .addQueryParameter("artist_name", query.artist)
                        .apply { if (query.album.isNotBlank()) addQueryParameter("album_name", query.album) }
                        .addQueryParameter("duration", query.durationSec.toString())
                        .build()
                get(exact.toString())?.let { body ->
                    val length = lrclibDuration(body)
                    if (length == null || kotlin.math.abs(length - query.durationSec) <= MAX_DURATION_GAP_SEC) {
                        parseLrclib(body)?.let { return it }
                    }
                }
            }
            val search =
                "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                    .addQueryParameter("track_name", query.title)
                    .addQueryParameter("artist_name", query.artist)
                    .build()
            return get(search.toString())?.let { parseLrclibSearch(it, query.durationSec) }
        }

        /** YouTube Music's Lyrics tab: the "next" page names a lyrics page; that page holds the text. */
        private suspend fun youTubeMusic(query: LyricsQuery): Lyrics? {
            val videoId = query.videoId ?: return null
            val next = innerTubeApi.next(NextRequest(InnerTubeContext.default(), videoId = videoId))
            val browseId =
                findAll(next, "browseEndpoint")
                    .firstNotNullOfOrNull { it.nav("browseId")?.safeString()?.takeIf { id -> id.startsWith("MPLYt") } }
                    ?: return null
            val page = innerTubeApi.browse(BrowseRequest.page(browseId)).contents ?: return null
            val text =
                findAll(page, "musicDescriptionShelfRenderer").firstNotNullOfOrNull { shelf ->
                    (shelf.nav("description", "runs") as? JsonArray)
                        ?.mapNotNull { it.nav("text")?.safeString() }
                        ?.joinToString("")
                        ?.takeIf { it.isNotBlank() }
                } ?: return null
            return (com.clibeats.data.lyrics.LrcParser.parse(text) as? Lyrics.Plain)?.copy(source = "YouTube Music")
        }

        private suspend fun lyricsOvh(query: LyricsQuery): Lyrics? {
            val url =
                "https://api.lyrics.ovh/v1/".toHttpUrl().newBuilder()
                    .addPathSegment(query.artist)
                    .addPathSegment(query.title)
                    .build()
            return get(url.toString())?.let(::parseLyricsOvh)
        }

        /** The body of a successful GET; null for "not found"; throws when the source can't be reached. */
        private suspend fun get(url: String): String? =
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                http.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }
            }

        private companion object {
            const val SOURCE_TIMEOUT_MS = 9_000L
            const val YOUTUBE_PREFERENCE = 3
            const val MEMORY_SIZE = 64
            const val NOT_FOUND_RETRY_MS = 3L * 24 * 60 * 60 * 1000
            const val USER_AGENT = "CLIBeats (Android music player; lyrics)"
            val LYRICS_PLUS_SERVERS = listOf("https://lyricsplus.prjktla.my.id", "https://lyricsplus.binimum.org")
        }
    }

/** Syllable sync from Apple Music is the best there is; then other syllable sync, line sync, plain text. */
internal fun rank(lyrics: Lyrics): Int =
    when {
        lyrics is Lyrics.Synced && lyrics.hasWordTiming && "Apple" in lyrics.source.orEmpty() -> RANK_APPLE_SYLLABLE
        lyrics is Lyrics.Synced && lyrics.hasWordTiming -> NON_APPLE_SYLLABLE
        lyrics is Lyrics.Synced -> 2
        else -> 1
    }

internal const val RANK_APPLE_SYLLABLE = 4

/**
 * Non-Apple syllable lyrics (QQ Music, Musixmatch via LyricsPlus) are sometimes another version of the song (an
 * English version, a cover). When another source has lyrics for it, they must share most words; if they barely
 * do, the other source's lyrics (line-synced or plain) are the right ones.
 */
internal fun checkedAgainstOthers(
    best: Lyrics,
    all: List<Lyrics>,
): Lyrics {
    if (rank(best) != NON_APPLE_SYLLABLE) return best
    val others = all.filter { it !== best && rank(it) < NON_APPLE_SYLLABLE }
    if (others.isEmpty()) return best
    val agreeing = others.any { similarity(it, best) >= MIN_SIMILARITY }
    return if (agreeing) best else others.maxBy(::rank)
}

/** Shared words between two lyrics (first lines only), 0 (none) to 1 (the same). */
internal fun similarity(
    a: Lyrics,
    b: Lyrics,
): Double {
    fun words(lyrics: Lyrics): Set<String> {
        val text =
            when (lyrics) {
                is Lyrics.Synced -> lyrics.lines.take(COMPARE_LINES).joinToString(" ") { it.text }
                is Lyrics.Plain -> lyrics.text.lines().take(COMPARE_LINES).joinToString(" ")
            }
        // Words for spaced scripts, single characters for unspaced ones (Japanese, Chinese).
        return text.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).flatMap { word ->
            if (word.any { Character.UnicodeScript.of(it.code) in UNSPACED }) word.map(Char::toString) else listOf(word)
        }.filter { it.isNotBlank() }.toSet()
    }
    val wa = words(a)
    val wb = words(b)
    if (wa.isEmpty() || wb.isEmpty()) return 0.0
    return (wa intersect wb).size.toDouble() / minOf(wa.size, wb.size)
}

private const val NON_APPLE_SYLLABLE = 3
private const val MIN_SIMILARITY = 0.35
private const val COMPARE_LINES = 20
private val UNSPACED =
    setOf(
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.THAI,
    )

/**
 * The search for [track]: its title without video decorations ("(Official Video)", "[4K]"), its first artist
 * (YouTube's "Artist - Topic" channels lose the suffix), album and length.
 */
internal fun queryFor(track: Track): LyricsQuery =
    LyricsQuery(
        title = cleanTitle(track.title),
        artist = mainArtist(track.artist),
        album = track.album,
        durationSec = (track.durationMs / 1000).toInt(),
        videoId = track.id.takeIf { it.isNotBlank() },
    )

internal fun cleanTitle(title: String): String =
    title
        .replace(VIDEO_DECORATION, "")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
        .ifEmpty { title }

internal fun mainArtist(artist: String): String =
    artist
        .removeSuffix(" - Topic")
        .split(Regex("""\s*(?:,|&|\bfeat\.?|\bft\.?|\bx\b|•)\s*""", RegexOption.IGNORE_CASE))
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?: artist

private val VIDEO_DECORATION =
    Regex(
        """\s*[(\[](?:official\s*)?(?:music\s*|lyrics?\s*|audio\s*|visuali[sz]er\s*)?""" +
            """(?:video|audio|visuali[sz]er|mv|m/v|hd|4k|hq|lyrics?)[)\]]""",
        RegexOption.IGNORE_CASE,
    )

/** A small "most recently used" map, safe across threads. */
internal class RecentMap<V>(
    private val capacity: Int,
) {
    private val map =
        object : LinkedHashMap<String, V>(capacity, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean {
                return size > capacity
            }
        }

    @Synchronized
    fun get(key: String): V? = map[key]

    @Synchronized
    fun put(
        key: String,
        value: V,
    ) {
        map[key] = value
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}
