@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "TooManyFunctions", "MatchingDeclarationName")

package com.clibeats.presentation.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.clibeats.domain.model.CollectionKind
import com.clibeats.domain.model.HomeItem
import com.clibeats.domain.model.HomeMood
import com.clibeats.domain.model.HomeShelf
import com.clibeats.domain.model.Track
import com.clibeats.presentation.component.AddToPlaylistDialog
import com.clibeats.presentation.component.TrackActionsViewModel
import com.clibeats.presentation.component.TrackArtwork
import com.clibeats.presentation.component.artworkUrlFor
import com.clibeats.presentation.playlist.PlaylistDetailView
import com.clibeats.presentation.playlist.SelectionActions
import com.clibeats.presentation.theme.CliBeatsBorderInactive
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor

/** What Home's feed rows do when tapped or held. */
internal class FeedActions(
    /** A song in a row: the row's name, its songs and the tapped one. */
    val playSong: (String, List<Track>, Int) -> Unit,
    val songMenu: (Track) -> Unit,
    val open: (HomeItem.Collection) -> Unit,
    val selectMood: (HomeMood) -> Unit,
)

/**
 * YouTube Music's home page: mood chips, then each row of the feed. Song rows ("Quick picks") show four
 * songs per column and page sideways; playlist and album rows are cards that open the list.
 */
@Suppress("LongParameterList")
internal fun LazyListScope.homeFeed(
    state: FeedState,
    moods: List<HomeMood>,
    selectedMood: HomeMood?,
    playingTrackId: String?,
    actions: FeedActions,
    /** Shown when the page has no rows at all (an empty local library). */
    emptyText: String = "Nothing to show yet.",
) {
    if (moods.isNotEmpty()) {
        item(key = "moods") { MoodChips(moods, selectedMood, actions.selectMood) }
    }
    when (state) {
        FeedState.Loading -> item(key = "feed_loading") { FeedMessage("Loading your home page…", busy = true) }
        FeedState.MoodFailed ->
            item(key = "feed_mood_failed") {
                val mood = selectedMood?.title ?: "this mood"
                FeedMessage("Couldn't load $mood. Check the connection or pick another.")
            }
        is FeedState.Loaded -> {
            if (state.feed.shelves.isEmpty()) item(key = "feed_empty") { FeedMessage(emptyText) }
            state.feed.shelves.forEachIndexed { index, shelf ->
                item(key = "shelf_${index}_${shelf.title}") {
                    ShelfTitle(shelf)
                    if (shelf.items.all { it is HomeItem.Song }) {
                        SongShelf(shelf.title, shelf.songs, playingTrackId, actions)
                    } else {
                        CardShelf(shelf, playingTrackId, actions)
                    }
                    Spacer(modifier = Modifier.height(SHELF_GAP))
                }
            }
        }
    }
}

@Composable
private fun MoodChips(
    moods: List<HomeMood>,
    selected: HomeMood?,
    onSelect: (HomeMood) -> Unit,
) {
    val accent = LocalAccentColor.current
    LazyRow(
        modifier = Modifier.fillMaxWidth().keepSwipesInRow(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        items(moods, key = { it.title }) { mood ->
            val on = mood == selected
            Text(
                text = mood.title,
                style = MaterialTheme.typography.labelMedium,
                color = if (on) MaterialTheme.colorScheme.background else accent,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (on) accent else CliBeatsSurface)
                        .border(1.dp, if (on) accent else CliBeatsBorderInactive, RoundedCornerShape(4.dp))
                        .clickable { onSelect(mood) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ShelfTitle(shelf: HomeShelf) {
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)) {
        if (shelf.strapline.isNotBlank()) {
            Text(
                text = shelf.strapline.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = CliBeatsTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = shelf.title,
            style = MaterialTheme.typography.titleMedium,
            color = LocalAccentColor.current,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Songs four to a column; the next column peeks in at the edge so it's clear the row scrolls. */
@Composable
private fun SongShelf(
    row: String,
    songs: List<Track>,
    playingTrackId: String?,
    actions: FeedActions,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columnWidth = if (songs.size > SONGS_PER_COLUMN) maxWidth * COLUMN_WIDTH_FRACTION else maxWidth
        val columns = songs.withIndex().chunked(SONGS_PER_COLUMN)
        LazyRow(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxWidth().keepSwipesInRow(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(columns.size) { column ->
                Column(modifier = Modifier.width(columnWidth)) {
                    columns[column].forEach { (index, track) ->
                        FeedSongRow(
                            track = track,
                            playing = track.id == playingTrackId,
                            onClick = { actions.playSong(row, songs, index) },
                            onLongClick = { actions.songMenu(track) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedSongRow(
    track: Track,
    playing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val accent = LocalAccentColor.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (playing) accent.copy(alpha = PLAYING_ALPHA) else Color.Transparent)
                .combinedClickable(
                    onClickLabel = "Play",
                    onLongClickLabel = "Song options",
                    onClick = onClick,
                    onLongClick = onLongClick,
                ).padding(vertical = 4.dp, horizontal = 2.dp),
    ) {
        TrackArtwork(artworkUrl = track.artworkUrl, size = SONG_ART_DP)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (playing) "▶ ${track.title}" else track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (playing) accent else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = CliBeatsTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Playlist, album and artist cards (and the odd song card) in one sideways row. */
@Composable
private fun CardShelf(
    shelf: HomeShelf,
    playingTrackId: String?,
    actions: FeedActions,
) {
    val songs = shelf.songs
    LazyRow(
        modifier = Modifier.fillMaxWidth().keepSwipesInRow(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(shelf.items.size) { i ->
            when (val item = shelf.items[i]) {
                is HomeItem.Collection ->
                    FeedCard(
                        title = item.title,
                        subtitle = item.subtitle,
                        artworkUrl = item.artworkUrl,
                        round = item.kind == CollectionKind.ARTIST,
                        playing = false,
                        onClick = { actions.open(item) },
                        onLongClick = null,
                    )
                is HomeItem.Song ->
                    FeedCard(
                        title = item.track.title,
                        subtitle = item.track.artist,
                        artworkUrl = item.track.artworkUrl,
                        round = false,
                        playing = item.track.id == playingTrackId,
                        onClick = { actions.playSong(shelf.title, songs, songs.indexOf(item.track)) },
                        onLongClick = { actions.songMenu(item.track) },
                    )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongParameterList")
@Composable
private fun FeedCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    round: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val accent = LocalAccentColor.current
    val shape = if (round) CircleShape else RoundedCornerShape(6.dp)
    Column(
        modifier =
            Modifier
                .width(CARD_DP)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier =
                Modifier
                    .size(CARD_DP)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(if (playing) 2.dp else 1.dp, if (playing) accent else CliBeatsBorderInactive, shape),
        ) {
            AsyncImage(
                model = artworkUrlFor(artworkUrl, CARD_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(CARD_DP),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (playing) "▶ $title" else title,
            style = MaterialTheme.typography.labelLarge,
            color = if (playing) accent else MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = CliBeatsTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FeedMessage(
    text: String,
    busy: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(
                color = LocalAccentColor.current,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = CliBeatsTextSecondary)
    }
}

/** Local Home's two big buttons: the whole library shuffled, and a random album from start to end. */
@Composable
internal fun LocalQuickActions(
    songCount: Int,
    albumCount: Int,
    onShuffleAll: () -> Unit,
    onSurpriseAlbum: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickAction("\u21c4 SHUFFLE ALL", "$songCount songs", enabled = songCount > 0, onClick = onShuffleAll)
        QuickAction("\u2726 SURPRISE ALBUM", "$albumCount albums", enabled = albumCount > 0, onClick = onSurpriseAlbum)
    }
}

@Composable
private fun RowScope.QuickAction(
    label: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val accent = LocalAccentColor.current
    Column(
        modifier =
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(CliBeatsSurface)
                .border(1.dp, if (enabled) accent else CliBeatsBorderInactive, RoundedCornerShape(6.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) accent else CliBeatsTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = detail, style = MaterialTheme.typography.labelSmall, color = CliBeatsTextSecondary, maxLines = 1)
    }
}

/**
 * A row's sideways swipe stays in the row: at either end it stops instead of flipping to the next tab.
 * (Swipes that start outside the rows still switch tabs.)
 */
private fun Modifier.keepSwipesInRow(): Modifier = nestedScroll(KeepHorizontalScroll)

private object KeepHorizontalScroll : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = Offset(available.x, 0f)

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity = Velocity(available.x, 0f)
}

/**
 * A playlist, album or artist opened from a Home card, in the same view as your own playlists (find box,
 * select several songs, the playing song highlighted), with Play, Shuffle and Save as playlist.
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun OpenedCollectionView(
    opened: OpenedCollection,
    homeViewModel: HomeViewModel,
    trackActions: TrackActionsViewModel,
    playingTrackId: String?,
    downloadedIds: Set<String>,
    onSongMenu: (Track) -> Unit,
) {
    BackHandler(onBack = homeViewModel::closeCollection)
    val context = LocalContext.current
    var bulkAddTracks by remember { mutableStateOf<List<Track>?>(null) }
    bulkAddTracks?.let { AddToPlaylistDialog(tracks = it, onDismiss = { bulkAddTracks = null }) }
    val selection =
        remember(trackActions) {
            SelectionActions(
                download = trackActions::downloadAll,
                addToPlaylist = { bulkAddTracks = it },
                queue = trackActions::addAllToQueue,
                playNext = trackActions::playAllNext,
                favorite = trackActions::favoriteAll,
                more = onSongMenu,
            )
        }
    val tracks = opened.tracks.orEmpty()
    val kind =
        when (opened.item.kind) {
            CollectionKind.PLAYLIST -> "Playlist"
            CollectionKind.ALBUM -> "Album"
            CollectionKind.ARTIST -> "Artist"
        }
    val subtitle =
        when {
            opened.tracks == null && !opened.failed -> "$kind · loading…"
            else -> "$kind · ${tracks.size} songs"
        }
    PlaylistDetailView(
        title = opened.title,
        subtitle = subtitle,
        tracks = tracks,
        emptyText =
            when {
                opened.failed -> "Couldn't load this ${kind.lowercase()}. Check the connection and open it again."
                opened.tracks == null -> "Loading songs…"
                else -> "No songs here."
            },
        onBackClick = homeViewModel::closeCollection,
        onTrackClick = { list, index -> homeViewModel.playCollection(list, index, opened.title) },
        onRemoveClick = null,
        removeLabel = "",
        onSwipeRight = trackActions::addToQueue,
        onSwipeLeft = trackActions::favorite,
        selection = selection,
        headerActions =
            if (tracks.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    "[ ▶ PLAY ]" to { homeViewModel.playCollection(tracks, 0, opened.title) },
                    "[ ⤨ SHUFFLE ]" to { homeViewModel.playCollection(tracks.shuffled(), 0, opened.title) },
                    "[ + SAVE ]" to {
                        homeViewModel.saveCollection(opened.title, tracks)
                        val note = "Saved “${opened.title}” to your playlists"
                        Toast.makeText(context, note, Toast.LENGTH_SHORT).show()
                    },
                )
            },
        coverUrl = opened.item.artworkUrl,
        downloadedIds = downloadedIds,
        playingTrackId = playingTrackId,
    )
}

private const val SONGS_PER_COLUMN = 4
private const val COLUMN_WIDTH_FRACTION = 0.86f
private const val SONG_ART_DP = 48
private const val PLAYING_ALPHA = 0.10f
private val CARD_DP: Dp = 144.dp
private const val CARD_ART_PX = 480
private val SHELF_GAP: Dp = 12.dp
