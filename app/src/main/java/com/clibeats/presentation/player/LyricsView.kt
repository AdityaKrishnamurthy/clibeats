@file:Suppress(
    "FunctionNaming",
    "ktlint:standard:function-naming",
    "MagicNumber",
    "TooManyFunctions",
    "LoopWithTooManyJumpStatements",
)

package com.clibeats.presentation.player

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clibeats.domain.model.LyricLine
import com.clibeats.domain.model.Lyrics
import com.clibeats.domain.model.PlaybackState
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter

/**
 * The lyrics area of Now Playing.
 * - Syllable-synced lyrics fill in syllable by syllable as they're sung.
 * - Line-synced lyrics light up line by line.
 * - Either way the current line stays in view; scroll yourself and it waits a few seconds before following
 *   again. Tap a line to jump there.
 * - Plain lyrics are shown as text to scroll.
 */
@Composable
internal fun LyricsPanel(
    state: LyricsState,
    playback: PlaybackState,
    isLocalSong: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            LyricsState.Loading -> Unit
            LyricsState.None ->
                LyricsMessage(
                    if (isLocalSong) {
                        "No lyrics in this file.\n\nLyrics are read from the song's LYRICS tag or a .lrc file " +
                            "with the same name next to it."
                    } else {
                        "Lyrics for streamed songs aren't available yet."
                    },
                )
            is LyricsState.Loaded ->
                when (val lyrics = state.lyrics) {
                    is Lyrics.Synced -> {
                        val position = rememberPlaybackPosition(playback)
                        SyncedLyrics(lyrics, position, onSeek)
                    }
                    is Lyrics.Plain -> PlainLyrics(lyrics.text)
                }
        }
    }
}

@Composable
private fun LyricsMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = CliBeatsTextSecondary)
    }
}

/**
 * The playback position, moving every frame between the player's half-second updates so syllables fill
 * smoothly. Never steps back a few milliseconds when an update lands slightly behind.
 */
@Composable
private fun rememberPlaybackPosition(playback: PlaybackState): State<Long> {
    val position = remember { mutableLongStateOf(playback.positionMs) }
    val trackId = playback.currentTrack?.id
    LaunchedEffect(trackId) { position.longValue = playback.positionMs }
    LaunchedEffect(playback.positionMs, playback.isPlaying, trackId) {
        val reported = playback.positionMs
        val current = position.longValue
        val base =
            if (playback.isPlaying && reported < current && current - reported < JITTER_MS) current else reported
        position.longValue = base
        if (!playback.isPlaying) return@LaunchedEffect
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> position.longValue = base + (now - startNanos) / 1_000_000 }
        }
    }
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
            listState.animateScrollToItem(active)
        } finally {
            autoScrolling = false
        }
    }
    val accent = LocalAccentColor.current
    val blurAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    BoxWithConstraints(modifier = Modifier.fillMaxSize().fadingEdges()) {
        val topPad = maxHeight * ACTIVE_LINE_AT
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPad, bottom = maxHeight - topPad),
            verticalArrangement = Arrangement.spacedBy(LINE_GAP),
        ) {
            itemsIndexed(lines, key = { i, line -> "$i@${line.startMs}" }) { index, line ->
                val distance = if (active < 0) index + 1 else kotlin.math.abs(index - active)
                val blur = if (blurAllowed && following && distance >= 2) minOf(distance - 1, 3) * 1.2f else 0f
                if (line.text.isBlank()) {
                    BreakDots(line, isActive = index == active, position = position, accent = accent)
                } else {
                    LyricLineText(
                        line = line,
                        isActive = index == active,
                        isPast = active >= 0 && index < active,
                        position = position,
                        blurDp = blur,
                        onClick = { onSeek(line.startMs) },
                    )
                }
            }
        }
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
            fontWeight = FontWeight.Bold,
            fontSize = 25.sp,
            lineHeight = 33.sp,
        )

@Suppress("LongParameterList")
@Composable
private fun LyricLineText(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    position: State<Long>,
    blurDp: Float,
    onClick: () -> Unit,
) {
    val bright = CliBeatsTextPrimary
    val dimAlpha by animateFloatAsState(
        targetValue =
            when {
                isActive && line.words.isEmpty() -> 1f
                isActive -> DIM_ACTIVE_ALPHA
                isPast -> DIM_PAST_ALPHA
                else -> DIM_ALPHA
            },
        animationSpec = tween(LINE_FADE_MS),
        label = "lineAlpha",
    )
    val scale by animateFloatAsState(if (isActive) 1f else INACTIVE_SCALE, tween(LINE_FADE_MS), label = "lineScale")
    val style = lyricStyle
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                }.then(if (blurDp > 0f) Modifier.blur(blurDp.dp) else Modifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Play from this line",
                    onClick = onClick,
                ).padding(vertical = 2.dp)
                .semantics { contentDescription = line.text },
    ) {
        Text(text = line.text, style = style, color = bright.copy(alpha = dimAlpha))
        // Syllable sync: the same text in full colour, shown only over the part already sung.
        if (isActive && line.words.isNotEmpty()) {
            var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = line.text,
                style = style,
                color = bright,
                onTextLayout = { layout = it },
                modifier =
                    Modifier.drawWithContent {
                        val result = layout ?: return@drawWithContent
                        clipPath(sungPath(line, result, position.value)) { this@drawWithContent.drawContent() }
                    },
            )
        }
    }
}

/** The area of [line]'s text sung by [positionMs]: whole syllables, and the current one up to how far it's got. */
private fun sungPath(
    line: LyricLine,
    layout: TextLayoutResult,
    positionMs: Long,
): Path {
    val path = Path()
    val length = layout.layoutInput.text.length
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
        // Each character of the syllable takes its share of the syllable's time.
        for (i in 0 until chars) {
            val at = offset + i
            if (at >= length) break
            val charStart = i.toFloat() / chars
            val charEnd = (i + 1).toFloat() / chars
            if (progress <= charStart) break
            val box = layout.getBoundingBox(at)
            val filled = ((progress - charStart) / (charEnd - charStart)).coerceIn(0f, 1f)
            // Line height, not glyph height, so ascenders and descenders are covered.
            val lineIndex = layout.getLineForOffset(at)
            val top = layout.getLineTop(lineIndex)
            val bottom = layout.getLineBottom(lineIndex)
            path.addRect(Rect(box.left, top, box.left + box.width * filled, bottom))
        }
        offset += chars
    }
    return path
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

/** Plain (untimed) lyrics: just the text, verse by verse. */
@Composable
private fun PlainLyrics(text: String) {
    val verses = remember(text) { text.split(Regex("\n\\s*\n")) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().fadingEdges(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Text(
                text = "NOT SYNCED · PLAIN LYRICS",
                style = MaterialTheme.typography.labelSmall,
                color = CliBeatsTextSecondary,
            )
        }
        itemsIndexed(verses) { _, verse ->
            Text(
                text = verse.trim(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
                color = CliBeatsTextPrimary.copy(alpha = 0.92f),
            )
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

private const val JITTER_MS = 300L
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
