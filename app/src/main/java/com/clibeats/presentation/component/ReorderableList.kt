@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "MatchingDeclarationName")

package com.clibeats.presentation.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * What a row of [ReorderableList] gets: whether it's being dragged, the ≡ handle to show, and a modifier
 * that turns the album art into a hold-to-drag handle.
 */
class ReorderRowScope internal constructor(
    val isDragging: Boolean,
    val isAnyDragging: Boolean,
    private val handleModifier: Modifier,
    private val artModifier: Modifier,
) {
    /** The ≡ handle: dragging it moves the row straight away. */
    @Composable
    fun DragHandle() {
        Icon(
            imageVector = Icons.Outlined.DragHandle,
            contentDescription = "Drag to reorder",
            tint = if (isDragging) CliBeatsAccent else CliBeatsTextSecondary,
            // Near the screen edge the system back gesture would steal the drag.
            modifier = handleModifier.systemGestureExclusion().size(HANDLE_DP.dp).padding(HANDLE_PADDING_DP.dp),
        )
    }

    /** Hold the album art to pick the row up; a quick tap on it still runs [onTap] (play). */
    fun Modifier.artworkHandle(onTap: () -> Unit): Modifier = this.then(artModifier).claimPress(onTap)
}

/**
 * A list whose rows can be dragged into a new order: drag the ≡ handle, or hold the album art and drag.
 * Built on the Reorderable library, which moves items as the finger passes them, scrolls at the edges and
 * animates the others out of the way. The order changes on screen while dragging; [onReorder] is called
 * once when the finger lifts with where the row came from, where it went, and the whole new list.
 */
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
@Composable
fun <T> ReorderableList(
    items: List<T>,
    itemId: (T) -> String,
    onReorder: (from: Int, to: Int, reordered: List<T>) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Bump this when the whole list is about to be reordered (sort, shuffle): the list goes back to the top,
     * also once the new order arrives. Without it the list follows the row that was on screen to its new
     * place, which lands somewhere in the middle.
     */
    scrollToTopRequest: Int = 0,
    // [index] is the item's position in [items] (what a tap should play), not where it is mid-drag.
    row: @Composable ReorderRowScope.(item: T, index: Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // Stable keys, even when the same song appears twice (the queue allows that).
    val keyed = remember(items) { keyedItems(items, itemId) }
    val sourceIndex = remember(keyed) { keyed.withIndex().associate { (i, entry) -> entry.key to i } }
    var shown by remember { mutableStateOf(keyed) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()
    var topPending by remember { mutableStateOf(false) }

    // A reorder was asked for: go to the top now, and again when the new order arrives (below). If the
    // order doesn't change (already sorted), stop waiting so a later edit doesn't jump to the top.
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest == 0) return@LaunchedEffect
        topPending = true
        listState.scrollToItem(0)
        delay(TOP_PENDING_MS)
        topPending = false
    }

    // New data from the app (saved order, a song added or removed): show it, unless a row is in the air.
    LaunchedEffect(keyed) {
        if (draggingKey == null) {
            shown = keyed
            // Same frame as the new rows, so it wins over the "keep the visible row in view" adjustment.
            if (topPending) {
                listState.requestScrollToItem(0)
                topPending = false
            }
        }
    }

    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            val fromIndex = shown.indexOfFirst { it.key == from.key }
            val toIndex = shown.indexOfFirst { it.key == to.key }
            if (fromIndex >= 0 && toIndex >= 0) {
                shown = shown.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }

    fun onDragStarted(key: String) {
        draggingKey = key
        dragFrom = shown.indexOfFirst { it.key == key }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun onDragStopped() {
        val key = draggingKey ?: return
        draggingKey = null
        val to = shown.indexOfFirst { it.key == key }
        if (dragFrom >= 0 && to >= 0 && to != dragFrom) {
            onReorder(dragFrom, to, shown.map { it.item })
        } else {
            shown = keyed // dropped where it started: show the latest data
        }
        dragFrom = -1
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        items(shown, key = { it.key }) { entry ->
            ReorderableItem(reorderState, key = entry.key) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) DRAG_ELEVATION_DP.dp else 0.dp, label = "lift")
                val scope =
                    ReorderRowScope(
                        isDragging = isDragging,
                        isAnyDragging = draggingKey != null,
                        handleModifier =
                            Modifier
                                .draggableHandle(
                                    onDragStarted = { onDragStarted(entry.key) },
                                    onDragStopped = { onDragStopped() },
                                ).claimPress(onTap = null),
                        artModifier =
                            Modifier.longPressDraggableHandle(
                                onDragStarted = { onDragStarted(entry.key) },
                                onDragStopped = { onDragStopped() },
                            ),
                    )
                Box(modifier = Modifier.zIndex(if (isDragging) 1f else 0f).shadow(elevation)) {
                    scope.row(entry.item, sourceIndex[entry.key] ?: 0)
                }
            }
        }
    }
}

private class Keyed<T>(
    val key: String,
    val item: T,
)

private fun <T> keyedItems(
    items: List<T>,
    itemId: (T) -> String,
): List<Keyed<T>> {
    val seen = HashMap<String, Int>()
    return items.map { item ->
        val id = itemId(item)
        val n = seen.merge(id, 1, Int::plus) ?: 1
        Keyed("$id#$n", item)
    }
}

/**
 * Takes the press for this element so the row underneath doesn't also treat it as a tap (play) or a long
 * press (add to playlist). A quick tap runs [onTap]; a drag (claimed by the handle) or a hold runs nothing.
 * Must be the innermost pointer modifier on the element: touches reach it before the drag handle.
 */
private fun Modifier.claimPress(onTap: (() -> Unit)?): Modifier =
    pointerInput(onTap) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            val up = waitForUpOrCancellation() ?: return@awaitEachGesture
            if (onTap != null && up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                onTap()
            }
        }
    }

private const val HANDLE_DP = 36
private const val HANDLE_PADDING_DP = 8
private const val DRAG_ELEVATION_DP = 6
private const val TOP_PENDING_MS = 3_000L
