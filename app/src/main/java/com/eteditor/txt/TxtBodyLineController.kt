package com.eteditor

fun EditorController.deleteTxtBodySelection(sourceStart: Int, sourceEnd: Int): Boolean {
    if (warnTxtMoveChapterSyncPending("删除所选文字")) return false
    val document = txt ?: run {
        statusMessage = "删除所选文字仅支持 TXT"
        return false
    }
    val start = sourceStart.coerceIn(0, document.text.length)
    val end = sourceEnd.coerceIn(start, document.text.length)
    if (end <= start) {
        statusMessage = "请先选择要删除的文字"
        return false
    }
    val sourceText = document.text
    document.text = sourceText.replaceRange(start, end, "")
    txtHiddenCatalogLineIndices = remapTxtLineIndicesAfterSelectionDeletion(
        lineIndices = txtHiddenCatalogLineIndices,
        sourceText = sourceText,
        start = start,
        end = end
    )
    txtSupplementedCatalogLines = remapTxtSupplementedLinesAfterSelectionDeletion(
        records = txtSupplementedCatalogLines,
        sourceText = sourceText,
        start = start,
        end = end
    )
    document.chapters = detectCurrentTxtChapters(document.text)
    restoreTxtPreviewPositionForSourceOffset(
        document = document,
        sourceOffset = start.coerceIn(0, document.text.length)
    )
    checkReport = null
    markDocumentChanged()
    clearTextSearchState()
    refreshChapters(refreshPreview = false)
    refreshPreview()
    statusMessage = "已删除所选文字"
    return true
}

internal fun remapTxtLineIndicesAfterSelectionDeletion(
    lineIndices: Set<Int>,
    sourceText: String,
    start: Int,
    end: Int
): Set<Int> {
    val remap = txtSelectionDeletionLineRemap(sourceText, start, end) ?: return lineIndices
    return lineIndices
        .mapNotNull { lineIndex ->
            remapTxtLineIndexAfterSelectionDeletion(remap, lineIndex)
        }
        .toSet()
}

internal fun remapTxtSupplementedLinesAfterSelectionDeletion(
    records: List<TxtSupplementedCatalogLine>,
    sourceText: String,
    start: Int,
    end: Int
): List<TxtSupplementedCatalogLine> {
    val remap = txtSelectionDeletionLineRemap(sourceText, start, end) ?: return records
    return records.mapNotNull { record ->
        remapTxtLineIndexAfterSelectionDeletion(remap, record.lineIndex)
            ?.let { nextLineIndex -> record.copy(lineIndex = nextLineIndex) }
    }
}

private data class TxtSelectionDeletionLineRemap(
    val removedLineBreaks: Int,
    val startLine: Int,
    val startLineFullyRemoved: Boolean,
    val endOffset: Int,
    val lineStarts: List<Int>
)

private fun txtSelectionDeletionLineRemap(
    sourceText: String,
    start: Int,
    end: Int
): TxtSelectionDeletionLineRemap? {
    val lineStarts = txtLineStartOffsets(sourceText)
    val startLine = countLineBreaksBefore(sourceText, start)
    val startLineFullyRemoved = start == lineStarts.getOrNull(startLine) &&
        (lineStarts.getOrNull(startLine + 1)?.let { nextLineStart -> end >= nextLineStart }
            ?: (end >= sourceText.length && end > start))
    val removedLineBreaks = (-txtLineBreakDeltaAfterReplacement(sourceText, start, end, ""))
        .coerceAtLeast(0)
    if (removedLineBreaks == 0 && !startLineFullyRemoved) return null
    return TxtSelectionDeletionLineRemap(
        removedLineBreaks = removedLineBreaks,
        startLine = startLine,
        startLineFullyRemoved = startLineFullyRemoved,
        endOffset = end,
        lineStarts = lineStarts
    )
}

private fun remapTxtLineIndexAfterSelectionDeletion(
    remap: TxtSelectionDeletionLineRemap,
    lineIndex: Int
): Int? {
    val lineStart = remap.lineStarts.getOrNull(lineIndex) ?: return null
    return when {
        lineIndex < remap.startLine -> lineIndex
        lineIndex == remap.startLine -> if (remap.startLineFullyRemoved) null else lineIndex
        lineStart < remap.endOffset -> null
        else -> lineIndex - remap.removedLineBreaks
    }
}
