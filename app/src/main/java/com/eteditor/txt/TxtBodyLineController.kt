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

private fun remapTxtLineIndicesAfterSelectionDeletion(
    lineIndices: Set<Int>,
    sourceText: String,
    start: Int,
    end: Int
): Set<Int> {
    return lineIndices
        .mapNotNull { lineIndex ->
            remapTxtLineIndexAfterSelectionDeletion(sourceText, start, end, lineIndex)
        }
        .toSet()
}

private fun remapTxtSupplementedLinesAfterSelectionDeletion(
    records: List<TxtSupplementedCatalogLine>,
    sourceText: String,
    start: Int,
    end: Int
): List<TxtSupplementedCatalogLine> {
    return records.mapNotNull { record ->
        remapTxtLineIndexAfterSelectionDeletion(sourceText, start, end, record.lineIndex)
            ?.let { nextLineIndex -> record.copy(lineIndex = nextLineIndex) }
    }
}

private fun remapTxtLineIndexAfterSelectionDeletion(
    sourceText: String,
    start: Int,
    end: Int,
    lineIndex: Int
): Int? {
    val deletedText = sourceText.substring(start, end)
    val removedLineBreaks = deletedText.count { it == '\n' }
    if (removedLineBreaks == 0) return lineIndex
    val startLine = sourceText.take(start).count { it == '\n' }
    val endLine = sourceText.take(end).count { it == '\n' }
    return when {
        lineIndex < startLine -> lineIndex
        lineIndex == startLine -> lineIndex
        lineIndex <= endLine -> null
        else -> lineIndex - removedLineBreaks
    }
}
