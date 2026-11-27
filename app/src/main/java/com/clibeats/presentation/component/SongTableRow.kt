@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:multiline-expression-wrapping",
)

package com.clibeats.presentation.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsDivider
import com.clibeats.presentation.theme.CliBeatsTextDisabled
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor

/**
 * Dense 48dp song table row — the primary list item component.
 *
 * States:
 * - Normal: CliBeatsBackground background
 * - Focused/Hovered: CliBeatsSurfaceVariant background
 * - Now Playing: CliBeatsSurface + 2dp CliBeatsAccent left border
 *
 * Per UI-SPEC: 48dp height, 32x32dp square artwork, start-aligned text.
 */
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongTableRow(
    trackTitle: String,
    artist: String,
    duration: String,
    index: Int? = null,
    isNowPlaying: Boolean = false,
    isDownloaded: Boolean = false,
    artworkContent: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    // Taller row with bigger artwork (Home "Details" view).
    large: Boolean = false,
    // Extra TalkBack actions on the row (e.g. "Remove from queue" where removal is a swipe).
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
    actions: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    onSwipeRight: (() -> Unit)? = null,
    swipeRightLabel: String = "+ QUEUE",
    onLongPress: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    /** Selection mode: true/false draws `[x]`/`[ ]` in front of the row; null = not selecting. */
    selected: Boolean? = null,
    /** Greyed out, e.g. a song still waiting to download; it gets full colour when done. */
    dimmed: Boolean = false,
    /** Short text shown instead of the length, e.g. "QUEUED" or "42%". */
    status: String? = null,
) {
    val accent = LocalAccentColor.current
    val semanticsDescription = songRowSemantics(trackTitle, artist, isNowPlaying, selected, status)
    val rowHeight = if (large) LARGE_ROW_DP.dp else 48.dp
    val rowBackground = when {
        selected == true -> accent.copy(alpha = SELECTED_ALPHA)
        isNowPlaying -> accent.copy(alpha = PLAYING_ALPHA)
        else -> CliBeatsBackground
    }

    // Swipe right = add to queue, swipe left = add to favorites (row follows the finger, snaps back). Either can
    // be turned off in Settings → Gestures.
    val gestureSettings = LocalGestureSettings.current
    val onSwipeRight = onSwipeRight.takeIf { gestureSettings.songSwipeRight }
    val onSwipeLeft = onSwipeLeft.takeIf { gestureSettings.songSwipeLeft }
    var dragX by remember { mutableFloatStateOf(0f) }
    val swipeModifier =
        if (onSwipeRight == null && onSwipeLeft == null) {
            Modifier
        } else {
            Modifier.pointerInput(onSwipeRight, onSwipeLeft) {
                val threshold = SWIPE_THRESHOLD_DP.dp.toPx()
                val maxRight = if (onSwipeRight != null) threshold * SWIPE_MAX_FACTOR else 0f
                val maxLeft = if (onSwipeLeft != null) -threshold * SWIPE_MAX_FACTOR else 0f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Only claim a direction this row handles; the other is left to the tab pager.
                    val start =
                        awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            val handled = if (over > 0f) onSwipeRight != null else onSwipeLeft != null
                            if (handled) change.consume()
                        } ?: return@awaitEachGesture
                    dragX = 0f
                    val completed =
                        horizontalDrag(start.id) { change ->
                            dragX = (dragX + change.positionChange().x).coerceIn(maxLeft, maxRight)
                            change.consume()
                        }
                    if (completed && dragX > threshold) onSwipeRight?.invoke()
                    if (completed && dragX < -threshold) onSwipeLeft?.invoke()
                    dragX = 0f
                }
            }
        }

    Box(modifier = modifier.fillMaxWidth().then(swipeModifier)) {
        if (dragX != 0f) SwipeHint(dragX, swipeRightLabel)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = dragX
                    alpha = if (dimmed) DIMMED_ALPHA else 1f
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .background(rowBackground)
                    .semantics {
                        contentDescription = semanticsDescription
                        if (accessibilityActions.isNotEmpty()) customActions = accessibilityActions
                    }
                    .combinedClickable(
                        onClickLabel = "Play",
                        onLongClickLabel = if (onLongPress != null) "More options" else null,
                        onLongClick = onLongPress,
                        onClick = onClick,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ── Now-playing left accent bar (2dp) ─────────────────────────
                if (isNowPlaying) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(rowHeight)
                            .background(accent),
                    )
                } else {
                    Spacer(modifier = Modifier.width(2.dp))
                }

                Spacer(modifier = Modifier.width(6.dp))

                if (selected != null) {
                    Text(
                        text = if (selected) "[x]" else "[ ]",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) accent else CliBeatsTextSecondary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }

                leading?.invoke()

                // ── Track index or artwork ─────────────────────────────────────
                if (artworkContent != null) {
                    Box(
                        modifier = Modifier.size(if (large) LARGE_ART_DP.dp else 32.dp),
                    ) {
                        artworkContent()
                    }
                } else {
                    // Index number fallback when no artwork
                    Text(
                        text = index?.toString() ?: "–",
                        style = MaterialTheme.typography.labelMedium,
                        color = CliBeatsTextDisabled,
                        modifier = Modifier.width(24.dp),
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // ── Track title + artist (fills remaining space) ──────────────
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isNowPlaying) {
                            Text(
                                text = "▶ ",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                            )
                        }
                        if (isDownloaded) {
                            Text(
                                text = "[↓] ",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                            )
                        }
                        Text(
                            text = trackTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isNowPlaying) accent else CliBeatsTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.labelMedium,
                        color = CliBeatsTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // ── Duration (end-aligned, tabular) ───────────────────────────
                Text(
                    text = status ?: duration,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (status != null) accent else CliBeatsTextSecondary,
                    modifier = Modifier.padding(end = if (actions == null) 16.dp else 8.dp),
                )

                actions?.let {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        it()
                    }
                }
            }

            // ── 1dp row divider ───────────────────────────────────────────────
            HorizontalDivider(
                thickness = 1.dp,
                color = CliBeatsDivider,
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
fun SongTableHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "#",
                style = MaterialTheme.typography.labelSmall,
                color = CliBeatsTextSecondary,
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "TITLE & ARTIST",
                style = MaterialTheme.typography.labelSmall,
                color = CliBeatsTextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "TIME",
                style = MaterialTheme.typography.labelSmall,
                color = CliBeatsTextSecondary,
                modifier = Modifier.padding(end = 16.dp),
            )
        }
        HorizontalDivider(thickness = 1.dp, color = CliBeatsDivider)
    }
}

private const val SWIPE_THRESHOLD_DP = 72
private const val SWIPE_MAX_FACTOR = 1.5f

/** Revealed behind the row while dragging: "+ QUEUE" (dragging right) or "♥ FAVORITE" (dragging left). */
@Suppress("FunctionNaming")
@Composable
private fun BoxScope.SwipeHint(
    dragX: Float,
    rightLabel: String,
) {
    val right = dragX > 0f
    Text(
        text = if (right) rightLabel else "♥ FAVORITE",
        style = MaterialTheme.typography.labelMedium,
        color = CliBeatsAccent,
        modifier =
            Modifier
                .align(if (right) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 16.dp),
    )
}

private const val LARGE_ROW_DP = 64
private const val LARGE_ART_DP = 52
private const val SELECTED_ALPHA = 0.18f
private const val PLAYING_ALPHA = 0.10f
private const val DIMMED_ALPHA = 0.4f

internal fun songRowSemantics(
    trackTitle: String,
    artist: String,
    isNowPlaying: Boolean = false,
    selected: Boolean? = null,
    status: String? = null,
): String =
    buildString {
        append("$trackTitle by $artist")
        if (isNowPlaying) append(", playing now")
        if (selected == true) append(", selected")
        status?.let { append(", $it") }
    }
