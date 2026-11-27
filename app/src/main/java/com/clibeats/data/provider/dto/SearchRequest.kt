@file:Suppress("ForbiddenImport")

package com.clibeats.data.provider.dto

import com.clibeats.data.provider.youtube.YouTubeClientConfig
import kotlinx.serialization.Serializable

@Serializable
data class SearchRequest(
    val context: InnerTubeContext,
    val query: String,
    val params: String? = null,
) {
    companion object {
        // Base64-encoded protobuf param that filters to "songs" type
        private const val SONGS_FILTER_PARAM = "EgWKAQIIAWoMEA4QChADEAQQCRAF"

        // Same, filtering to music videos. Unlike an unfiltered search, these rows include the length.
        private const val VIDEOS_FILTER_PARAM = "EgWKAQIQAWoMEA4QChADEAQQCRAF"

        // Same, filtering to albums, singles and EPs.
        private const val ALBUMS_FILTER_PARAM = "EgWKAQIYAWoMEA4QChADEAQQCRAF"

        // Same, filtering to playlists (featured and community ones).
        private const val PLAYLISTS_FILTER_PARAM = "EgWKAQIoAWoMEA4QChADEAQQCRAF"

        // Same, filtering to artists.
        private const val ARTISTS_FILTER_PARAM = "EgWKAQIgAWoMEA4QChADEAQQCRAF"

        fun forArtists(query: String): SearchRequest = forQuery(query).copy(params = ARTISTS_FILTER_PARAM)

        fun forAlbums(query: String): SearchRequest = forQuery(query).copy(params = ALBUMS_FILTER_PARAM)

        fun forPlaylists(query: String): SearchRequest = forQuery(query).copy(params = PLAYLISTS_FILTER_PARAM)

        fun forQuery(
            query: String,
            filterSongs: Boolean = true,
        ): SearchRequest =
            SearchRequest(
                context =
                    InnerTubeContext.forClient(
                        YouTubeClientConfig(
                            name = "WEB_REMIX",
                            clientName = "WEB_REMIX",
                            clientVersion = "1.20240618.01.00",
                            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        ),
                    ),
                query = query,
                // false = music videos (songs that only exist as a video, e.g. a label's lyrical upload).
                params = if (filterSongs) SONGS_FILTER_PARAM else VIDEOS_FILTER_PARAM,
            )
    }
}
