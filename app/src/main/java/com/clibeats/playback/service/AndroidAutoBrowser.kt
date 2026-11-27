@file:Suppress("ForbiddenImport", "MaxLineLength", "ReturnCount", "TooManyFunctions")

package com.clibeats.playback.service

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import com.clibeats.data.local.dao.SongDao
import com.clibeats.data.local.mapper.toDomain
import com.clibeats.domain.model.Playlist
import com.clibeats.domain.model.Track
import com.clibeats.domain.repository.HistoryRepository
import com.clibeats.domain.repository.LibraryRepository
import com.clibeats.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Auto / Android Automotive browse tree.
 *
 * Android Auto shows at most four root tabs, so the tree is intentionally shallow:
 * HOME (recently played), PLAYLISTS (Liked Songs + user playlists), LIBRARY (downloads and
 * on-device music) and QUEUE (what is playing next).
 *
 * The tree only describes content. Playback still goes through [PlaybackService], which resolves
 * the returned media ids back to [Track]s and starts them through the normal queue pipeline.
 */
@OptIn(UnstableApi::class)
@Singleton
class AndroidAutoBrowser
    @Inject
    constructor(
        private val libraryRepository: LibraryRepository,
        private val playlistRepository: PlaylistRepository,
        private val historyRepository: HistoryRepository,
        private val songDao: SongDao,
    ) {
        suspend fun libraryRoot(recent: Boolean): MediaItem {
            val id = if (recent) RECENT_ROOT else ROOT
            return container(
                id = id,
                title = if (recent) "Recently played" else "CLIBeats",
            )
        }

        suspend fun children(
            parentId: String,
            queue: List<MediaItem>,
            currentIndex: Int,
        ): List<MediaItem> =
            when {
                parentId == ROOT -> rootTabs()
                parentId == HOME -> recentTracks().map(::trackItem)
                parentId == PLAYLISTS -> playlistChildren()
                parentId == LIKED_SONGS -> likedTracks().map(::trackItem)
                parentId.startsWith(PLAYLIST_PREFIX) ->
                    playlistTracks(parentId.removePrefix(PLAYLIST_PREFIX)).map(::trackItem)
                parentId == LIBRARY -> libraryTracks().map(::trackItem)
                parentId == QUEUE -> queue.takeIf { it.isNotEmpty() } ?: recentQueue(queue, currentIndex)
                else -> emptyList()
            }

        /** Finds a browsed item by media id (Android Auto asks for one item before playing it). */
        suspend fun item(mediaId: String): MediaItem? {
            if (mediaId in rootTabIds) return rootTabs().firstOrNull { it.mediaId == mediaId }
            if (mediaId == LIKED_SONGS) return container(LIKED_SONGS, "Liked Songs")
            if (mediaId.startsWith(PLAYLIST_PREFIX)) {
                val id = mediaId.removePrefix(PLAYLIST_PREFIX)
                return playlists().firstOrNull { it.id == id }?.let(::playlistItem)
            }
            return findTracks(listOf(mediaId)).firstOrNull()?.let(::trackItem)
        }

        /** Resolves the media ids of a browsed selection, preserving the requested order. */
        suspend fun resolveTracks(mediaIds: List<String>): List<Track> = findTracks(mediaIds)

        /** Voice search over on-device, downloaded, liked, recent and playlist songs. */
        suspend fun search(
            query: String,
            limit: Int = SEARCH_LIMIT,
        ): List<MediaItem> {
            val needle = query.trim()
            if (needle.length < 2) return emptyList()
            val all =
                buildList {
                    addAll(libraryTracks())
                    addAll(likedTracks())
                    addAll(recentTracks())
                    playlistRepository.getAllPlaylistsAsFlow().first().forEach { playlist ->
                        addAll(playlistRepository.getSongsForPlaylistAsFlow(playlist.id).first())
                    }
                }
            return all.asSequence()
                .distinctBy { it.id }
                .filter { it.title.contains(needle, ignoreCase = true) || it.artist.contains(needle, ignoreCase = true) }
                .take(limit)
                .map(::trackItem)
                .toList()
        }

        private suspend fun rootTabs(): List<MediaItem> =
            listOf(
                container(HOME, "Recently Played"),
                container(PLAYLISTS, "Playlists"),
                container(LIBRARY, "Library"),
                container(QUEUE, "Queue"),
            )

        private suspend fun playlistChildren(): List<MediaItem> {
            val liked = container(LIKED_SONGS, "Liked Songs")
            return listOf(liked) + playlists().map(::playlistItem)
        }

        private suspend fun playlists(): List<Playlist> = playlistRepository.getAllPlaylistsAsFlow().first()

        private suspend fun recentTracks(): List<Track> =
            historyRepository
                .getRecentlyPlayedTracks(RECENT_LIMIT)
                .first()

        private suspend fun likedTracks(): List<Track> = libraryRepository.getLikedSongs().first()

        private suspend fun libraryTracks(): List<Track> {
            val downloaded = songDao.getDownloadedSongsAsFlow().first()
            val local = songDao.getLocalAsFlow().first()
            return (downloaded + local).distinctBy { it.id }.map { it.toAutoTrack() }
        }

        private suspend fun playlistTracks(playlistId: String): List<Track> =
            playlistRepository.getSongsForPlaylistAsFlow(playlistId).first()

        private suspend fun findTracks(ids: List<String>): List<Track> {
            if (ids.isEmpty()) return emptyList()
            val known = mutableMapOf<String, Track>()
            (libraryTracks() + likedTracks() + recentTracks()).forEach { known[it.id] = it }
            playlists().forEach { playlist ->
                playlistTracks(playlist.id).forEach { known[it.id] = it }
            }
            return ids.mapNotNull(known::get)
        }

        private fun recentQueue(
            queue: List<MediaItem>,
            currentIndex: Int,
        ): List<MediaItem> = listOfNotNull(queue.getOrNull(currentIndex) ?: queue.firstOrNull())

        private fun playlistItem(playlist: Playlist): MediaItem =
            container(
                id = PLAYLIST_PREFIX + playlist.id,
                title = playlist.name,
                subtitle = "${playlist.trackCount} songs",
                artworkUrl = playlist.artworkUrl,
            )

        private fun container(
            id: String,
            title: String,
            subtitle: String? = null,
            artworkUrl: String? = null,
        ): MediaItem {
            val metadata =
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setExtras(contentStyle())
                    .apply {
                        subtitle?.let(::setArtist)
                        artworkUrl?.let { setArtworkUri(android.net.Uri.parse(it)) }
                    }.build()
            return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build()
        }

        private fun trackItem(track: Track): MediaItem =
            MediaItem.Builder()
                .setMediaId(track.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .apply {
                            // The car fetches covers itself: only web links work (not on-phone files).
                            com.clibeats.util.artworkUrlFor(track.artworkUrl, com.clibeats.util.ARTWORK_THUMB_PX * 2)
                                ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                                ?.let { setArtworkUri(android.net.Uri.parse(it)) }
                        }
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setExtras(contentStyle())
                        .build(),
                ).build()

        private fun contentStyle(): Bundle =
            Bundle().apply {
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                )
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                )
            }

        /** Media items from [SongEntity] are enough for the player; the service rebuilds full items. */
        private fun com.clibeats.data.local.entity.SongEntity.toAutoTrack(): Track = toDomain()

        companion object {
            const val ROOT = "clibeats_root"
            const val RECENT_ROOT = "clibeats_recent"
            const val HOME = "$ROOT/home"
            const val PLAYLISTS = "$ROOT/playlists"
            const val LIBRARY = "$ROOT/library"
            const val QUEUE = "$ROOT/queue"
            const val LIKED_SONGS = "$PLAYLISTS/liked"
            const val PLAYLIST_PREFIX = "$PLAYLISTS/"
            const val SEARCH_LIMIT = 25
            private const val RECENT_LIMIT = 20
            private val rootTabIds = setOf(HOME, PLAYLISTS, LIBRARY, QUEUE)
        }
    }
