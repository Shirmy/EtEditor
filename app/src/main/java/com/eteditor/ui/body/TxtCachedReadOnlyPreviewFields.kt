package com.eteditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex

@Composable
internal fun TxtCachedReadOnlyPreview(
    mode: String,
    fullText: String,
    fullContentKey: Any,
    fullPositionKey: Any,
    fullHighlightRange: Pair<Int, Int>?,
    fullScrollTargetOffset: Int?,
    fullScrollTargetLineIndex: Int?,
    chapterText: String,
    chapterContentKey: Any,
    chapterPositionKey: Any,
    chapterHighlightRange: Pair<Int, Int>?,
    onDoubleTap: ((Int) -> Unit)?,
    onFullLongPressLine: ((Int) -> Unit)?,
    onChapterLongPressLine: ((Int) -> Unit)?,
    onFullDeleteSelection: ((Int, Int) -> Unit)?,
    onChapterDeleteSelection: ((Int, Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val fullVisible = mode == TXT_PREVIEW_MODE_FULL
    val density = LocalDensity.current
    var hostWidthPx by remember { mutableStateOf(0) }
    var cachedFullWidthPx by remember { mutableStateOf(0) }
    var cachedChapterWidthPx by remember { mutableStateOf(0) }
    var cachedChapterText by remember { mutableStateOf(chapterText) }
    var cachedChapterContentKey by remember { mutableStateOf(chapterContentKey) }
    var cachedChapterPositionKey by remember { mutableStateOf(chapterPositionKey) }
    var cachedChapterHighlightRange by remember { mutableStateOf(chapterHighlightRange) }
    LaunchedEffect(fullVisible, hostWidthPx) {
        if (hostWidthPx > 0) {
            if (fullVisible) {
                cachedFullWidthPx = hostWidthPx
            } else {
                cachedChapterWidthPx = hostWidthPx
            }
        }
    }
    LaunchedEffect(
        fullVisible,
        chapterText,
        chapterContentKey,
        chapterPositionKey,
        chapterHighlightRange
    ) {
        if (!fullVisible) {
            cachedChapterText = chapterText
            cachedChapterContentKey = chapterContentKey
            cachedChapterPositionKey = chapterPositionKey
            cachedChapterHighlightRange = chapterHighlightRange
        }
    }
    val activeChapterText = if (fullVisible) cachedChapterText else chapterText
    val activeChapterContentKey = if (fullVisible) cachedChapterContentKey else chapterContentKey
    val activeChapterPositionKey = if (fullVisible) cachedChapterPositionKey else chapterPositionKey
    val activeChapterHighlightRange = if (fullVisible) cachedChapterHighlightRange else chapterHighlightRange
    val fullLayerModifier = if (fullVisible || cachedFullWidthPx <= 0) {
        Modifier.fillMaxSize()
    } else {
        Modifier
            .width(with(density) { cachedFullWidthPx.toDp() })
            .fillMaxHeight()
    }
    val chapterLayerModifier = if (!fullVisible || cachedChapterWidthPx <= 0) {
        Modifier.fillMaxSize()
    } else {
        Modifier
            .width(with(density) { cachedChapterWidthPx.toDp() })
            .fillMaxHeight()
    }
    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { hostWidthPx = it.width }
    ) {
        LargeBodyReadOnlyPreview(
            text = fullText,
            contentKey = fullContentKey,
            positionKey = if (fullVisible) fullPositionKey else "txt-full-idle",
            highlightRange = if (fullVisible) fullHighlightRange else null,
            scrollTargetOffset = if (fullVisible) fullScrollTargetOffset else null,
            scrollTargetLineIndex = if (fullVisible) fullScrollTargetLineIndex else null,
            interactive = fullVisible,
            showLoading = fullVisible,
            onDoubleTap = if (fullVisible) onDoubleTap else null,
            onLongPressLine = if (fullVisible) onFullLongPressLine else null,
            selectionActions = if (fullVisible && onFullDeleteSelection != null) {
                listOf(BodyReadOnlySelectionAction("删除", onFullDeleteSelection))
            } else {
                emptyList()
            },
            modifier = fullLayerModifier
                .alpha(if (fullVisible) 1f else 0f)
                .zIndex(if (fullVisible) 1f else 0f)
                .clipToBounds()
        )
        LargeBodyReadOnlyPreview(
            text = activeChapterText,
            contentKey = activeChapterContentKey,
            positionKey = activeChapterPositionKey,
            highlightRange = if (!fullVisible) activeChapterHighlightRange else null,
            scrollTargetOffset = null,
            scrollTargetLineIndex = null,
            interactive = !fullVisible,
            showLoading = !fullVisible,
            onDoubleTap = if (!fullVisible) onDoubleTap else null,
            onLongPressLine = if (!fullVisible) onChapterLongPressLine else null,
            selectionActions = if (!fullVisible && onChapterDeleteSelection != null) {
                listOf(BodyReadOnlySelectionAction("删除", onChapterDeleteSelection))
            } else {
                emptyList()
            },
            modifier = chapterLayerModifier
                .alpha(if (fullVisible) 0f else 1f)
                .zIndex(if (fullVisible) 0f else 1f)
                .clipToBounds()
        )
    }
}
