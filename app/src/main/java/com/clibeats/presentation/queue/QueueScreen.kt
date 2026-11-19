@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:multiline-expression-wrapping",
    "MagicNumber",
)

package com.clibeats.presentation.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.clibeats.presentation.component.SongTableRow
import com.clibeats.presentation.search.formatDuration
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsBackground
import com.clibeats.presentation.theme.CliBeatsSurface
import com.clibeats.presentation.theme.CliBeatsTextPrimary
import com.clibeats.presentation.theme.CliBeatsTextSecondary

@Suppress("FunctionNaming", "LongMethod")
@Composable
fun QueueScreen(
    viewModel: QueueViewModel = hiltViewModel(),
    onClose: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CliBeatsBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CliBeatsSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp).padding(end = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Close queue",
                            tint = CliBeatsTextPrimary,
                        )
                    }
                }
                Text(
                    text = "PLAYBACK QUEUE",
                    style = MaterialTheme.typography.titleMedium,
                    color = CliBeatsTextPrimary,
                )
            }

            if (state is QueueUiState.Success) {
                OutlinedButton(
                    onClick = viewModel::onClear,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CliBeatsTextSecondary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                    )
                    Text(text = " Clear upcoming", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        when (val currentState = state) {
            is QueueUiState.Empty -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Queue is empty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CliBeatsTextSecondary,
                    )
                }
            }

            is QueueUiState.Success -> ReorderableQueue(currentState, viewModel)
        }
    }
}

/**
 * Queue list reordered by dragging a row's handle. The order changes locally while dragging and is
 * committed to the player as a single move when the finger lifts, so playback isn't disturbed mid-drag.
 */
@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
private fun ReorderableQueue(
    state: QueueUiState.Success,
    viewModel: QueueViewModel,
) {
    val tracks = state.tracks
    // Stable keys that survive reordering; the same song can be queued more than once.
    val keys =
        remember(tracks) {
            val seen = HashMap<String, Int>()
            tracks.map { track ->
                val n = seen.merge(track.id, 1, Int::plus) ?: 1
                "${track.id}#$n"
            }
        }
    var order by remember(tracks) { mutableStateOf(tracks.indices.toList()) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val edge = with(LocalDensity.current) { AUTO_SCROLL_EDGE_DP.dp.toPx() }
    val haptics = LocalHapticFeedback.current

    fun endDrag(commit: Boolean) {
        val to = order.indexOfFirst { keys[it] == dragKey }
        val moved = dragFrom >= 0 && to >= 0 && to != dragFrom
        if (commit && moved) {
            viewModel.onMove(dragFrom, to)
        } else {
            order = tracks.indices.toList()
        }
        dragKey = null
        dragFrom = -1
        dragOffset = 0f
    }

    fun startDrag(
        key: String,
        index: Int,
    ) {
        dragKey = key
        dragFrom = order.indexOf(index)
        dragOffset = 0f
    }

    @Suppress("ReturnCount")
    fun dragBy(
        key: String,
        amountY: Float,
    ) {
        dragOffset += amountY
        val info = listState.layoutInfo
        val current = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        // Wait until the list has laid out the last swap; acting on stale positions swapped rows back and forth.
        if (current.index != order.indexOfFirst { keys[it] == key }) return
        // Keep scrolling while the row is held near the top or bottom.
        val top = current.offset + dragOffset
        val bottom = top + current.size
        val nearEnd = bottom > info.viewportEndOffset - edge && amountY > 0
        val nearStart = top < info.viewportStartOffset + edge && amountY < 0
        if (nearEnd || nearStart) dragOffset += listState.dispatchRawDelta(amountY)
        val center = current.offset + dragOffset + current.size / 2f
        val target =
            info.visibleItemsInfo.firstOrNull {
                it.key != key && center >= it.offset && center <= it.offset + it.size
            } ?: return
        // Moving the first visible row would make the list scroll to follow it; keep the view where it is.
        val firstIndex = listState.firstVisibleItemIndex
        val firstOffset = listState.firstVisibleItemScrollOffset
        order = order.toMutableList().apply { add(target.index, removeAt(current.index)) }
        if (target.index == firstIndex || current.index == firstIndex) {
            listState.requestScrollToItem(firstIndex, firstOffset)
        }
        dragOffset += current.offset - target.offset
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(items = order, key = { _, i -> keys[i] }) { position, i ->
            val track = tracks[i]
            val key = keys[i]
            val dragging = key == dragKey
            Box(
                modifier =
                    (
                        if (dragging) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer {
                                    translationY = dragOffset
                                    shadowElevation = 8f
                                }
                        } else {
                            Modifier.animateItem()
                        }
                    ).pointerInput(key) {
                        // Hold anywhere on the row (number, title, ...) and move it up or down.
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                startDrag(key, i)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragBy(key, amount.y)
                            },
                            onDragEnd = { endDrag(commit = true) },
                            onDragCancel = { endDrag(commit = false) },
                        )
                    },
            ) {
                SongTableRow(
                    trackTitle = track.title,
                    artist = track.artist,
                    duration = formatDuration(track.durationMs),
                    index = position + 1,
                    isNowPlaying = track.id == state.currentTrackId,
                    onClick = { if (dragKey == null) viewModel.onTrackClick(track, i) },
                    actions = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // The handle drags straight away, without the long press.
                            Icon(
                                imageVector = Icons.Outlined.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = if (dragging) CliBeatsAccent else CliBeatsTextSecondary,
                                modifier =
                                    Modifier
                                        .size(36.dp)
                                        .pointerInput(key) {
                                            detectDragGestures(
                                                onDragStart = { startDrag(key, i) },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragBy(key, amount.y)
                                                },
                                                onDragEnd = { endDrag(commit = true) },
                                                onDragCancel = { endDrag(commit = false) },
                                            )
                                        }.padding(8.dp),
                            )
                            IconButton(
                                onClick = { viewModel.onRemove(i) },
                                enabled = dragKey == null,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Remove from queue",
                                    tint = CliBeatsTextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

private const val AUTO_SCROLL_EDGE_DP = 48
