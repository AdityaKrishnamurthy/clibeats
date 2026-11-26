@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "MagicNumber")

package com.clibeats.presentation.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.clibeats.domain.model.CollectionKind
import com.clibeats.domain.model.HomeItem
import com.clibeats.domain.model.HomeShelf
import com.clibeats.domain.model.Track
import com.clibeats.presentation.component.AddToPlaylistDialog
import com.clibeats.presentation.component.TrackActionsViewModel
import com.clibeats.presentation.playlist.PlaylistDetailView
import com.clibeats.presentation.playlist.SelectionActions
import com.clibeats.presentation.theme.CliBeatsBorderInactive
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor
import com.clibeats.util.artworkUrlFor

@Composable
internal fun ShelfTitle(shelf: HomeShelf) {
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

@Composable
internal fun ChartsSection(
    shelf: HomeShelf,
    playingTrackId: String?,
    actions: FeedActions,
) {
    val songs = shelf.songs
    LazyRow(
        state = actions.rowState?.invoke(shelf.title) ?: rememberLazyListState(),
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
internal fun FeedCard(
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
internal fun FeedMessage(
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

@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun OpenedCollectionView(
    opened: OpenedCollection,
    onBack: () -> Unit,
    onPlay: (tracks: List<Track>, index: Int, source: String) -> Unit,
    onSave: (name: String, tracks: List<Track>) -> Unit,
    trackActions: TrackActionsViewModel,
    playingTrackId: String?,
    downloadedIds: Set<String>,
    onSongMenu: (Track) -> Unit,
) {
    BackHandler(onBack = onBack)
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
            opened.progress != null -> "$kind · ${tracks.size} songs · ${opened.progress}"
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
                opened.progress != null -> "Finding the chart's songs on YouTube Music…"
                opened.tracks == null -> "Loading songs…"
                else -> "No songs here."
            },
        onBackClick = onBack,
        onTrackClick = { list, index -> onPlay(list, index, opened.title) },
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
                    "[ ▶ PLAY ]" to { onPlay(tracks, 0, opened.title) },
                    "[ ⤨ SHUFFLE ]" to { onPlay(tracks.shuffled(), 0, opened.title) },
                    "[ + SAVE ]" to {
                        onSave(opened.title, tracks)
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

private val CARD_DP: Dp = 144.dp
private const val CARD_ART_PX = 480
