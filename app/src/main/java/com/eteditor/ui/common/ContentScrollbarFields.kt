package com.eteditor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ContentScrollbar(
    state: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
    prominent: Boolean = true,
    fixedItemHeight: Dp? = null,
    directDrag: Boolean = false,
    thumbFollowsDrag: Boolean = false,
    hitWidth: Dp = if (prominent) 18.dp else 14.dp,
    trackWidth: Dp = if (prominent) 7.dp else 3.dp,
    thumbWidth: Dp = if (prominent) 10.dp else 6.dp,
    draggingThumbWidth: Dp = 12.dp
) {
    val layoutInfo = state.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val firstVisibleItem = visibleItems.firstOrNull()
    val lastVisibleItem = visibleItems.lastOrNull()
    val effectiveItemCount = itemCount.coerceAtLeast(layoutInfo.totalItemsCount)
    val canScrollBackward = state.canScrollBackward
    val canScrollForward = state.canScrollForward
    val hasScrollableContent = canScrollBackward ||
        canScrollForward ||
        lastVisibleItem == null ||
        lastVisibleItem.index < effectiveItemCount - 1 ||
        lastVisibleItem.offset + lastVisibleItem.size > layoutInfo.viewportEndOffset
    if (effectiveItemCount <= 0 || visibleItems.isEmpty() || !hasScrollableContent) return

    val density = LocalDensity.current
    val viewportHeightPx = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
        .toFloat()
        .coerceAtLeast(1f)
    val fixedItemSizePx = fixedItemHeight?.let { with(density) { it.toPx() } }?.coerceAtLeast(1f)
    val visibleItemCount = visibleItems.size.coerceAtLeast(1)
    val contentHeightPx = if (fixedItemSizePx != null) {
        fixedItemSizePx * effectiveItemCount
    } else {
        viewportHeightPx * effectiveItemCount / visibleItemCount.toFloat()
    }.coerceAtLeast(viewportHeightPx + 1f)
    val estimatedVisibleItems = if (fixedItemSizePx != null) {
        viewportHeightPx / fixedItemSizePx
    } else {
        visibleItemCount.toFloat()
    }.coerceAtLeast(1f)
    val maxItemPosition = (effectiveItemCount - estimatedVisibleItems).coerceAtLeast(1f)
    val firstVisibleSize = firstVisibleItem?.size?.toFloat()?.coerceAtLeast(1f) ?: fixedItemSizePx ?: 1f
    val currentItemPosition = (
        state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / firstVisibleSize
    ).coerceIn(0f, maxItemPosition)
    val reachedStart = !canScrollBackward
    val reachedEnd = !canScrollForward
    val scrollProgress = when {
        reachedStart -> 0f
        reachedEnd -> 1f
        else -> (currentItemPosition / maxItemPosition).coerceIn(0f, 1f)
    }
    val scope = rememberCoroutineScope()
    var directDragJob by remember { mutableStateOf<Job?>(null) }
    ContentScrollbarTrack(
        modifier = modifier,
        prominent = prominent,
        scrollProgress = scrollProgress,
        contentHeightPx = { trackHeightPx -> contentHeightPx.coerceAtLeast(trackHeightPx + 1f) },
        dragKey = effectiveItemCount,
        thumbFollowsDrag = thumbFollowsDrag,
        hitWidth = hitWidth,
        trackWidth = trackWidth,
        thumbWidth = thumbWidth,
        draggingThumbWidth = draggingThumbWidth,
        onDrag = { dragAmount, trackRangePx, targetProgress ->
            if (directDrag) {
                val targetPosition = (targetProgress.coerceIn(0f, 1f) * maxItemPosition)
                    .coerceIn(0f, maxItemPosition)
                val targetIndex = targetPosition.toInt().coerceIn(0, (effectiveItemCount - 1).coerceAtLeast(0))
                val targetOffset = ((targetPosition - targetIndex) * (fixedItemSizePx ?: firstVisibleSize))
                    .roundToInt()
                    .coerceAtLeast(0)
                directDragJob?.cancel()
                directDragJob = scope.launch {
                    state.scrollToItem(targetIndex, targetOffset)
                }
            } else {
                when {
                    targetProgress <= 0.001f -> {
                        directDragJob?.cancel()
                        directDragJob = scope.launch {
                            state.scrollToItem(0)
                        }
                    }
                    targetProgress >= 0.999f -> {
                        directDragJob?.cancel()
                        directDragJob = scope.launch {
                            state.scrollToItem((effectiveItemCount - 1).coerceAtLeast(0))
                        }
                    }
                    else -> {
                        val maxScrollPx = (contentHeightPx - viewportHeightPx).coerceAtLeast(1f)
                        state.dispatchRawDelta(dragAmount / trackRangePx.coerceAtLeast(1f) * maxScrollPx)
                    }
                }
            }
        }
    )
}

@Composable
fun ContentScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
    prominent: Boolean = true
) {
    val maxScroll = state.maxValue
    if (maxScroll <= 0) return

    ContentScrollbarTrack(
        modifier = modifier,
        prominent = prominent,
        scrollProgress = (state.value / maxScroll.toFloat()).coerceIn(0f, 1f),
        contentHeightPx = { viewportHeightPx -> viewportHeightPx + maxScroll.toFloat() },
        dragKey = maxScroll,
        onDrag = { dragAmount, trackRangePx, _ ->
            state.dispatchRawDelta(dragAmount / trackRangePx.coerceAtLeast(1f) * maxScroll.toFloat())
        }
    )
}

@Composable
private fun ContentScrollbarTrack(
    modifier: Modifier,
    prominent: Boolean,
    scrollProgress: Float,
    contentHeightPx: (viewportHeightPx: Float) -> Float,
    dragKey: Any,
    thumbFollowsDrag: Boolean = true,
    hitWidth: Dp = if (prominent) 18.dp else 14.dp,
    trackWidth: Dp = if (prominent) 7.dp else 3.dp,
    thumbWidth: Dp = if (prominent) 10.dp else 6.dp,
    draggingThumbWidth: Dp = 12.dp,
    onDrag: (dragAmountPx: Float, trackRangePx: Float, targetProgress: Float) -> Unit
) {
    val density = LocalDensity.current
    var trackHeightPx by remember { mutableStateOf(0) }
    var dragThumbOffsetPx by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val minThumbHeightPx = with(density) { 32.dp.toPx() }
    val viewportHeightPx = trackHeightPx.toFloat().coerceAtLeast(1f)
    val totalContentHeightPx = contentHeightPx(viewportHeightPx).coerceAtLeast(viewportHeightPx + 1f)
    val thumbHeightPx = if (trackHeightPx > 0) {
        (viewportHeightPx * viewportHeightPx / totalContentHeightPx).coerceIn(minThumbHeightPx, viewportHeightPx)
    } else {
        0f
    }
    val trackRangePx = (viewportHeightPx - thumbHeightPx).coerceAtLeast(1f)
    val stateThumbOffsetPx = (trackRangePx * scrollProgress.coerceIn(0f, 1f)).coerceIn(0f, trackRangePx)
    val latestTrackRangePx by rememberUpdatedState(trackRangePx)
    val latestThumbHeightPx by rememberUpdatedState(thumbHeightPx)
    val latestStateThumbOffsetPx by rememberUpdatedState(stateThumbOffsetPx)
    val latestThumbFollowsDrag by rememberUpdatedState(thumbFollowsDrag)
    val latestOnDrag by rememberUpdatedState(onDrag)
    val thumbOffsetPx = if (dragging && thumbFollowsDrag) {
        dragThumbOffsetPx.coerceIn(0f, trackRangePx)
    } else {
        stateThumbOffsetPx
    }
    Box(
        modifier = modifier
            .width(hitWidth)
            .onSizeChanged { trackHeightPx = it.height }
            .pointerInput(trackHeightPx, dragKey, thumbFollowsDrag) {
                if (trackHeightPx <= 0 || thumbHeightPx <= 0f) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { startOffset ->
                        dragging = true
                        dragThumbOffsetPx = if (latestThumbFollowsDrag) {
                            (startOffset.y - latestThumbHeightPx / 2f).coerceIn(0f, latestTrackRangePx)
                        } else {
                            latestStateThumbOffsetPx
                        }
                        if (latestThumbFollowsDrag) {
                            val targetProgress = (dragThumbOffsetPx / latestTrackRangePx.coerceAtLeast(1f))
                                .coerceIn(0f, 1f)
                            latestOnDrag(0f, latestTrackRangePx, targetProgress)
                        }
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragThumbOffsetPx = if (latestThumbFollowsDrag) {
                            (dragThumbOffsetPx + dragAmount).coerceIn(0f, latestTrackRangePx)
                        } else {
                            latestStateThumbOffsetPx
                        }
                        val targetProgress = (dragThumbOffsetPx / latestTrackRangePx.coerceAtLeast(1f))
                            .coerceIn(0f, 1f)
                        latestOnDrag(dragAmount, latestTrackRangePx, targetProgress)
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(trackWidth)
                .background(
                    MaterialTheme.colorScheme.outlineVariant.copy(
                        alpha = when {
                            dragging -> 0.9f
                            prominent -> 0.72f
                            else -> 0.45f
                        }
                    ),
                    RoundedCornerShape(999.dp)
                )
        )
        if (trackHeightPx > 0 && thumbHeightPx > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                    .width(hitWidth)
                    .height(with(density) { thumbHeightPx.toDp() })
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight()
                        .width(if (dragging) draggingThumbWidth else thumbWidth)
                        .background(
                            if (dragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (prominent) 0.78f else 0.48f),
                            RoundedCornerShape(999.dp)
                        )
                )
            }
        }
    }
}
