package com.eteditor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal fun coerceRuleDragOffset(
    value: Float,
    currentPosition: Int,
    itemCount: Int,
    stepPx: Float
): Float {
    if (itemCount <= 1 || stepPx <= 0f) return 0f
    val minOffset = -currentPosition.coerceAtLeast(0) * stepPx
    val maxOffset = ((itemCount - 1).coerceAtLeast(0) - currentPosition.coerceAtLeast(0)) * stepPx
    return value.coerceIn(minOffset, maxOffset)
}

internal fun ruleDragTargetPosition(
    currentPosition: Int,
    dragOffsetPx: Float,
    itemCount: Int,
    stepPx: Float
): Int {
    if (itemCount <= 1 || stepPx <= 0f) return currentPosition.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    val offset = (dragOffsetPx / stepPx).roundToInt()
    return (currentPosition + offset).coerceIn(0, (itemCount - 1).coerceAtLeast(0))
}

internal fun displacedRuleOffsetPx(
    position: Int,
    draggingPosition: Int?,
    targetPosition: Int?,
    stepPx: Float
): Float {
    val from = draggingPosition ?: return 0f
    val to = targetPosition ?: return 0f
    if (from == to || stepPx <= 0f || position == from) return 0f
    return when {
        to < from && position in to until from -> stepPx
        to > from && position in (from + 1)..to -> -stepPx
        else -> 0f
    }
}

internal fun <T> previewRuleListForDrag(
    items: List<T>,
    draggingPosition: Int?,
    targetPosition: Int?
): List<T> {
    val source = draggingPosition ?: return items
    val target = targetPosition ?: return items
    if (source !in items.indices || target !in items.indices || source == target) return items
    val reordered = items.toMutableList()
    val draggedItem = reordered.removeAt(source)
    reordered.add(target, draggedItem)
    return reordered
}

internal fun ruleDragVisualOffsetPx(
    sourcePosition: Int,
    displayPosition: Int,
    dragOffsetPx: Float,
    stepPx: Float
): Float {
    if (stepPx <= 0f) return dragOffsetPx
    return dragOffsetPx - ((displayPosition - sourcePosition) * stepPx)
}

internal fun ruleListScrollByDeltaForDragDelta(dragDeltaPx: Float): Float = dragDeltaPx

private data class RuleListAutoScrollState(
    val listState: LazyListState,
    val boundsInWindow: () -> Rect?
)

private val LocalRuleListAutoScrollState = staticCompositionLocalOf<RuleListAutoScrollState?> { null }

private data class RuleListDragHandleEntry(
    val rowKey: Any,
    val position: Int,
    val itemCount: Int,
    val reorderStepPx: Float,
    val boundsInWindow: Rect?,
    val onDragOffsetChange: (Float) -> Unit,
    val onDraggingChange: (Boolean) -> Unit,
    val onDragVisualChange: (Int?, Float) -> Unit,
    val onMove: (Int) -> Unit
)

private class RuleListDragCoordinator {
    private val entries = linkedMapOf<Any, RuleListDragHandleEntry>()

    fun upsert(entry: RuleListDragHandleEntry) {
        entries[entry.rowKey] = entry
    }

    fun remove(rowKey: Any) {
        entries.remove(rowKey)
    }

    fun hitTest(positionInWindow: Offset): RuleListDragHandleEntry? {
        return entries.values.lastOrNull { entry ->
            val bounds = entry.boundsInWindow ?: return@lastOrNull false
            entry.itemCount > 1 &&
                positionInWindow.x in bounds.left..bounds.right &&
                positionInWindow.y in bounds.top..bounds.bottom
        }
    }
}

private val LocalRuleListDragCoordinator = staticCompositionLocalOf<RuleListDragCoordinator?> { null }

@Composable
private fun CompactRuleListRow(
    rowKey: Any,
    index: Int,
    name: String,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    openSwipeRowKey: Any?,
    onOpenSwipeRowChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    onClick: (() -> Unit)? = null,
    dragOffsetPx: Float = 0f,
    displacedOffsetPx: Float = 0f,
    dragging: Boolean = false,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    var rowOffsetPx by remember(rowKey) { mutableStateOf(0f) }
    val actionWidth = if (onEdit != null) 72.dp else 40.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    LaunchedEffect(openSwipeRowKey, rowKey) {
        if (openSwipeRowKey != rowKey) rowOffsetPx = 0f
    }
    val rowColor by animateColorAsState(
        targetValue = if (dragging) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 90),
        label = "compactRuleRowDragColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (dragging) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
        },
        animationSpec = tween(durationMillis = 90),
        label = "compactRuleRowDragBorder"
    )
    val settleOffsetPx by animateFloatAsState(
        targetValue = if (dragging) 0f else displacedOffsetPx,
        animationSpec = tween(durationMillis = 90),
        label = "compactRuleRowSettleOffset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .then(if (dragging || displacedOffsetPx != 0f) Modifier else Modifier.clipToBounds())
    ) {
        Surface(
            shape = RowShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(actionWidth)
                .height(40.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (onEdit != null) {
                    IconButton(
                        onClick = {
                            rowOffsetPx = 0f
                            onOpenSwipeRowChange(null)
                            onEdit()
                        },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑规则", modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(
                    onClick = {
                        rowOffsetPx = 0f
                        onOpenSwipeRowChange(null)
                        onDelete()
                    },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除规则", modifier = Modifier.size(16.dp))
                }
            }
        }
        Surface(
            shape = PreviewShape,
            color = rowColor,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = if (dragging) 4.dp else 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(
                        rowOffsetPx.roundToInt(),
                        if (dragging) dragOffsetPx.roundToInt() else settleOffsetPx.roundToInt()
                    )
                }
                .then(
                    if (onClick != null) {
                        Modifier.clickable {
                            rowOffsetPx = 0f
                            onOpenSwipeRowChange(null)
                            onClick()
                        }
                    } else {
                        Modifier
                    }
                )
                .pointerInput(rowKey, actionWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            onOpenSwipeRowChange(rowKey)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            rowOffsetPx = (rowOffsetPx + dragAmount).coerceIn(-actionWidthPx, 0f)
                        },
                        onDragEnd = {
                            val nextOffset = if (rowOffsetPx < -actionWidthPx * 0.45f) -actionWidthPx else 0f
                            rowOffsetPx = nextOffset
                            onOpenSwipeRowChange(if (nextOffset < 0f) rowKey else null)
                        },
                        onDragCancel = {
                            val nextOffset = if (rowOffsetPx < -actionWidthPx * 0.45f) -actionWidthPx else 0f
                            rowOffsetPx = nextOffset
                            onOpenSwipeRowChange(if (nextOffset < 0f) rowKey else null)
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier.padding(start = 6.dp, top = 5.dp, end = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                leadingContent?.invoke(this)
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(22.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = name.ifBlank { "未命名" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                trailingContent?.invoke(this)
            }
        }
    }
}

internal data class RuleListDragContext(
    val position: Int,
    val itemCount: Int,
    val reorderStepPx: Float,
    val dragOffsetPx: Float,
    val dragging: Boolean,
    val reorderEnabled: Boolean,
    val displacedOffsetPx: Float,
    val onDragOffsetChange: (Float) -> Unit,
    val onDraggingChange: (Boolean) -> Unit,
    val onDragVisualChange: (Int?, Float) -> Unit,
    val openSwipeRowKey: Any?,
    val onOpenSwipeRowChange: (Any?) -> Unit
)

@Composable
internal fun <T> UnifiedRuleList(
    items: List<T>,
    emptyText: String,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    itemCountForScrollbar: Int = items.size,
    openSwipeRowKey: Any? = null,
    onOpenSwipeRowChange: ((Any?) -> Unit)? = null,
    sortMode: Boolean = true,
    hideScrollbarWhileSorting: Boolean = false,
    lockUserScrollWhileSorting: Boolean = false,
    rowContent: @Composable (T, Int, RuleListDragContext) -> Unit
) {
    val density = LocalDensity.current
    val reorderStepPx = with(density) { 48.dp.toPx() }
    var draggingRowKey by remember { mutableStateOf<Any?>(null) }
    var draggingPosition by remember { mutableStateOf<Int?>(null) }
    var draggingOffsetPx by remember { mutableStateOf(0f) }
    var ownedOpenSwipeRowKey by remember { mutableStateOf<Any?>(null) }
    val usesSharedSwipeState = onOpenSwipeRowChange != null
    val effectiveOpenSwipeRowKey = if (usesSharedSwipeState) openSwipeRowKey else ownedOpenSwipeRowKey
    val updateOpenSwipeRowKey = onOpenSwipeRowChange ?: { nextKey: Any? -> ownedOpenSwipeRowKey = nextKey }
    val dragTargetPosition = draggingPosition?.let { position ->
        ruleDragTargetPosition(position, draggingOffsetPx, items.size, reorderStepPx)
    }
    val previewItems = previewRuleListForDrag(
        items = items,
        draggingPosition = draggingPosition.takeIf { draggingRowKey != null },
        targetPosition = dragTargetPosition
    )
    if (!usesSharedSwipeState) {
        LaunchedEffect(items, effectiveOpenSwipeRowKey) {
            val openedKey = effectiveOpenSwipeRowKey ?: return@LaunchedEffect
            if (items.none { item -> itemKey(item) == openedKey }) {
                updateOpenSwipeRowKey(null)
            }
        }
    }
    LaunchedEffect(items, draggingRowKey) {
        val draggedKey = draggingRowKey ?: return@LaunchedEffect
        if (items.none { item -> itemKey(item) == draggedKey }) {
            draggingRowKey = null
            draggingPosition = null
            draggingOffsetPx = 0f
        }
    }

    UnifiedRuleListFrame(
        modifier = modifier,
        itemCount = itemCountForScrollbar.takeIf { items.isNotEmpty() } ?: 0,
        showScrollbar = draggingRowKey == null && !(hideScrollbarWhileSorting && sortMode),
        userScrollEnabled = draggingRowKey == null && !(lockUserScrollWhileSorting && sortMode)
    ) {
        if (items.isEmpty()) {
            item("empty") {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            itemsIndexed(previewItems, key = { _, item -> itemKey(item) }) { actualPosition, item ->
                val rowKey = itemKey(item)
                val rowDragging = draggingRowKey == rowKey
                val rowSourcePosition = if (rowDragging) {
                    draggingPosition ?: actualPosition
                } else {
                    actualPosition
                }
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowContent(
                        item,
                        actualPosition,
                        RuleListDragContext(
                            position = rowSourcePosition,
                            itemCount = items.size,
                            reorderStepPx = reorderStepPx,
                            dragOffsetPx = if (rowDragging) {
                                ruleDragVisualOffsetPx(
                                    sourcePosition = rowSourcePosition,
                                    displayPosition = actualPosition,
                                    dragOffsetPx = draggingOffsetPx,
                                    stepPx = reorderStepPx
                                )
                            } else {
                                0f
                            },
                            dragging = rowDragging,
                            reorderEnabled = sortMode,
                            displacedOffsetPx = 0f,
                            onDragOffsetChange = { offset ->
                                draggingRowKey = rowKey
                                draggingPosition = rowSourcePosition
                                draggingOffsetPx = offset
                            },
                            onDraggingChange = { isDragging ->
                                if (isDragging) {
                                    draggingRowKey = rowKey
                                    draggingPosition = rowSourcePosition
                                    draggingOffsetPx = 0f
                                } else if (draggingRowKey == rowKey) {
                                    draggingRowKey = null
                                    draggingPosition = null
                                    draggingOffsetPx = 0f
                                }
                            },
                            onDragVisualChange = { nextPosition, offset ->
                                if (nextPosition != null) {
                                    draggingRowKey = rowKey
                                    draggingPosition = rowSourcePosition
                                    draggingOffsetPx = offset
                                } else if (draggingRowKey == rowKey) {
                                    draggingRowKey = null
                                    draggingPosition = null
                                    draggingOffsetPx = 0f
                                }
                            },
                            openSwipeRowKey = effectiveOpenSwipeRowKey,
                            onOpenSwipeRowChange = updateOpenSwipeRowKey
                        )
                    )
                }
            }
        }
    }
}

@Composable
internal fun UnifiedRuleListFrame(
    modifier: Modifier = Modifier,
    itemCount: Int,
    showScrollbar: Boolean = true,
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    val listState = rememberLazyListState()
    var listBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
    val autoScrollState = remember(listState) {
        RuleListAutoScrollState(
            listState = listState,
            boundsInWindow = { listBoundsInWindow }
        )
    }
    val dragCoordinator = remember { RuleListDragCoordinator() }
    val latestAutoScrollState = rememberUpdatedState(autoScrollState)
    val edgeAutoScrollPx = with(LocalDensity.current) { 64.dp.toPx() }
    Box(
        modifier = modifier
            .pointerInput(dragCoordinator, edgeAutoScrollPx) {
                coroutineScope {
                    val gestureScope = this
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val listBounds = latestAutoScrollState.value.boundsInWindow()
                        val downInWindow = listBounds?.let { bounds ->
                            Offset(bounds.left + down.position.x, bounds.top + down.position.y)
                        }
                        val dragEntry = downInWindow?.let(dragCoordinator::hitTest)
                            ?: return@awaitEachGesture
                        down.consume()

                        var gestureDragOffsetPx = 0f
                        var autoScrollPerFramePx = 0f
                        var draggingGestureActive = true
                        var autoScrollJob: Job? = null
                        var pointerYInWindow = downInWindow.y
                        val pointerId: PointerId = down.id

                        fun applyDragDelta(delta: Float) {
                            val nextOffset = gestureDragOffsetPx + delta
                            if (nextOffset == gestureDragOffsetPx) return
                            gestureDragOffsetPx = nextOffset
                            dragEntry.onDragOffsetChange(nextOffset)
                            dragEntry.onDragVisualChange(dragEntry.position, nextOffset)
                        }

                        fun stopAutoScroll() {
                            autoScrollPerFramePx = 0f
                            autoScrollJob?.cancel()
                            autoScrollJob = null
                        }

                        fun updateAutoScroll() {
                            autoScrollPerFramePx = ruleListAutoScrollPerFrame(
                                state = latestAutoScrollState.value,
                                pointerYInWindow = pointerYInWindow,
                                edgePx = edgeAutoScrollPx,
                                maxScrollPerFramePx = dragEntry.reorderStepPx * 0.18f
                            )
                        }

                        fun clearDrag() {
                            dragEntry.onDraggingChange(false)
                            dragEntry.onDragOffsetChange(0f)
                            dragEntry.onDragVisualChange(null, 0f)
                        }

                        fun finishDrag() {
                            if (!draggingGestureActive) return
                            draggingGestureActive = false
                            stopAutoScroll()
                            val drag = coerceRuleDragOffset(
                                gestureDragOffsetPx,
                                dragEntry.position,
                                dragEntry.itemCount,
                                dragEntry.reorderStepPx
                            )
                            val targetPosition = ruleDragTargetPosition(
                                dragEntry.position,
                                drag,
                                dragEntry.itemCount,
                                dragEntry.reorderStepPx
                            )
                            if (targetPosition != dragEntry.position) {
                                dragEntry.onMove(targetPosition)
                            }
                            clearDrag()
                        }

                        fun cancelDrag() {
                            if (!draggingGestureActive) return
                            draggingGestureActive = false
                            stopAutoScroll()
                            clearDrag()
                        }

                        dragEntry.onDraggingChange(true)
                        dragEntry.onDragOffsetChange(0f)
                        dragEntry.onDragVisualChange(dragEntry.position, 0f)
                        updateAutoScroll()
                        autoScrollJob = gestureScope.launch {
                            while (isActive) {
                                val scroll = autoScrollPerFramePx
                                if (scroll != 0f) {
                                    val consumed = latestAutoScrollState
                                        .value
                                        .listState
                                        .dispatchRawDelta(ruleListScrollByDeltaForDragDelta(scroll))
                                    applyDragDelta(consumed)
                                }
                                delay(16)
                            }
                        }

                        try {
                            while (draggingGestureActive) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull { it.pressed }
                                if (change == null || !change.pressed) {
                                    finishDrag()
                                    break
                                }
                                val dragAmount = change.positionChange().y
                                if (dragAmount != 0f) {
                                    change.consume()
                                    pointerYInWindow += dragAmount
                                    applyDragDelta(dragAmount)
                                }
                                updateAutoScroll()
                            }
                        } finally {
                            cancelDrag()
                        }
                    }
                }
            }
    ) {
        CompositionLocalProvider(
            LocalRuleListAutoScrollState provides autoScrollState,
            LocalRuleListDragCoordinator provides dragCoordinator
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 10.dp)
                    .onGloballyPositioned { coordinates ->
                        listBoundsInWindow = coordinates.boundsInWindow()
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = userScrollEnabled,
                content = content
            )
        }
        if (showScrollbar) {
            ContentScrollbar(
                state = listState,
                itemCount = itemCount,
                fixedItemHeight = 48.dp,
                directDrag = true,
                thumbFollowsDrag = true,
                hitWidth = 10.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
internal fun DraggableCompactRuleListRow(
    rowKey: Any,
    index: Int,
    name: String,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    position: Int,
    itemCount: Int,
    reorderStepPx: Float,
    displacedOffsetPx: Float,
    onMove: (Int) -> Unit,
    onDragVisualChange: (Int?, Float) -> Unit,
    dragOffsetPx: Float? = null,
    dragging: Boolean? = null,
    onDragOffsetChange: ((Float) -> Unit)? = null,
    onDraggingChange: ((Boolean) -> Unit)? = null,
    openSwipeRowKey: Any?,
    onOpenSwipeRowChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    onClick: (() -> Unit)? = null,
    showDragHandle: Boolean = true,
    leadingContent: (@Composable RowScope.() -> Unit)? = null
) {
    var ownedDragOffset by remember(rowKey, itemCount) { mutableStateOf(0f) }
    var ownedDragging by remember(rowKey, itemCount) { mutableStateOf(false) }
    val effectiveDragOffset = dragOffsetPx ?: ownedDragOffset
    val effectiveDragging = dragging ?: ownedDragging
    val updateDragOffset = onDragOffsetChange ?: { offset: Float -> ownedDragOffset = offset }
    val updateDragging = onDraggingChange ?: { isDragging: Boolean -> ownedDragging = isDragging }
    CompactRuleListRow(
        rowKey = rowKey,
        index = index,
        name = name,
        subtitle = subtitle,
        onEdit = onEdit,
        onDelete = onDelete,
        openSwipeRowKey = openSwipeRowKey,
        onOpenSwipeRowChange = onOpenSwipeRowChange,
        modifier = modifier,
        dragOffsetPx = effectiveDragOffset,
        displacedOffsetPx = displacedOffsetPx,
        dragging = effectiveDragging,
        onClick = onClick,
        leadingContent = leadingContent,
        trailingContent = {
            if (showDragHandle) {
                RuleListDragHandle(
                    rowKey = rowKey,
                    position = position,
                    itemCount = itemCount,
                    reorderStepPx = reorderStepPx,
                    dragging = effectiveDragging,
                    onDragOffsetChange = updateDragOffset,
                    onDraggingChange = updateDragging,
                    onDragVisualChange = onDragVisualChange,
                    onMove = onMove
                )
            }
        }
    )
}

@Composable
internal fun RuleListDragHandle(
    rowKey: Any,
    position: Int,
    itemCount: Int,
    reorderStepPx: Float,
    dragging: Boolean,
    onDragOffsetChange: (Float) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    onDragVisualChange: (Int?, Float) -> Unit,
    onMove: (Int) -> Unit
) {
    val autoScrollState = LocalRuleListAutoScrollState.current
    val latestAutoScrollState = rememberUpdatedState(autoScrollState)
    val latestOnDragOffsetChange = rememberUpdatedState(onDragOffsetChange)
    val latestOnDraggingChange = rememberUpdatedState(onDraggingChange)
    val latestOnDragVisualChange = rememberUpdatedState(onDragVisualChange)
    val latestOnMove = rememberUpdatedState(onMove)
    var handleBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
    val latestHandleBoundsInWindow = rememberUpdatedState(handleBoundsInWindow)
    val edgeAutoScrollPx = with(LocalDensity.current) { 64.dp.toPx() }
    val dragCoordinator = LocalRuleListDragCoordinator.current
    SideEffect {
        dragCoordinator?.upsert(
            RuleListDragHandleEntry(
                rowKey = rowKey,
                position = position,
                itemCount = itemCount,
                reorderStepPx = reorderStepPx,
                boundsInWindow = handleBoundsInWindow,
                onDragOffsetChange = onDragOffsetChange,
                onDraggingChange = onDraggingChange,
                onDragVisualChange = onDragVisualChange,
                onMove = onMove
            )
        )
    }
    DisposableEffect(dragCoordinator, rowKey) {
        onDispose {
            dragCoordinator?.remove(rowKey)
        }
    }
    val fallbackDragModifier = if (dragCoordinator == null) {
        Modifier.pointerInput(rowKey, position, itemCount, reorderStepPx) {
                coroutineScope {
                    var gestureDragOffsetPx = 0f
                    var autoScrollPerFramePx = 0f
                    var draggingGestureActive = false
                    var autoScrollJob: Job? = null
                    var pointerYInWindow: Float? = null

                    fun applyDragDelta(delta: Float) {
                        val nextOffset = gestureDragOffsetPx + delta
                        if (nextOffset == gestureDragOffsetPx) return
                        gestureDragOffsetPx = nextOffset
                        latestOnDragOffsetChange.value(nextOffset)
                        latestOnDragVisualChange.value(position, nextOffset)
                    }

                    fun stopAutoScroll() {
                        autoScrollPerFramePx = 0f
                        autoScrollJob?.cancel()
                        autoScrollJob = null
                    }

                    fun updateAutoScroll() {
                        val state = latestAutoScrollState.value
                        val pointerY = pointerYInWindow
                        autoScrollPerFramePx = if (state != null && pointerY != null) {
                            ruleListAutoScrollPerFrame(
                                state = state,
                                pointerYInWindow = pointerY,
                                edgePx = edgeAutoScrollPx,
                                maxScrollPerFramePx = reorderStepPx * 0.18f
                            )
                        } else {
                            0f
                        }
                    }

                    fun clearDrag() {
                        gestureDragOffsetPx = 0f
                        pointerYInWindow = null
                        latestOnDraggingChange.value(false)
                        latestOnDragOffsetChange.value(0f)
                        latestOnDragVisualChange.value(null, 0f)
                    }

                    fun finishDrag() {
                        if (!draggingGestureActive) return
                        draggingGestureActive = false
                        stopAutoScroll()
                        val drag = coerceRuleDragOffset(
                            gestureDragOffsetPx,
                            position,
                            itemCount,
                            reorderStepPx
                        )
                        val targetPosition = ruleDragTargetPosition(
                            position,
                            drag,
                            itemCount,
                            reorderStepPx
                        )
                        if (targetPosition != position) {
                            latestOnMove.value(targetPosition)
                        }
                        clearDrag()
                    }

                    fun cancelDrag() {
                        if (!draggingGestureActive) return
                        draggingGestureActive = false
                        stopAutoScroll()
                        clearDrag()
                    }

                    fun startDrag() {
                        draggingGestureActive = true
                        gestureDragOffsetPx = 0f
                        autoScrollPerFramePx = 0f
                        latestOnDraggingChange.value(true)
                        latestOnDragOffsetChange.value(0f)
                        latestOnDragVisualChange.value(position, 0f)
                        stopAutoScroll()
                        autoScrollJob = launch {
                            while (isActive) {
                                val scroll = autoScrollPerFramePx
                                val state = latestAutoScrollState.value
                                if (scroll != 0f && state != null) {
                                    val consumed = state
                                        .listState
                                        .dispatchRawDelta(ruleListScrollByDeltaForDragDelta(scroll))
                                    applyDragDelta(consumed)
                                }
                                delay(16)
                            }
                        }
                    }

                    detectVerticalDragGestures(
                        onDragStart = { startOffset ->
                            pointerYInWindow = latestHandleBoundsInWindow.value
                                ?.let { bounds -> bounds.top + startOffset.y }
                            startDrag()
                            updateAutoScroll()
                        },
                        onDragEnd = {
                            finishDrag()
                        },
                        onDragCancel = {
                            cancelDrag()
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            pointerYInWindow = pointerYInWindow
                                ?.plus(dragAmount)
                                ?: latestHandleBoundsInWindow.value
                                    ?.let { bounds -> bounds.top + change.position.y }
                            if (dragAmount != 0f) {
                                applyDragDelta(dragAmount)
                            }
                            updateAutoScroll()
                        }
                    )
                }
            }
    } else {
        Modifier
    }
    Surface(
        shape = ControlShape,
        color = if (dragging) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (dragging) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)),
        modifier = Modifier
            .size(width = 38.dp, height = 30.dp)
            .onGloballyPositioned { coordinates ->
                handleBoundsInWindow = coordinates.boundsInWindow()
            }
            .then(fallbackDragModifier)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.DragHandle,
                contentDescription = "拖动排序",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun ruleListAutoScrollPerFrame(
    state: RuleListAutoScrollState?,
    pointerYInWindow: Float,
    edgePx: Float,
    maxScrollPerFramePx: Float
): Float {
    val listBounds = state?.boundsInWindow() ?: return 0f
    if (listBounds.height <= 0f || edgePx <= 0f || maxScrollPerFramePx <= 0f) return 0f
    val effectiveEdge = edgePx.coerceAtMost(listBounds.height / 2f)
    return when {
        pointerYInWindow < listBounds.top + effectiveEdge -> {
            val strength = ((listBounds.top + effectiveEdge - pointerYInWindow) / effectiveEdge).coerceIn(0f, 1f)
            -maxScrollPerFramePx * strength
        }
        pointerYInWindow > listBounds.bottom - effectiveEdge -> {
            val strength = ((pointerYInWindow - (listBounds.bottom - effectiveEdge)) / effectiveEdge).coerceIn(0f, 1f)
            maxScrollPerFramePx * strength
        }
        else -> 0f
    }
}
