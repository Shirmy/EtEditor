package com.eteditor

internal fun buildInsertChapterSourcePreview(
    data: InsertChapterSourceData,
    previewSourceType: String = data.sourceType
): InsertChapterSourcePreview {
    return InsertChapterSourcePreview(
        sourceUri = data.sourceUri,
        sourceType = previewSourceType,
        items = data.chapters.map { chapter ->
            InsertChapterSourceItem(
                sourceIndex = chapter.sourceIndex,
                title = chapter.title,
                fileName = chapter.fileName,
                wordCount = chapter.wordCount,
                tocLevel = chapter.tocLevel,
                isVolume = chapter.isVolume
            )
        }
    )
}

internal fun selectInsertableChapters(
    source: InsertChapterSourceData,
    selectedSourceIndices: Set<Int>,
    useSelectedSourceIndices: Boolean,
    reverseSelectedOrder: Boolean
): List<InsertableChapter> {
    val selected = if (useSelectedSourceIndices) {
        source.chapters.filter { it.sourceIndex in selectedSourceIndices }
    } else {
        source.chapters
    }
    return if (reverseSelectedOrder) selected.asReversed() else selected
}

internal fun insertChapterSourceDataMatches(
    data: InsertChapterSourceData,
    sourceUri: String,
    sourceType: String
): Boolean {
    if (data.sourceUri != sourceUri) return false
    return when (sourceType) {
        INSERT_CHAPTER_SOURCE_UPLOAD -> data.sourceType == INSERT_CHAPTER_SOURCE_EPUB ||
            data.sourceType == INSERT_CHAPTER_SOURCE_TXT
        else -> data.sourceType == sourceType
    }
}

internal fun insertChapterProgressLabel(
    fallbackLabel: String,
    phase: String,
    completed: Int,
    total: Int
): String {
    val label = phase.ifBlank { fallbackLabel }.ifBlank { "插入章节" }
    return if (total > 0 && completed > 0) {
        "$label ${completed.coerceAtMost(total)}/$total"
    } else {
        label
    }
}

internal fun insertChapterProgressValue(completed: Int, total: Int): Float {
    return if (total > 0) {
        completed.coerceIn(0, total).toFloat() / total
    } else {
        0f
    }
}
