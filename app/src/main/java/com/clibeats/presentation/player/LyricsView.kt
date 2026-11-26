@file:Suppress(
    "FunctionNaming",
    "ktlint:standard:function-naming",
    "MagicNumber",
    "TooManyFunctions",
    "LoopWithTooManyJumpStatements",
    "MatchingDeclarationName",
)

package com.clibeats.presentation.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Abc
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clibeats.domain.model.LyricLine
import com.clibeats.domain.model.Lyrics
import com.clibeats.domain.model.PlaybackState
import com.clibeats.presentation.theme.CliBeatsBorderInactive
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor
import com.clibeats.presentation.theme.PoppinsFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter

/** The translation / pronunciation switches and what they show. */
internal class LyricsExtras(
    val showTranslation: Boolean,
    val showPronunciation: Boolean,
    /** Translations or pronunciations are being fetched. */
    val loading: Boolean,
    val onToggleTranslation: () -> Unit,
    val onTogglePronunciation: () -> Unit,
)

/**
 * The lyrics area of Now Playing.
 * - Syllable-synced lyrics fill in syllable by syllable as they're sung.
 * - Line-synced lyrics light up line by line.
 * - Either way the current line stays in view; scroll yourself and it waits a few seconds before following
 *   again. Tap a line to jump there.
 * - Plain lyrics are shown as text to scroll.
 * - A slim bar on top says how they're timed and where they're from, with the Translate and Pronunciation
 *   switches; each line's translation / pronunciation shows under it.
 */
@Suppress("LongParameterList")
@Composable
internal fun LyricsPanel(
    state: LyricsState,
    playback: PlaybackState,
    isLocalSong: Boolean,
    onSeek: (Long) -> Unit,
    /** The player's exact position now (read every frame). */
    livePosition: () -> Long,
    extras: LyricsExtras,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state is LyricsState.Loaded) LyricsToolbar(state, extras)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                LyricsState.Loading -> if (!isLocalSong) LyricsMessage("Searching for lyrics…", busy = true)
                LyricsState.None ->
                    LyricsMessage(
                        if (isLocalSong) {
                            "No lyrics in this file.\n\nLyrics are read from the song's LYRICS tag or a .lrc file " +
                                "with the same name next to it."
                        } else {
                            "No lyrics found for this song."
                        },
                    )
                LyricsState.Offline ->
                    LyricsMessage(
                        "You're offline, and this song's lyrics haven't been saved yet.\n\n" +
                            "They load when you're back online (downloaded songs keep theirs).",
                    )
                is LyricsState.Loaded ->
                    when (val lyrics = state.lyrics) {
                        is Lyrics.Synced -> {
                            val position = rememberPlaybackPosition(playback, livePosition)
                            SyncedLyrics(lyrics, position, onSeek, extras)
                        }
                        is Lyrics.Plain -> PlainLyrics(lyrics, extras)
                    }
            }
        }
    }
}

/** "SYLLABLE · LYRICSPLUS · APPLE", and the Translate / Pronunciation switches. */
@Composable
private fun LyricsToolbar(
    state: LyricsState.Loaded,
    extras: LyricsExtras,
) {
    val lyrics = state.lyrics
    val timing =
        when {
            lyrics is Lyrics.Synced && lyrics.hasWordTiming -> "SYLLABLE"
            lyrics is Lyrics.Synced -> "LINE SYNCED"
            else -> "PLAIN"
        }
    val label = listOf(timing, (lyrics.source ?: "from file").uppercase()).joinToString(" · ")
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CliBeatsTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (extras.loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = LocalAccentColor.current,
            )
        }
        LyricsToggle(
            icon = Icons.Outlined.Translate,
            label = "Translation",
            on = extras.showTranslation,
            onClick = extras.onToggleTranslation,
        )
        if (state.canPronounce) {
            LyricsToggle(
                icon = Icons.Outlined.Abc,
                label = "Pronunciation",
                on = extras.showPronunciation,
                onClick = extras.onTogglePronunciation,
            )
        }
    }
}

/** A small framed switch: accent and filled when on. */
@Composable
private fun LyricsToggle(
    icon: ImageVector,
    label: String,
    on: Boolean,
    onClick: () -> Unit,
) {
    val accent = LocalAccentColor.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier =
            Modifier
                .size(TOGGLE_SIZE)
                .clip(shape)
                .background(if (on) accent.copy(alpha = 0.16f) else Color.Transparent)
                .border(1.dp, if (on) accent else CliBeatsBorderInactive, shape)
                .toggleable(value = on, role = Role.Switch, onValueChange = { onClick() })
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (on) accent else CliBeatsTextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun LyricsMessage(
    text: String,
    busy: Boolean = false,
) {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = LocalAccentColor.current,
                )
            }
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = CliBeatsTextSecondary)
        }
    }
}

/**
 * The playback position, read from the player's own clock on every frame. It's exact at all times, including
 * right after tapping a line (the player reports the new spot at once), so syllables never fill twice.
 */
@Composable
private fun rememberPlaybackPosition(
    playback: PlaybackState,
    livePosition: () -> Long,
): State<Long> {
    val position = remember { mutableLongStateOf(playback.positionMs) }
    val trackId = playback.currentTrack?.id
    LaunchedEffect(trackId, playback.isPlaying) {
        position.longValue = livePosition()
        if (!playback.isPlaying) return@LaunchedEffect
        while (true) {
            withFrameNanos { position.longValue = livePosition() }
        }
    }
    // Paused: still follow seeks (the waveform, a tapped line).
    LaunchedEffect(playback.positionMs) { if (!playback.isPlaying) position.longValue = livePosition() }
    return position
}

/** The lyrics, with a blank break line added up front when singing starts a while into the song. */
private fun withIntro(lines: List<LyricLine>): List<LyricLine> {
    val first = lines.firstOrNull() ?: return lines
    return if (first.startMs >= INTRO_MIN_MS) listOf(LyricLine(0L, first.startMs, "")) + lines else lines
}

@Composable
private fun SyncedLyrics(
    lyrics: Lyrics.Synced,
    position: State<Long>,
    onSeek: (Long) -> Unit,
    extras: LyricsExtras,
) {
    val lines = remember(lyrics) { withIntro(lyrics.lines) }
    val starts = remember(lines) { LongArray(lines.size) { lines[it].startMs } }
    val active by remember(lines) { derivedStateOf { activeIndex(starts, position.value) } }
    val listState = rememberLazyListState()
    // Following the song pauses while you scroll yourself, then picks up again.
    var followPausedUntil by remember { mutableLongStateOf(0L) }
    var autoScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it && !autoScrolling }.collect {
            followPausedUntil = System.currentTimeMillis() + FOLLOW_PAUSE_MS
        }
    }
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(followPausedUntil) {
        following = false
        val wait = followPausedUntil - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        following = true
    }
    LaunchedEffect(active, following) {
        if (!following || active < 0) return@LaunchedEffect
        autoScrolling = true
        try {
            glideTo(listState, active)
        } finally {
            autoScrolling = false
        }
    }
    val accent = LocalAccentColor.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize().fadingEdges()) {
        val topPad = maxHeight * ACTIVE_LINE_AT
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPad, bottom = maxHeight - topPad),
            verticalArrangement = Arrangement.spacedBy(LINE_GAP),
        ) {
            itemsIndexed(lines, key = { i, line -> "$i@${line.startMs}" }) { index, line ->
                // Lines further from the current one fade a little more (cheap, unlike a blur).
                val distance = if (active < 0) index + 1 else kotlin.math.abs(index - active)
                if (line.text.isBlank()) {
                    BreakDots(line, isActive = index == active, position = position, accent = accent)
                } else {
                    LyricLineText(
                        line = line,
                        isActive = index == active,
                        isPast = active >= 0 && index < active,
                        position = position,
                        distance = if (following) distance else 1,
                        extras = extras,
                        onClick = { onSeek(line.startMs) },
                    )
                }
            }
        }
    }
}

/**
 * Brings line [index] to the reading spot. On screen: an eased scroll by the exact distance. Off screen (after a
 * seek or your own scrolling): jump close first, then glide.
 */
private suspend fun glideTo(
    listState: androidx.compose.foundation.lazy.LazyListState,
    index: Int,
) {
    if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
        listState.scrollToItem((index - 1).coerceAtLeast(0))
    }
    val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (target == null) {
        listState.animateScrollToItem(index)
    } else {
        listState.animateScrollBy(target.offset.toFloat(), tween(FOLLOW_SCROLL_MS, easing = FastOutSlowInEasing))
    }
}

/** The last line that has started (-1 before the first). */
private fun activeIndex(
    starts: LongArray,
    position: Long,
): Int {
    var low = 0
    var high = starts.size - 1
    var found = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (starts[mid] <= position) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return found
}

private val lyricStyle: TextStyle
    @Composable get() =
        MaterialTheme.typography.headlineSmall.copy(
            fontFamily = PoppinsFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
        )

@Suppress("LongParameterList")
@Composable
private fun LyricLineText(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    position: State<Long>,
    distance: Int,
    extras: LyricsExtras,
    onClick: () -> Unit,
) {
    val bright = CliBeatsTextPrimary
    val dimAlpha by animateFloatAsState(
        targetValue =
            when {
                isActive && line.words.isEmpty() -> 1f
                isActive -> DIM_ACTIVE_ALPHA
                isPast -> DIM_PAST_ALPHA * distanceFade(distance)
                else -> DIM_ALPHA * distanceFade(distance)
            },
        animationSpec = tween(LINE_FADE_MS),
        label = "lineAlpha",
    )
    val scale by animateFloatAsState(if (isActive) 1f else INACTIVE_SCALE, tween(LINE_FADE_MS), label = "lineScale")
    val style = lyricStyle
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                }.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Play from this line",
                    onClick = onClick,
                ).padding(vertical = 2.dp)
                .semantics { contentDescription = line.text },
    ) {
        Box {
            Text(text = line.text, style = style, color = bright.copy(alpha = dimAlpha))
            // Syllable sync: the same text in full colour, shown only over the part already sung.
            if (isActive && line.words.isNotEmpty()) {
                // Each character's box is measured once per layout; every frame only picks which are sung.
                var boxes by remember(line) { mutableStateOf<List<Rect>>(emptyList()) }
                val path = remember { Path() }
                Text(
                    text = line.text,
                    style = style,
                    color = bright,
                    onTextLayout = { boxes = characterBoxes(it) },
                    modifier =
                        Modifier.drawWithContent {
                            if (boxes.isEmpty()) return@drawWithContent
                            sungPath(path, line, boxes, position.value)
                            clipPath(path) { this@drawWithContent.drawContent() }
                        },
                )
            }
        }
        SubLines(
            pronunciation = line.pronunciation.takeIf { extras.showPronunciation },
            translation = line.translation.takeIf { extras.showTranslation },
            alpha = if (isActive) SUB_ACTIVE_ALPHA else SUB_ALPHA * distanceFade(distance),
        )
    }
}

/** A line's pronunciation and / or translation, smaller, under it. */
@Composable
private fun SubLines(
    pronunciation: String?,
    translation: String?,
    alpha: Float,
) {
    val color = CliBeatsTextPrimary.copy(alpha = alpha)
    if (!pronunciation.isNullOrBlank()) {
        Text(text = pronunciation, style = subStyle, color = color, modifier = Modifier.padding(top = 4.dp))
    }
    if (!translation.isNullOrBlank()) {
        Text(
            text = translation,
            style = subStyle.copy(fontWeight = FontWeight.Normal, fontStyle = FontStyle.Italic),
            color = color,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private val subStyle: TextStyle
    @Composable get() =
        MaterialTheme.typography.titleMedium.copy(
            fontFamily = PoppinsFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            lineHeight = 24.sp,
        )

/** Each character's box, as tall as its text line (so ascenders and descenders are covered). */
private fun characterBoxes(layout: TextLayoutResult): List<Rect> =
    List(layout.layoutInput.text.length) { at ->
        val box = layout.getBoundingBox(at)
        val lineIndex = layout.getLineForOffset(at)
        Rect(box.left, layout.getLineTop(lineIndex), box.right, layout.getLineBottom(lineIndex))
    }

/**
 * Sets [path] to the part of [line] sung by [positionMs]: whole syllables, and the current one up to how far
 * it has got (each of its characters takes an equal share of its time).
 */
private fun sungPath(
    path: Path,
    line: LyricLine,
    boxes: List<Rect>,
    positionMs: Long,
) {
    path.reset()
    var offset = 0
    for (word in line.words) {
        val chars = word.text.length
        if (positionMs < word.startMs) break
        val progress =
            if (positionMs >= word.endMs || word.endMs <= word.startMs) {
                1f
            } else {
                (positionMs - word.startMs).toFloat() / (word.endMs - word.startMs)
            }
        for (i in 0 until chars) {
            val box = boxes.getOrNull(offset + i) ?: break
            val charStart = i.toFloat() / chars
            if (progress <= charStart) break
            val filled = ((progress - charStart) * chars).coerceIn(0f, 1f)
            path.addRect(Rect(box.left, box.top, box.left + box.width * filled, box.bottom))
        }
        offset += chars
    }
}

/** An instrumental break: three dots that fill as the break goes on, while it's the current line. */
@Composable
private fun BreakDots(
    line: LyricLine,
    isActive: Boolean,
    position: State<Long>,
    accent: Color,
) {
    val height = if (isActive && line.endMs - line.startMs >= BREAK_DOTS_MIN_MS) BREAK_HEIGHT else 0.dp
    Row(
        modifier = Modifier.height(height).padding(start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (height == 0.dp) return@Row
        repeat(BREAK_DOTS) { dot ->
            Box(
                modifier =
                    Modifier.size(9.dp).drawBehind {
                        val span = (line.endMs - line.startMs).coerceAtLeast(1L).toFloat()
                        val progress = ((position.value - line.startMs) / span).coerceIn(0f, 1f)
                        val lit = (progress * BREAK_DOTS - dot).coerceIn(0f, 1f)
                        drawCircle(color = accent.copy(alpha = 0.25f + 0.75f * lit))
                    },
            )
        }
    }
}

/** Plain (untimed) lyrics: the text, verse by verse (line by line with translations / pronunciations on). */
@Composable
private fun PlainLyrics(
    lyrics: Lyrics.Plain,
    extras: LyricsExtras,
) {
    val lines = remember(lyrics.text) { lyrics.text.lines() }
    val lineStyle =
        MaterialTheme.typography.titleMedium.copy(
            fontFamily = PoppinsFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 30.sp,
        )
    LazyColumn(
        modifier = Modifier.fillMaxSize().fadingEdges(),
        contentPadding = PaddingValues(vertical = 24.dp),
    ) {
        itemsIndexed(lines) { i, line ->
            if (line.isBlank()) {
                Box(Modifier.height(22.dp))
            } else {
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(text = line.trim(), style = lineStyle, color = CliBeatsTextPrimary.copy(alpha = 0.92f))
                    SubLines(
                        pronunciation = lyrics.pronunciations.getOrNull(i).takeIf { extras.showPronunciation },
                        translation = lyrics.translations.getOrNull(i).takeIf { extras.showTranslation },
                        alpha = SUB_ALPHA,
                    )
                }
            }
        }
    }
}

/** Fades the list out at the top and bottom edges instead of cutting lines off. */
private fun Modifier.fadingEdges(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        drawRect(
            brush =
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    EDGE_FADE to Color.Black,
                    1f - EDGE_FADE to Color.Black,
                    1f to Color.Transparent,
                ),
            blendMode = BlendMode.DstIn,
        )
    }

/** 1 for the lines next to the current one, a little less further away. */
private fun distanceFade(distance: Int): Float = if (distance <= 1) 1f else maxOf(0.45f, 1f - 0.18f * (distance - 1))

private const val FOLLOW_SCROLL_MS = 520
private const val SUB_ALPHA = 0.5f
private const val SUB_ACTIVE_ALPHA = 0.82f
private val TOGGLE_SIZE = 34.dp
private const val INTRO_MIN_MS = 4_000L
private const val FOLLOW_PAUSE_MS = 3_000L
private const val ACTIVE_LINE_AT = 0.28f
private val LINE_GAP = 18.dp
private const val DIM_ALPHA = 0.32f
private const val DIM_PAST_ALPHA = 0.26f
private const val DIM_ACTIVE_ALPHA = 0.36f
private const val INACTIVE_SCALE = 0.94f
private const val LINE_FADE_MS = 280
private const val BREAK_DOTS = 3
private const val BREAK_DOTS_MIN_MS = 3_000L
private val BREAK_HEIGHT = 30.dp
private const val EDGE_FADE = 0.08f
