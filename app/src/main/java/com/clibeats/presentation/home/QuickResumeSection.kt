@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "MagicNumber")

package com.clibeats.presentation.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.clibeats.domain.model.Track
import com.clibeats.presentation.component.TrackArtwork
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor

/**
 * Song shelves ("Quick picks" / "Jump back in") showing four songs per column with sideways scroll.
 */
@Composable
internal fun QuickResumeSection(
    row: String,
    songs: List<Track>,
    playingTrackId: String?,
    actions: FeedActions,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columnWidth = if (songs.size > SONGS_PER_COLUMN) maxWidth * COLUMN_WIDTH_FRACTION else maxWidth
        val columns = songs.withIndex().chunked(SONGS_PER_COLUMN)
        LazyRow(
            state = actions.rowState?.invoke(row) ?: rememberLazyListState(),
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

internal fun Modifier.keepSwipesInRow(): Modifier = nestedScroll(KeepHorizontalScroll)

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

private const val SONGS_PER_COLUMN = 4
private const val COLUMN_WIDTH_FRACTION = 0.86f
private const val SONG_ART_DP = 48
private const val PLAYING_ALPHA = 0.10f
