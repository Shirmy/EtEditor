package com.eteditor

import com.eteditor.core.TxtDocument

private const val TXT_FULL_PREVIEW_WINDOW_LIMIT = 100_000
private const val TXT_FULL_PREVIEW_WINDOW_BEFORE = 20_000
private const val TXT_FULL_PREVIEW_WINDOW_LINE_ALIGN_LIMIT = 2_000
private const val TXT_FULL_EDIT_WINDOW_LIMIT = 100_000
private const val TXT_FULL_EDIT_WINDOW_BEFORE = 50_000
private const val TXT_FULL_WINDOW_TAIL_ALLOWANCE = 20_000

internal fun txtRequiresFullEditWindow(sourceLength: Int): Boolean {
    return sourceLength > TXT_FULL_EDIT_WINDOW_LIMIT + TXT_FULL_WINDOW_TAIL_ALLOWANCE
}

internal fun buildTxtFullEditWindowSeed(source: String, anchorOffset: Int): TxtFullEditWindowSeed {
    val safeAnchorOffset = anchorOffset.coerceIn(0, source.length)
    if (source.length <= TXT_FULL_EDIT_WINDOW_LIMIT + TXT_FULL_WINDOW_TAIL_ALLOWANCE) {
        return TxtFullEditWindowSeed(
            sourceText = source,
            startOffset = 0,
            endOffset = source.length,
            targetOffset = safeAnchorOffset,
            targetLineIndex = countLineBreaksBefore(source, safeAnchorOffset),
            windowed = false
        )
    }
    val latestStart = (source.length - TXT_FULL_EDIT_WINDOW_LIMIT).coerceAtLeast(0)
    val roughStart = (safeAnchorOffset - TXT_FULL_EDIT_WINDOW_BEFORE)
        .coerceIn(0, latestStart)
    val alignedStart = alignTxtWindowStart(source, roughStart)
    val start = if (alignedStart + TXT_FULL_EDIT_WINDOW_LIMIT < safeAnchorOffset) {
        roughStart
    } else {
        alignedStart
    }
    val roughEnd = (start + TXT_FULL_EDIT_WINDOW_LIMIT).coerceAtMost(source.length)
    val alignedEnd = alignTxtWindowEnd(source, roughEnd)
    val end = if (source.length - alignedEnd <= TXT_FULL_WINDOW_TAIL_ALLOWANCE) {
        source.length
    } else {
        alignedEnd
    }
    return TxtFullEditWindowSeed(
        sourceText = source,
        startOffset = start,
        endOffset = end,
        targetOffset = (safeAnchorOffset - start).coerceIn(0, end - start),
        targetLineIndex = countLineBreaksBefore(source, safeAnchorOffset) - countLineBreaksBefore(source, start),
        windowed = true
    )
}

internal fun buildTxtFullPreviewWindow(source: String, anchorOffset: Int): TxtFullPreviewWindow {
    if (source.length <= TXT_FULL_PREVIEW_WINDOW_LIMIT + TXT_FULL_WINDOW_TAIL_ALLOWANCE) {
        return TxtFullPreviewWindow(
            text = source,
            startOffset = 0,
            endOffset = source.length,
            startLineIndex = 0
        )
    }
    val safeAnchorOffset = anchorOffset.coerceIn(0, source.length)
    val latestStart = (source.length - TXT_FULL_PREVIEW_WINDOW_LIMIT).coerceAtLeast(0)
    val roughStart = (safeAnchorOffset - TXT_FULL_PREVIEW_WINDOW_BEFORE)
        .coerceIn(0, latestStart)
    val alignedStart = alignTxtWindowStart(source, roughStart)
    val start = if (alignedStart + TXT_FULL_PREVIEW_WINDOW_LIMIT < safeAnchorOffset) {
        roughStart
    } else {
        alignedStart
    }
    val roughEnd = (start + TXT_FULL_PREVIEW_WINDOW_LIMIT).coerceAtMost(source.length)
    val alignedEnd = alignTxtWindowEnd(source, roughEnd)
    val end = if (source.length - alignedEnd <= TXT_FULL_WINDOW_TAIL_ALLOWANCE) {
        source.length
    } else {
        alignedEnd
    }
    return TxtFullPreviewWindow(
        text = source.substring(start, end),
        startOffset = start,
        endOffset = end,
        startLineIndex = countLineBreaksBefore(source, start)
    )
}

internal fun txtFullPreviewAnchorOffset(
    document: TxtDocument,
    selectedLocation: TextSourceLocation?,
    cachedAnchor: TxtFullPreviewAnchor?,
    previewChapterIndex: Int
): Int {
    selectedLocation?.let { location ->
        return location.sourceStart.coerceIn(0, document.text.length)
    }
    cachedAnchor?.let { anchor ->
        return anchor.offset.coerceIn(0, document.text.length)
    }
    if (previewChapterIndex < 0) return 0
    return document.chapters
        .getOrNull(previewChapterIndex)
        ?.startIndex
        ?.coerceIn(0, document.text.length)
        ?: 0
}

internal fun txtFullPreviewHighlightRange(
    window: TxtFullPreviewWindow,
    selectedLocation: TextSourceLocation?
): Pair<Int, Int>? {
    val location = selectedLocation ?: return null
    if (location.sourceEnd <= window.startOffset || location.sourceStart >= window.endOffset) return null
    val start = (location.sourceStart - window.startOffset).coerceIn(0, window.text.length)
    val end = (location.sourceEnd - window.startOffset).coerceIn(start, window.text.length)
    return (start to end).takeIf { end > start }
}

internal fun buildTxtFullPreviewState(
    sourceLength: Int?,
    window: TxtFullPreviewWindow,
    anchor: TxtFullPreviewAnchor?,
    highlightRange: Pair<Int, Int>?
): TxtFullPreviewState {
    return TxtFullPreviewState(
        text = window.text,
        windowKey = "${window.startOffset}:${window.endOffset}",
        startLineIndex = window.startLineIndex,
        highlightRange = highlightRange,
        scrollTargetOffset = if (sourceLength != null && anchor != null && highlightRange == null) {
            (anchor.offset.coerceIn(0, sourceLength) - window.startOffset)
                .coerceIn(0, window.text.length)
        } else {
            null
        },
        scrollTargetLineIndex = if (anchor != null && highlightRange == null) {
            (anchor.lineIndex - window.startLineIndex).coerceAtLeast(0)
        } else {
            null
        }
    )
}

internal fun txtPreviewSelectedLineHighlight(
    document: TxtDocument,
    selectedLocation: TextSourceLocation?,
    fullPreviewMode: Boolean,
    prefaceEndIndex: Int?
): TxtPreviewLineHighlight? {
    val location = selectedLocation ?: return null
    if (location.sourceStart < 0 || location.sourceEnd <= location.sourceStart) return null
    if (document.chapters.isEmpty() || fullPreviewMode) {
        return txtLineHighlightForSource(
            source = document.text,
            sourceStart = location.sourceStart,
            sourceEnd = location.sourceEnd,
            baseLineIndex = 0
        )
    }
    prefaceEndIndex?.let { prefaceEnd ->
        if (location.sourceStart < prefaceEnd) {
            val source = document.text.substring(0, prefaceEnd)
            return txtLineHighlightForSource(
                source = source,
                sourceStart = location.sourceStart,
                sourceEnd = location.sourceEnd.coerceAtMost(prefaceEnd),
                baseLineIndex = 0
            )
        }
    }
    val fallbackIndex = document.chapters.indexOfLast { chapter ->
        chapter.startIndex <= location.sourceStart
    }
    val resolvedIndex = location.chapterIndex
        .takeIf { it in document.chapters.indices }
        ?.takeIf { index ->
            val chapter = document.chapters[index]
            location.sourceStart in chapter.startIndex..chapter.endIndex.coerceAtLeast(chapter.startIndex)
        }
        ?: fallbackIndex
    val chapter = document.chapters.getOrNull(resolvedIndex) ?: return null
    val start = chapter.startIndex.coerceIn(0, document.text.length)
    val end = chapter.endIndex.coerceIn(start, document.text.length)
    val source = document.text.substring(start, end)
    return txtLineHighlightForSource(
        source = source,
        sourceStart = location.sourceStart - start,
        sourceEnd = location.sourceEnd - start,
        baseLineIndex = chapter.lineIndex
    )
}

private fun alignTxtWindowStart(text: String, roughStart: Int): Int {
    if (roughStart <= 0) return 0
    val aligned = txtAlignedLineStart(text, roughStart)
    return if (roughStart - aligned <= TXT_FULL_PREVIEW_WINDOW_LINE_ALIGN_LIMIT) aligned else roughStart
}

private fun alignTxtWindowEnd(text: String, roughEnd: Int): Int {
    if (roughEnd >= text.length) return text.length
    val aligned = txtAlignedLineEnd(text, roughEnd)
    return if (aligned - roughEnd <= TXT_FULL_PREVIEW_WINDOW_LINE_ALIGN_LIMIT) aligned else roughEnd
}

internal fun txtLineHighlightForSource(
    source: String,
    sourceStart: Int,
    sourceEnd: Int,
    baseLineIndex: Int
): TxtPreviewLineHighlight? {
    if (source.isEmpty()) return null
    val start = sourceStart.coerceIn(0, source.length)
    val end = sourceEnd.coerceIn(start, source.length)
    if (end <= start) return null
    val anchor = if (start > 0 && start < source.length && (source[start] == '\n' || source[start] == '\r')) {
        start - 1
    } else {
        start
    }
    val lineStart = txtLineStartAtOffset(source, anchor)
    val lineBreakStart = txtLineBreakStartAtOrAfter(source, anchor)
    val lineEnd = if (lineBreakStart < source.length && source[lineBreakStart] == '\r') {
        lineBreakStart + 1
    } else {
        lineBreakStart
    }
    val lineIndex = baseLineIndex + countLineBreaksBefore(source, lineStart)
    val highlightStart = (start - lineStart).coerceAtLeast(0)
    val highlightEnd = (end.coerceAtMost(lineEnd) - lineStart)
        .coerceAtLeast(highlightStart)
    return TxtPreviewLineHighlight(
        lineIndex = lineIndex,
        start = highlightStart,
        end = highlightEnd
    )
}

internal fun mappedTxtChapterPreviewOffsetToSourceOffset(
    source: String,
    mappedTitle: String,
    mappedOffset: Int
): Int {
    if (source.isEmpty()) return 0
    val safeMappedOffset = mappedOffset.coerceAtLeast(0)
    val titleLineEnd = txtLineBreakStartAtOrAfter(source, 0)
    val displayTitle = mappedTitle.takeIf { it.isNotBlank() } ?: return safeMappedOffset.coerceIn(0, source.length)
    val rawTitle = source.substring(0, titleLineEnd)
    if (rawTitle == displayTitle) return safeMappedOffset.coerceIn(0, source.length)
    return if (safeMappedOffset <= displayTitle.length) {
        safeMappedOffset.coerceIn(0, titleLineEnd)
    } else {
        (safeMappedOffset - (displayTitle.length - titleLineEnd)).coerceIn(0, source.length)
    }
}
