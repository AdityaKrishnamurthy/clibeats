@file:Suppress(
    "FunctionNaming",
    "ktlint:standard:function-naming",
    "TooManyFunctions",
    // Download states come from the download manager, like the other song lists.
    "ForbiddenImport",
    "MatchingDeclarationName",
)

package com.clibeats.presentation.playlist

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.clibeats.data.download.DownloadStatus
import com.clibeats.domain.model.Track
import com.clibeats.presentation.component.CliConfirmDialog
import com.clibeats.presentation.component.ReorderableList
import com.clibeats.presentation.component.SongTableRow
import com.clibeats.presentation.component.TrackArtwork
import com.clibeats.presentation.component.TuiTextField
import com.clibeats.presentation.layout.LockTabSwipe
import com.clibeats.presentation.search.formatDuration
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsDestructive
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsSurfaceVariant
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor
import com.clibeats.util.ARTWORK_FULL_PX
import com.clibeats.util.ARTWORK_THUMB_PX
import com.clibeats.util.artworkUrlFor

/** What can be done with the songs picked in a playlist's selection mode. */
class SelectionActions(
    val download: (List<Track>) -> Unit,
    val addToPlaylist: (List<Track>) -> Unit,
    val queue: (List<Track>) -> Unit,
    /** Right after the song playing now, in the order shown. */
    val playNext: (List<Track>) -> Unit,
    val favorite: (List<Track>) -> Unit,
    /** The usual hold menu for one song (shown when exactly one is selected). */
    val more: (Track) -> Unit,
)

/**
 * One playlist's songs.
 * - The search box filters by title, artist or album; a tap still plays the whole list from that song.
 * - The song playing now is highlighted.
 * - Hold a song to start selecting (tap more to add them); a bar offers download, add to playlist, queue,
 *   favorite, remove, and the usual menu for a single song. Back or ✕ ends the selection.
 * - Songs still downloading (Downloaded) are greyed out with their progress, and get full colour when done.
 * - With [playAll], ▶ PLAY and ⤨ SHUFFLE at the top play the whole list (in the order shown) via [onTrackClick].
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
@Composable
fun PlaylistDetailView(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    emptyText: String,
    onBackClick: () -> Unit,
    /** The list as shown (sorted on screen, if a sort was picked for a fixed-order list) and the tapped row. */
    onTrackClick: (List<Track>, Int) -> Unit,
    onRemoveClick: ((Track) -> Unit)?,
    removeLabel: String,
    onSwipeRight: (Track) -> Unit,
    onSwipeLeft: (Track) -> Unit,
    selection: SelectionActions,
    headerAction: Pair<String, () -> Unit>? = null,
    /** Buttons under the header, side by side ([headerAction] alone by default). */
    headerActions: List<Pair<String, () -> Unit>> = listOfNotNull(headerAction),
    coverUrl: String? = null,
    onChangeCoverClick: (() -> Unit)? = null,
    /** Non-null: rows get a ≡ handle to drag them into a new order. */
    onReorder: ((List<Track>) -> Unit)? = null,
    /** Removes several songs at once (one save for a playlist); defaults to [onRemoveClick] for each. */
    onRemoveMany: ((List<Track>) -> Unit)? = onRemoveClick?.let { remove -> { list -> list.forEach(remove) } },
    downloadedIds: Set<String> = emptySet(),
    downloads: Map<String, DownloadStatus> = emptyMap(),
    /** Songs shown greyed out until they finish downloading (the Downloaded playlist's queue). */
    pendingIds: Set<String> = emptySet(),
    playingTrackId: String? = null,
    /** Adds ▶ PLAY and ⤨ SHUFFLE before [headerActions]. */
    playAll: Boolean = false,
) {
    val accent = LocalAccentColor.current
    // A list you can reorder is re-saved in the picked order (and can still be dragged after). Fixed lists
    // (Most Played, M3U files) are only sorted on screen, until you leave them.
    var viewSort by remember(title) { mutableStateOf<SongSort?>(null) }
    val ordered = remember(tracks, viewSort) { viewSort?.let { sortSongs(tracks, it) } ?: tracks }
    var query by rememberSaveable(title) { mutableStateOf("") }
    val filtered = remember(ordered, query) { filterSongs(ordered, query) }
    val searching = query.isNotBlank()
    // After a sort the list starts again from the top (it used to stay on the row that was visible).
    var sortCount by remember(title) { mutableIntStateOf(0) }
    val fixedListState = rememberLazyListState()
    var lastSort by remember(title) { mutableStateOf<SongSort?>(null) }
    val context = LocalContext.current
    var rowView by remember { mutableStateOf(SongRowView.load(context)) }
    val onSort: (SongSort) -> Unit = { sort ->
        lastSort = sort
        sortCount++
        if (onReorder != null) onReorder(sortSongs(tracks, sort)) else viewSort = sort
    }
    LaunchedEffect(sortCount, viewSort, query) { if (sortCount > 0 || searching) fixedListState.scrollToItem(0) }

    var selectedIds by remember(title) { mutableStateOf(emptySet<String>()) }
    // Songs that left the list (removed, deleted) leave the selection too.
    LaunchedEffect(tracks) { selectedIds = selectedIds intersect tracks.mapTo(HashSet()) { it.id } }
    val selecting = selectedIds.isNotEmpty()
    BackHandler(enabled = selecting) { selectedIds = emptySet() }
    // Selecting: a sideways swipe (e.g. over the action bar) must not flip to the next tab.
    LockTabSwipe(selecting)
    val selectedTracks = ordered.filter { it.id in selectedIds }
    var confirmRemove by remember { mutableStateOf(false) }

    fun toggle(track: Track) {
        selectedIds = if (track.id in selectedIds) selectedIds - track.id else selectedIds + track.id
    }

    // A tap plays the whole (sorted) list from that song, also when the search narrowed what's shown.
    fun play(track: Track) {
        val at = ordered.indexOfFirst { it.id == track.id }
        if (at >= 0) onTrackClick(ordered, at)
    }

    if (confirmRemove && onRemoveMany != null) {
        val n = selectedTracks.size
        CliConfirmDialog(
            command = "rm $n ${if (n == 1) "song" else "songs"} from \"$title\"",
            message = "$removeLabel: $n ${if (n == 1) "song" else "songs"}?",
            confirmLabel = "Remove",
            onConfirm = {
                onRemoveMany(selectedTracks)
                selectedIds = emptySet()
            },
            onDismiss = { confirmRemove = false },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(
                count = selectedTracks.size,
                allSelected = filtered.all { it.id in selectedIds },
                onClose = { selectedIds = emptySet() },
                onSelectAll = {
                    val visible = filtered.mapTo(HashSet()) { it.id }
                    val allPicked = visible.all { it in selectedIds }
                    selectedIds = if (allPicked) selectedIds - visible else selectedIds + visible
                },
                onDownload = {
                    selection.download(selectedTracks)
                    selectedIds = emptySet()
                },
                onAddToPlaylist = { selection.addToPlaylist(selectedTracks) },
                onQueue = {
                    selection.queue(selectedTracks)
                    selectedIds = emptySet()
                },
                onPlayNext = {
                    selection.playNext(selectedTracks)
                    selectedIds = emptySet()
                },
                onFavorite = {
                    selection.favorite(selectedTracks)
                    selectedIds = emptySet()
                },
                onRemove = if (onRemoveMany != null) ({ confirmRemove = true }) else null,
                onMore = selectedTracks.singleOrNull()?.let { only -> { selection.more(only) } },
            )
        } else {
            DetailHeader(
                title = title,
                subtitle = subtitle,
                coverUrl = coverUrl,
                onBackClick = onBackClick,
                onChangeCoverClick = onChangeCoverClick,
                onSort = if (tracks.size > 1) onSort else null,
                currentSort = lastSort,
                rowView = rowView,
                onCycleView =
                    if (tracks.isNotEmpty()) {
                        { rowView = rowView.next().also { it.save(context) } }
                    } else {
                        null
                    },
            )
        }
        // Songs still waiting to download (Downloaded) can't play yet, so PLAY / SHUFFLE leave them out.
        val playable = if (playAll) ordered.filter { it.id !in pendingIds || it.id in downloadedIds } else emptyList()
        val actions =
            if (playable.isEmpty()) {
                headerActions
            } else {
                listOf(
                    "[ ▶ PLAY ]" to { onTrackClick(playable, 0) },
                    "[ ⤨ SHUFFLE ]" to { onTrackClick(playable.shuffled(), 0) },
                ) + headerActions
            }
        if (actions.isNotEmpty() && !selecting) {
            // Wraps to a second line when the labels don't fit (e.g. next to SAVE AS APP PLAYLIST).
            FlowRow(modifier = Modifier.padding(horizontal = 4.dp)) {
                actions.forEach { (label, onClick) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        modifier =
                            Modifier
                                .clickable(onClick = onClick)
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            }
        }
        if (tracks.size > 1) PlaylistSearchField(query = query, onQueryChange = { query = it })

        fun statusOf(track: Track): String? =
            when (val status = downloads[track.id]) {
                DownloadStatus.Queued -> "QUEUED"
                is DownloadStatus.Downloading ->
                    if (status.progressPercent > 0) "↓ ${status.progressPercent}%" else "↓ …"
                is DownloadStatus.Failed -> if (track.id in pendingIds) "FAILED" else null
                else -> null
            }

        @Composable
        fun SongRow(
            track: Track,
            art: (@Composable () -> Unit)?,
            onClick: () -> Unit,
            actions: (@Composable () -> Unit)?,
        ) = SongTableRow(
            trackTitle = track.title,
            artist = track.artist,
            duration = formatDuration(track.durationMs),
            isNowPlaying = track.id == playingTrackId,
            isDownloaded = track.id in downloadedIds,
            artworkContent = art,
            large = rowView == SongRowView.LARGE,
            onClick = if (selecting) ({ toggle(track) }) else onClick,
            onSwipeRight = if (selecting) null else ({ onSwipeRight(track) }),
            onSwipeLeft = if (selecting) null else ({ onSwipeLeft(track) }),
            onLongPress = { toggle(track) },
            selected = if (selecting) track.id in selectedIds else null,
            dimmed = track.id in pendingIds && track.id !in downloadedIds,
            status = statusOf(track),
            actions = if (selecting) null else actions,
        )

        when {
            tracks.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(text = emptyText, style = MaterialTheme.typography.bodyMedium, color = CliBeatsTextSecondary)
                }
            filtered.isEmpty() ->
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No songs match “$query”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CliBeatsTextSecondary,
                    )
                }
            rowView.isGrid ->
                SongGrid(
                    tracks = filtered,
                    columns = rowView.columns,
                    playingTrackId = playingTrackId,
                    selectedIds = if (selecting) selectedIds else null,
                    onClick = { track -> if (selecting) toggle(track) else play(track) },
                    onLongPress = { track -> toggle(track) },
                )
            // Dragging only makes sense on the whole list, not a filtered part of it.
            onReorder != null && !searching ->
                ReorderableList(
                    items = ordered,
                    itemId = { it.id },
                    onReorder = { _, _, reordered -> onReorder(reordered) },
                    scrollToTopRequest = sortCount,
                ) { track, _ ->
                    SongRow(
                        track = track,
                        // Hold the cover (or drag ≡) to move the song; a quick tap on the cover still plays it.
                        art = {
                            TrackArtwork(
                                artworkUrl = track.artworkUrl,
                                size = rowView.artSize,
                                modifier =
                                    if (selecting) Modifier else Modifier.artworkHandle { play(track) },
                            )
                        },
                        onClick = { if (!isAnyDragging) play(track) },
                        actions = { DragHandle() },
                    )
                }
            else ->
                LazyColumn(
                    state = fixedListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Top,
                ) {
                    itemsIndexed(items = filtered, key = { index, track -> "${track.id}_$index" }) { _, track ->
                        SongRow(
                            track = track,
                            art = { TrackArtwork(artworkUrl = track.artworkUrl, size = rowView.artSize) },
                            onClick = { play(track) },
                            actions = null,
                        )
                    }
                }
        }
    }
}

/** Songs whose title, artist or album contains every word of [query] (case and accents ignored). */
internal fun filterSongs(
    tracks: List<Track>,
    query: String,
): List<Track> {
    val words = fold(query).split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return tracks
    return tracks.filter { track ->
        val haystack = fold("${track.title} ${track.artist} ${track.album}")
        words.all { it in haystack }
    }
}

private fun fold(text: String): String =
    java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")

private val COMBINING_MARKS = Regex("\\p{Mn}+")

@Composable
private fun PlaylistSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    TuiTextField(
        value = query,
        onValueChange = onQueryChange,
        label = "Find",
        placeholder = "Search this playlist",
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
            KeyboardActions(onSearch = {
                keyboard?.hide()
                focusManager.clearFocus()
            }),
        trailingContent = {
            if (query.isNotEmpty()) {
                IconButton(onClick = {
                    onQueryChange("")
                    focusManager.clearFocus()
                }) {
                    Icon(Icons.Outlined.Clear, contentDescription = "Clear search", tint = CliBeatsTextSecondary)
                }
            }
        },
    )
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun DetailHeader(
    title: String,
    subtitle: String,
    coverUrl: String?,
    onBackClick: () -> Unit,
    onChangeCoverClick: (() -> Unit)?,
    onSort: ((SongSort) -> Unit)?,
    currentSort: SongSort? = null,
    rowView: SongRowView = SongRowView.DETAILS,
    onCycleView: (() -> Unit)? = null,
) {
    val accent = LocalAccentColor.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(CliBeatsSurface)
                .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back to playlists",
                tint = CliBeatsTextPrimary,
            )
        }
        if (onChangeCoverClick != null) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CliBeatsSurfaceVariant)
                        .border(1.dp, accent, RoundedCornerShape(4.dp))
                        .clickable(onClick = onChangeCoverClick),
                contentAlignment = Alignment.Center,
            ) {
                if (coverUrl.isNullOrBlank()) {
                    Text(text = "♫", style = MaterialTheme.typography.titleMedium, color = accent)
                } else {
                    AsyncImage(
                        model = artworkUrlFor(coverUrl, ARTWORK_THUMB_PX * 2),
                        contentDescription = "Change playlist cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = CliBeatsTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = CliBeatsTextSecondary)
        }
        onCycleView?.let { cycle ->
            // One icon: each tap switches to the next row style (compact → list → large); remembered.
            IconButton(onClick = cycle) {
                Icon(
                    imageVector = rowView.icon,
                    contentDescription = "Row style: ${rowView.label}. Tap for ${rowView.next().label}",
                    tint = accent,
                )
            }
        }
        onSort?.let { SortButton(current = currentSort, onSort = it) }
        if (onChangeCoverClick != null) {
            IconButton(onClick = onChangeCoverClick) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit playlist name and cover",
                    tint = accent,
                )
            }
        }
    }
}

/** The bar shown while songs are selected: count, select all, and what to do with them. */
@Suppress("LongParameterList", "LongMethod")
@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onFavorite: () -> Unit,
    onRemove: (() -> Unit)?,
    onMore: (() -> Unit)?,
) {
    val accent = LocalAccentColor.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(CliBeatsSurface)
                .border(1.dp, accent)
                .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Clear, contentDescription = "End selection", tint = CliBeatsTextPrimary)
            }
            Text(
                text = "$count selected",
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            BarAction(if (allSelected) "[ NONE ]" else "[ ALL ]", onClick = onSelectAll)
        }
        // Icons only, so every action fits on one line; hold an icon to see what it does.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BarIcon(Icons.AutoMirrored.Outlined.PlaylistPlay, "Play next", onClick = onPlayNext)
            BarIcon(Icons.AutoMirrored.Outlined.QueueMusic, "Add to queue", onClick = onQueue)
            BarIcon(Icons.Outlined.Download, "Download", onClick = onDownload)
            BarIcon(Icons.AutoMirrored.Outlined.PlaylistAdd, "Add to playlist", onClick = onAddToPlaylist)
            BarIcon(Icons.Outlined.FavoriteBorder, "Add to favorites", onClick = onFavorite)
            onRemove?.let { BarIcon(Icons.Outlined.Delete, "Remove", tint = CliBeatsDestructive, onClick = it) }
            onMore?.let { BarIcon(Icons.Outlined.MoreVert, "More options", onClick = it) }
        }
    }
}

/** One selection action: an icon, with its name as a short toast when held. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarIcon(
    icon: ImageVector,
    label: String,
    tint: Color = LocalAccentColor.current,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .combinedClickable(
                    onClickLabel = label,
                    role = Role.Button,
                    onLongClick = { Toast.makeText(context, label, Toast.LENGTH_SHORT).show() },
                    onClick = onClick,
                ),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint)
    }
}

@Composable
private fun BarAction(
    text: String,
    description: String? = null,
    color: Color = LocalAccentColor.current,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier =
            Modifier
                .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 10.dp),
    )
}

/** How songs show inside a playlist: details, bigger details, grid, bigger grid. Remembered across playlists. */
@Suppress("MagicNumber")
internal enum class SongRowView(
    val label: String,
    /** 0 for the row styles, otherwise how many covers across. */
    val columns: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    DETAILS("details", 0, Icons.AutoMirrored.Outlined.ViewList),
    LARGE("bigger details", 0, Icons.Outlined.ViewAgenda),
    GRID("grid", 3, Icons.Outlined.Apps),
    LARGE_GRID("bigger grid", 2, Icons.Outlined.GridView),
    ;

    val isGrid: Boolean get() = columns > 0
    val artSize: Int get() = if (this == LARGE) 48 else 32

    fun next(): SongRowView = entries[(ordinal + 1) % entries.size]

    fun save(context: android.content.Context) {
        prefs(context).edit().putString(KEY, name).apply()
    }

    companion object {
        private const val KEY = "playlist_song_view"

        private fun prefs(context: android.content.Context) =
            context.getSharedPreferences("clibeats_ui", android.content.Context.MODE_PRIVATE)

        fun load(context: android.content.Context): SongRowView =
            prefs(context).getString(KEY, null)?.let { saved -> entries.firstOrNull { it.name == saved } } ?: DETAILS
    }
}

/** Songs as covers in [columns] columns (title and artist under each); tap plays, hold selects. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Suppress("LongParameterList")
@Composable
private fun SongGrid(
    tracks: List<Track>,
    columns: Int,
    playingTrackId: String?,
    selectedIds: Set<String>?,
    onClick: (Track) -> Unit,
    onLongPress: (Track) -> Unit,
) {
    val accent = LocalAccentColor.current
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(tracks.size, key = { "${tracks[it].id}_$it" }) { index ->
            val track = tracks[index]
            val playing = track.id == playingTrackId
            val selected = selectedIds?.contains(track.id) == true
            Column(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (selected) accent.copy(alpha = SELECTED_TILE_ALPHA) else CliBeatsBackground,
                        )
                        .combinedClickable(onClick = { onClick(track) }, onLongClick = { onLongPress(track) })
                        .padding(4.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(CliBeatsSurfaceVariant)
                            .then(if (playing) Modifier.border(2.dp, accent, RoundedCornerShape(6.dp)) else Modifier),
                ) {
                    AsyncImage(
                        model = artworkUrlFor(track.artworkUrl, ARTWORK_FULL_PX),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    text = (if (playing) "▶ " else "") + track.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (playing) accent else CliBeatsTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = CliBeatsTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val SELECTED_TILE_ALPHA = 0.18f
