@file:Suppress(
    "ktlint:standard:function-naming",
    "FunctionNaming",
    "TooManyFunctions",
    "MatchingDeclarationName",
    // HomeLanguage is a plain catalog of languages, like the view model uses.
    "ForbiddenImport",
)

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.clibeats.data.home.HomeLanguage
import com.clibeats.data.home.LocalSetup
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
    /** Opens the language picker. */
    val editLanguages: () -> Unit = {},
    /** "Not now" on the language suggestion: keep every language. */
    val skipLanguages: () -> Unit = {},
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
    /** Shown when the page has no rows at all; null shows nothing (the caller shows its own). */
    emptyText: String? = "Nothing to show yet.",
    /** Online only: the picked languages ("Hindi, Kannada"; blank = all), or null to hide the chip. */
    languagesLabel: String? = null,
    /** Suggest picking languages (until it's answered once). */
    suggestLanguages: Boolean = false,
) {
    if (suggestLanguages) item(key = "languages_hint") { LanguageHint(actions) }
    if (moods.isNotEmpty() || languagesLabel != null) {
        item(key = "moods") { MoodChips(moods, selectedMood, actions, languagesLabel) }
    }
    when (state) {
        FeedState.Loading -> item(key = "feed_loading") { FeedMessage("Loading your home page…", busy = true) }
        FeedState.MoodFailed ->
            item(key = "feed_mood_failed") {
                val mood = selectedMood?.title ?: "this mood"
                FeedMessage("Couldn't load $mood. Check the connection or pick another.")
            }
        is FeedState.Loaded -> {
            if (state.feed.shelves.isEmpty() && emptyText != null) item(key = "feed_empty") { FeedMessage(emptyText) }
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
    actions: FeedActions,
    languagesLabel: String?,
) {
    val accent = LocalAccentColor.current
    val onSelect = actions.selectMood
    LazyRow(
        modifier = Modifier.fillMaxWidth().keepSwipesInRow(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        if (languagesLabel != null) {
            item(key = "languages") {
                Text(
                    text = "\u270e " + languagesLabel.ifBlank { "All languages" },
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .widthIn(max = LANGUAGE_CHIP_MAX)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, accent, RoundedCornerShape(4.dp))
                            .clickable(onClickLabel = "Choose languages", onClick = actions.editLanguages)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
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

/** Until the language picker was answered once: a short suggestion to tune Home to your languages. */
@Composable
private fun LanguageHint(actions: FeedActions) {
    val accent = LocalAccentColor.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .background(CliBeatsSurface)
                .border(1.dp, accent, RoundedCornerShape(6.dp))
                .padding(12.dp),
    ) {
        Text("> home --languages", style = MaterialTheme.typography.labelSmall, color = accent)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text =
                "Pick the languages you listen to: Home hides the rest and adds songs, playlists and " +
                    "charts in yours.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = "[ PICK LANGUAGES ]",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                modifier = Modifier.clickable(onClick = actions.editLanguages).padding(vertical = 8.dp),
            )
            Text(
                text = "[ NOT NOW ]",
                style = MaterialTheme.typography.labelMedium,
                color = CliBeatsTextSecondary,
                modifier = Modifier.clickable(onClick = actions.skipLanguages).padding(vertical = 8.dp),
            )
        }
    }
}

/**
 * The languages Home is tuned to. Nothing picked means every language (nothing hidden). Saved when Done is
 * tapped; Home reloads for them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LanguagePickerDialog(
    current: Set<HomeLanguage>,
    onDone: (Set<HomeLanguage>) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalAccentColor.current
    var picked by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, accent),
        title = { Text("> home --languages") },
        text = {
            Column {
                Text(
                    text =
                        "Home shows songs, playlists and charts in these languages and hides others. " +
                            "Pick none to see everything.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CliBeatsTextSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HomeLanguage.ordered.forEach { language ->
                        val on = language in picked
                        Text(
                            text = if (on) "\u2713 ${language.label}" else language.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) MaterialTheme.colorScheme.background else accent,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (on) accent else CliBeatsSurface)
                                    .border(1.dp, if (on) accent else CliBeatsBorderInactive, RoundedCornerShape(4.dp))
                                    .clickable { picked = if (on) picked - language else picked + language }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDone(picked)
                onDismiss()
            }) { Text(if (picked.isEmpty()) "Show all" else "Done", color = accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = CliBeatsTextSecondary) }
        },
    )
}

/**
 * Local Home with no songs: what to do next instead of empty buttons. No folder yet: choose one. Scanning:
 * the progress. Scanned but nothing found (or the folder can't be read): pick another folder.
 */
@Composable
internal fun LocalSetupCard(
    setup: LocalSetup,
    onChooseFolder: () -> Unit,
) {
    val accent = LocalAccentColor.current
    val scanning = setup.scanning
    val copy = setupCopy(setup)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(CliBeatsSurface)
                .border(1.dp, accent, RoundedCornerShape(6.dp))
                .padding(16.dp),
    ) {
        Text(copy.command, style = MaterialTheme.typography.labelSmall, color = accent)
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (scanning != null) {
                CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = copy.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(copy.body, style = MaterialTheme.typography.bodySmall, color = CliBeatsTextSecondary)
        if (copy.button.isNotEmpty()) {
            Text(
                text = copy.button,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                modifier = Modifier.padding(top = 8.dp).clickable(onClick = onChooseFolder).padding(vertical = 8.dp),
            )
        }
    }
}

/** The set-up card's words for each state. */
private data class SetupCopy(
    val command: String,
    val title: String,
    val body: String,
    /** Empty while scanning (nothing to tap). */
    val button: String,
)

private fun setupCopy(setup: LocalSetup): SetupCopy {
    val scanning = setup.scanning
    return when {
        scanning != null ->
            SetupCopy(
                "> local --scan",
                "Scanning your music\u2026",
                if (scanning.total > 0) "${scanning.processed} of ${scanning.total} files" else "Looking for songs",
                "",
            )
        setup.failure != null ->
            SetupCopy(
                "> local --error",
                "Couldn't read your music folder",
                "${setup.failure} Choose the folder again to give CLIBeats access.",
                "[ CHOOSE FOLDER AGAIN ]",
            )
        setup.folderCount > 0 ->
            SetupCopy(
                "> local --empty",
                "No music found in your folder",
                "CLIBeats looked for audio files (MP3, FLAC, M4A, OGG, WAV\u2026) in it and its subfolders " +
                    "and found none. Pick the folder that has your songs.",
                "[ CHOOSE ANOTHER FOLDER ]",
            )
        else ->
            SetupCopy(
                "> local --setup",
                "No local music yet",
                "Pick the folder with your music. CLIBeats reads the songs in it (and its subfolders), and " +
                    "Home fills with mixes, albums and artists from your library. Works offline.",
                "[ CHOOSE MUSIC FOLDER ]",
            )
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
            opened.progress != null -> "$kind \u00b7 ${tracks.size} songs \u00b7 ${opened.progress}"
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
                opened.progress != null -> "Finding the chart's songs on YouTube Music\u2026"
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
private val LANGUAGE_CHIP_MAX: Dp = 220.dp
private const val COLUMN_WIDTH_FRACTION = 0.86f
private const val SONG_ART_DP = 48
private const val PLAYING_ALPHA = 0.10f
private val CARD_DP: Dp = 144.dp
private const val CARD_ART_PX = 480
private val SHELF_GAP: Dp = 12.dp
