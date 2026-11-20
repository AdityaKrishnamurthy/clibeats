@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "MatchingDeclarationName")

package com.clibeats.presentation.component

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.clibeats.presentation.theme.CliBeatsAccent
import com.clibeats.presentation.theme.CliBeatsTextSecondary

/** What a row of [ReorderableList] gets: whether it's being dragged, and the ≡ handle to show. */
class ReorderRowScope internal constructor(
    val isDragging: Boolean,
    val isAnyDragging: Boolean,
    val dragHandle: @Composable () -> Unit,
)

/**
 * A list whose rows can be dragged into a new order with their ≡ handle (and, with [holdToDrag], by holding
 * the row anywhere). The order changes on screen while dragging and [onReorder] gets the whole new list
 * once, when the finger lifts. The list keeps that order until [items] changes (the saved order coming back).
 */
@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
@Composable
fun <T> ReorderableList(
    items: List<T>,
    itemId: (T) -> String,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    holdToDrag: Boolean = false,
    // [index] is the item's position in [items] (what a tap should play), not where it is mid-drag.
    row: @Composable ReorderRowScope.(item: T, index: Int) -> Unit,
) {
    // Stable keys that survive reordering, even when the same item appears twice.
    val keys =
        remember(items) {
            val seen = HashMap<String, Int>()
            items.map { item ->
                val id = itemId(item)
                val n = seen.merge(id, 1, Int::plus) ?: 1
                "$id#$n"
            }
        }
    var order by remember(items) { mutableStateOf(items.indices.toList()) }
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
            onReorder(order.map { items[it] })
        } else {
            order = items.indices.toList()
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

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        itemsIndexed(items = order, key = { _, i -> keys[i] }) { _, i ->
            val key = keys[i]
            val dragging = key == dragKey
            val rowModifier =
                if (dragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer {
                            translationY = dragOffset
                            shadowElevation = DRAG_ELEVATION
                        }
                } else {
                    Modifier.animateItem()
                }
            Box(
                modifier =
                    if (holdToDrag) {
                        rowModifier.pointerInput(key) {
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
                        }
                    } else {
                        rowModifier
                    },
            ) {
                val handle: @Composable () -> Unit = {
                    // The handle drags straight away, without a long press.
                    Icon(
                        imageVector = Icons.Outlined.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = if (dragging) CliBeatsAccent else CliBeatsTextSecondary,
                        modifier =
                            Modifier
                                .size(36.dp)
                                .pointerInput(key) {
                                    detectDragGestures(
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
                                }.padding(8.dp),
                    )
                }
                ReorderRowScope(isDragging = dragging, isAnyDragging = dragKey != null, dragHandle = handle)
                    .row(items[i], i)
            }
        }
    }
}

private const val AUTO_SCROLL_EDGE_DP = 56
private const val DRAG_ELEVATION = 8f
