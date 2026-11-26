@file:Suppress(
    "FunctionNaming",
    "ktlint:standard:function-naming",
    "LongParameterList",
    "MagicNumber",
    "MatchingDeclarationName",
)

package com.clibeats.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.clibeats.presentation.component.ARTWORK_THUMB_PX
import com.clibeats.presentation.component.artworkUrlFor
import com.clibeats.presentation.playlist.PlayButton
import com.clibeats.presentation.playlist.PlaylistViewMode
import com.clibeats.presentation.playlist.ViewModeBar
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBorderInactive
import com.clibeats.presentation.theme.CliBeatsDivider
import com.clibeats.presentation.theme.CliBeatsSurfaceVariant
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary

/** One artist or album as the Library shows it, whatever the view. */
internal data class GroupEntry(
    val key: String,
    val title: String,
    /** The album's artist; null for artists. */
    val subtitle: String?,
    val songCount: Int,
    val playCount: Int,
    val artworkUrl: String?,
    /** Artists are round, albums square. */
    val round: Boolean,
    /** Shown when there's no cover: the artist's initial, or ◉ for an album. */
    val glyph: String,
    val onClick: () -> Unit,
    val onPlay: () -> Unit,
)

internal fun ArtistGroup.toEntry(
    onClick: () -> Unit,
    onPlay: () -> Unit,
) = GroupEntry(
    key = name.lowercase(),
    title = name,
    subtitle = null,
    songCount = trackCount,
    playCount = playCount,
    artworkUrl = artworkUrl,
    round = true,
    glyph = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "♪",
    onClick = onClick,
    onPlay = onPlay,
)

internal fun AlbumGroup.toEntry(
    onClick: () -> Unit,
    onPlay: () -> Unit,
) = GroupEntry(
    key = key,
    title = title,
    subtitle = artist,
    songCount = trackCount,
    playCount = playCount,
    artworkUrl = artworkUrl,
    round = false,
    glyph = "◉",
    onClick = onClick,
    onPlay = onPlay,
)

/** "12 songs · 48 plays" (plays left out while there are none). */
internal fun statsLabel(
    songs: Int,
    plays: Int,
): String {
    val songText = if (songs == 1) "1 song" else "$songs songs"
    return when (plays) {
        0 -> songText
        1 -> "$songText · 1 play"
        else -> "$songText · $plays plays"
    }
}

/**
 * Artists or albums as a small / large grid, a list or a details list (the same four views as Playlists),
 * with a sort switch. Each one opens on tap and has a ▶ to play it straight away.
 */
@Composable
internal fun GroupBrowser(
    entries: List<GroupEntry>,
    noun: String,
    viewMode: PlaylistViewMode,
    onViewModeChange: (PlaylistViewMode) -> Unit,
    sort: GroupSort,
    onSortChange: (GroupSort) -> Unit,
    /** Owned by the Library, so the grid is where you left it after opening an artist or album. */
    gridState: LazyGridState,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(viewMode.columns),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
        verticalArrangement = Arrangement.spacedBy(if (viewMode.isGrid) GRID_GAP else 0.dp),
    ) {
        item(key = "toolbar", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                ViewModeBar(
                    label = if (entries.size == 1) "1 $noun" else "${entries.size} ${noun}s",
                    selected = viewMode,
                    onSelect = onViewModeChange,
                )
                SortBar(sort = sort, onSelect = onSortChange)
            }
        }
        items(entries, key = { it.key }) { entry ->
            when (viewMode) {
                PlaylistViewMode.GRID_SMALL -> GroupTile(entry, large = false)
                PlaylistViewMode.GRID_LARGE -> GroupTile(entry, large = true)
                PlaylistViewMode.LIST -> GroupRow(entry, details = false)
                PlaylistViewMode.DETAILS -> GroupRow(entry, details = true)
            }
        }
    }
}

@Composable
private fun SortBar(
    sort: GroupSort,
    onSelect: (GroupSort) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "SORT", style = MaterialTheme.typography.labelSmall, color = CliBeatsTextSecondary)
        GroupSort.entries.forEach { option ->
            val isSelected = option == sort
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) CliBeatsAccent else CliBeatsTextSecondary,
                maxLines = 1,
                modifier =
                    Modifier
                        .border(1.dp, if (isSelected) CliBeatsAccent else CliBeatsDivider)
                        .semantics { selected = isSelected }
                        .clickable(role = Role.RadioButton) { onSelect(option) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun GroupCover(
    entry: GroupEntry,
    modifier: Modifier,
    glyphLarge: Boolean,
) {
    val shape: Shape = if (entry.round) CircleShape else RoundedCornerShape(6.dp)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(CliBeatsSurfaceVariant)
                .border(1.dp, CliBeatsBorderInactive, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (entry.artworkUrl.isNullOrBlank()) {
            Text(
                text = entry.glyph,
                style = with(MaterialTheme.typography) { if (glyphLarge) headlineMedium else titleMedium },
                color = CliBeatsAccent,
            )
        } else {
            AsyncImage(
                model = artworkUrlFor(entry.artworkUrl, ARTWORK_THUMB_PX * 2),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun GroupTile(
    entry: GroupEntry,
    large: Boolean,
) {
    Column(modifier = Modifier.clickable(onClick = entry.onClick)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            GroupCover(entry, Modifier.fillMaxSize(), glyphLarge = true)
            PlayButton(
                title = entry.title,
                size = if (large) PLAY_LARGE else PLAY_SMALL,
                onClick = entry.onPlay,
                // Inset more on a round cover so the button sits inside the circle.
                modifier =
                    Modifier.align(Alignment.BottomEnd).padding(
                        when {
                            entry.round && large -> 12.dp
                            entry.round -> 6.dp
                            large -> 8.dp
                            else -> 5.dp
                        },
                    ),
            )
        }
        Text(
            text = entry.title,
            style = if (large) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            color = CliBeatsTextPrimary,
            maxLines = if (large) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (entry.subtitle != null) {
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = CliBeatsTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = statsLabel(entry.songCount, entry.playCount),
            style = MaterialTheme.typography.labelSmall,
            color = CliBeatsAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GroupRow(
    entry: GroupEntry,
    details: Boolean,
) {
    val coverSize: Dp = if (details) DETAILS_COVER else LIST_COVER
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = entry.onClick)
                .padding(vertical = if (details) 8.dp else 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupCover(entry, Modifier.size(coverSize), glyphLarge = details)
        Spacer(Modifier.width(12.dp))
        // Takes the room left, so a long name is cut with … instead of squeezing the counts.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = if (details) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                color = CliBeatsTextPrimary,
                maxLines = if (details) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.subtitle != null) {
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = CliBeatsTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = statsLabel(entry.songCount, entry.playCount),
                style = MaterialTheme.typography.labelSmall,
                color = if (details) CliBeatsAccent else CliBeatsTextSecondary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        PlayButton(title = entry.title, size = PLAY_ROW, onClick = entry.onPlay)
    }
}

private val GRID_GAP = 10.dp
private val PLAY_SMALL = 30.dp
private val PLAY_LARGE = 38.dp
private val PLAY_ROW = 36.dp
private val LIST_COVER = 44.dp
private val DETAILS_COVER = 72.dp
