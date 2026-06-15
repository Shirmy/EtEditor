package com.eteditor

import com.eteditor.core.TxtChapter

internal data class TxtChapterBlockDeletionResult(
    val text: String,
    val hiddenCatalogLineIndices: Set<Int>,
    val supplementedCatalogLines: List<TxtSupplementedCatalogLine>,
    val deletedIndices: Set<Int>,
    val deletedCount: Int
)

internal fun deleteTxtChapterBlockText(
    sourceText: String,
    chapters: List<TxtChapter>,
    index: Int,
    hiddenCatalogLineIndices: Set<Int>,
    supplementedCatalogLines: List<TxtSupplementedCatalogLine>
): TxtChapterBlockDeletionResult? {
    val range = txtDeleteRangeForChapter(sourceText, chapters, index) ?: return null
    val lineOffsets = txtLineOffsets(sourceText)
    return TxtChapterBlockDeletionResult(
        text = sourceText.replaceRange(range.start, range.end, ""),
        hiddenCatalogLineIndices = hiddenCatalogLineIndices
            .mapNotNull { lineIndex -> remapTxtLineIndexAfterDeleteRange(lineIndex, lineOffsets, range) }
            .toSet(),
        supplementedCatalogLines = supplementedCatalogLines.mapNotNull { record ->
            remapTxtLineIndexAfterDeleteRange(record.lineIndex, lineOffsets, range)?.let { nextLineIndex ->
                record.copy(lineIndex = nextLineIndex)
            }
        },
        deletedIndices = setOf(index),
        deletedCount = 1
    )
}

internal fun deleteTxtChapterBlocksText(
    sourceText: String,
    chapters: List<TxtChapter>,
    selectedIndices: List<Int>,
    hiddenCatalogLineIndices: Set<Int>,
    supplementedCatalogLines: List<TxtSupplementedCatalogLine>
): TxtChapterBlockDeletionResult? {
    val indexedRanges = selectedIndices.mapNotNull { index ->
        txtDeleteRangeForChapter(sourceText, chapters, index)?.let { range -> index to range }
    }
    if (indexedRanges.isEmpty()) return null

    val ranges = indexedRanges.map { (_, range) -> range }.sortedBy { it.start }
    val lineOffsets = txtLineOffsets(sourceText)
    val nextText = buildString(sourceText.length) {
        var cursor = 0
        ranges.forEach { range ->
            append(sourceText, cursor, range.start)
            cursor = range.end
        }
        append(sourceText, cursor, sourceText.length)
    }

    return TxtChapterBlockDeletionResult(
        text = nextText,
        hiddenCatalogLineIndices = hiddenCatalogLineIndices
            .mapNotNull { lineIndex -> remapTxtLineIndexAfterDeleteRanges(lineIndex, lineOffsets, ranges) }
            .toSet(),
        supplementedCatalogLines = supplementedCatalogLines.mapNotNull { record ->
            remapTxtLineIndexAfterDeleteRanges(record.lineIndex, lineOffsets, ranges)?.let { nextLineIndex ->
                record.copy(lineIndex = nextLineIndex)
            }
        },
        deletedIndices = indexedRanges.map { (index, _) -> index }.toSet(),
        deletedCount = indexedRanges.size
    )
}

private data class TxtDeleteRange(
    val start: Int,
    val end: Int,
    val removedLineBreaks: Int
)

private fun txtDeleteRangeForChapter(sourceText: String, chapters: List<TxtChapter>, index: Int): TxtDeleteRange? {
    val chapter = chapters.getOrNull(index) ?: return null
    val start = chapter.startIndex.coerceIn(0, sourceText.length)
    val end = (chapters.getOrNull(index + 1)?.startIndex ?: sourceText.length)
        .coerceIn(start, sourceText.length)
    if (end <= start) return null
    return TxtDeleteRange(
        start = start,
        end = end,
        removedLineBreaks = sourceText.substring(start, end).count { it == '\n' }
    )
}

private fun remapTxtLineIndexAfterDeleteRange(
    lineIndex: Int,
    lineOffsets: List<Int>,
    range: TxtDeleteRange
): Int? {
    val lineStart = lineOffsets.getOrNull(lineIndex) ?: return null
    return when {
        lineStart < range.start -> lineIndex
        lineStart >= range.end -> (lineIndex - range.removedLineBreaks).coerceAtLeast(0)
        else -> null
    }
}

private fun remapTxtLineIndexAfterDeleteRanges(
    lineIndex: Int,
    lineOffsets: List<Int>,
    ranges: List<TxtDeleteRange>
): Int? {
    val lineStart = lineOffsets.getOrNull(lineIndex) ?: return null
    var shift = 0
    ranges.forEach { range ->
        when {
            lineStart < range.start -> return (lineIndex - shift).coerceAtLeast(0)
            lineStart < range.end -> return null
            else -> shift += range.removedLineBreaks
        }
    }
    return (lineIndex - shift).coerceAtLeast(0)
}
