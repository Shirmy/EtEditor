package com.eteditor

import com.eteditor.core.EpubBook

// 改正文后是否走「保留并重建静读/替换预览」路径（而非整段清空）。
internal fun shouldPreserveEpubTextSearchAfterBodyChange(
    textSearchToolId: String?,
    replacementPreviewPresent: Boolean
): Boolean = textSearchToolId != null || replacementPreviewPresent

// 结构类变更：仅在明确允许保留，且当前确有静读/替换预览时走保留路径。
internal fun shouldPreserveEpubTextSearchAfterStructureChange(
    preserveTextSearch: Boolean,
    textSearchToolId: String?,
    replacementPreviewPresent: Boolean
): Boolean = preserveTextSearch && shouldPreserveEpubTextSearchAfterBodyChange(
    textSearchToolId = textSearchToolId,
    replacementPreviewPresent = replacementPreviewPresent
)

private suspend fun <R> EditorController.prepareCurrentEpubMutation(
    missingMessage: String,
    mutation: (EpubBook) -> R
): PreparedEpubMutation<R>? {
    val source = epub ?: run {
        statusMessage = missingMessage
        return null
    }
    val sourceContentVersion = documentContentVersion
    return prepareEpubMutation(source, sourceContentVersion, mutation)
}

private fun EditorController.finishPreparedEpubStructureChange(
    prepared: PreparedEpubMutation<*>,
    nextPreviewIndex: Int,
    message: String,
    preserveTextSearch: Boolean = false
): Boolean {
    if (!publishPreparedEpubMutation(prepared)) return false
    previewChapterIndex = nextPreviewIndex.coerceIn(0, prepared.book.chapters.lastIndex.coerceAtLeast(0))
    checkReport = null
    markDocumentChanged()
    clearFileRenamePlan()
    val shouldPreserveTextSearch = shouldPreserveEpubTextSearchAfterStructureChange(
        preserveTextSearch = preserveTextSearch,
        textSearchToolId = textSearchToolId,
        replacementPreviewPresent = replacementFilePreview != null
    )
    if (shouldPreserveTextSearch) clearTextSearchStateAfterBodyTextChange() else clearTextSearchState()
    refreshChapters()
    statusMessage = message
    return true
}

private fun EditorController.finishPreparedEpubBodyChange(
    prepared: PreparedEpubMutation<*>,
    chapterIndex: Int,
    message: String
): Boolean {
    if (!publishPreparedEpubMutation(prepared)) return false
    previewChapterIndex = chapterIndex.coerceIn(0, prepared.book.chapters.lastIndex.coerceAtLeast(0))
    checkReport = null
    markDocumentChanged()
    clearFileRenamePlan()
    val shouldPreserveTextSearch = shouldPreserveEpubTextSearchAfterBodyChange(
        textSearchToolId = textSearchToolId,
        replacementPreviewPresent = replacementFilePreview != null
    )
    if (shouldPreserveTextSearch) clearTextSearchStateAfterBodyTextChange() else clearTextSearchState()
    refreshEpubChapterInfoAt(chapterIndex, refreshPreview = !shouldPreserveTextSearch)
    statusMessage = message
    return true
}

internal suspend fun EditorController.deleteEpubChapterAsync(chapterIndex: Int): Boolean {
    val prepared = prepareCurrentEpubMutation("删除章节仅支持 EPUB") { book ->
        deleteEpubChapterFromBook(book, chapterIndex)
    } ?: return false
    val result = prepared.result
    if (!result.success) {
        statusMessage = result.message
        return false
    }
    return finishPreparedEpubStructureChange(
        prepared,
        result.nextPreviewIndex,
        buildEpubStructureChangeMessage(
            prefix = "已删除 EPUB 章节：${result.deletedDisplayTitle}",
            resequence = result.resequence
        )
    )
}

internal suspend fun EditorController.deleteEpubChaptersAsync(chapterIndices: Set<Int>): Boolean {
    val prepared = prepareCurrentEpubMutation("删除章节仅支持 EPUB") { book ->
        deleteEpubChaptersFromBook(book, chapterIndices)
    } ?: return false
    val result = prepared.result
    if (!result.success) {
        statusMessage = result.message
        return false
    }
    return finishPreparedEpubStructureChange(
        prepared,
        result.nextPreviewIndex,
        buildEpubStructureChangeMessage(
            prefix = "已批量删除 EPUB 章节：${result.deletedDisplayTitle}",
            resequence = result.resequence
        )
    )
}

internal suspend fun EditorController.splitEpubChapterAtBodyLineAsync(
    chapterIndex: Int,
    lineIndex: Int,
    newTitleText: String
): Boolean {
    val prepared = prepareCurrentEpubMutation("分章仅支持 EPUB") { book ->
        splitEpubChapterAtLineInBook(
            book = book,
            chapterIndex = chapterIndex,
            lineNumberText = (lineIndex + 1).toString(),
            newTitleText = newTitleText,
            dropSplitLineFromBody = true
        )
    } ?: return false
    val result = prepared.result
    if (!result.success) {
        statusMessage = result.message
        return false
    }
    return finishPreparedEpubStructureChange(
        prepared,
        result.nextPreviewIndex,
        buildEpubStructureChangeMessage(
            prefix = "已分章：${result.sourceDisplayTitle} -> ${result.newTitle}",
            resequence = result.resequence
        ),
        preserveTextSearch = true
    )
}

internal suspend fun EditorController.addEpubVolumeAsync(
    kind: String,
    volumeTitle: String,
    insertIndex: Int
): Boolean {
    val prepared = prepareCurrentEpubMutation("增加卷仅支持 EPUB") { book ->
        addEpubVolumeToBook(book, kind, volumeTitle, insertIndex)
    } ?: return false
    val result = prepared.result
    if (!result.success) {
        statusMessage = result.message
        return false
    }
    return finishPreparedEpubStructureChange(
        prepared,
        result.nextPreviewIndex,
        "已增加卷：${result.fileName}"
    )
}

internal suspend fun EditorController.epubMoveChapterAfterAsync(
    sourceIndex: Int,
    targetIndex: Int
): Boolean {
    val prepared = prepareCurrentEpubMutation("移动章节仅支持 EPUB") { book ->
        moveEpubChapterAfterInBook(
            book,
            sourceIndex,
            targetIndex,
            MOVE_TARGET_BOOK_START,
            MOVE_TARGET_BOOK_END
        )
    } ?: return false
    val result = prepared.result
    if (!result.success) {
        if (result.message.isNotBlank()) statusMessage = result.message
        return false
    }
    return finishPreparedEpubStructureChange(
        prepared,
        result.nextPreviewIndex,
        buildEpubStructureChangeMessage(
            prefix = "已移动章节：${result.movedDisplayTitle}",
            resequence = result.resequence
        )
    )
}

internal suspend fun EditorController.epubMoveChaptersAfterAsync(
    sourceIndices: Set<Int>,
    targetIndex: Int
): Boolean {
    val prepared = prepareCurrentEpubMutation("移动章节仅支持 EPUB") { book ->
        moveEpubChaptersAfterInBook(
            book,
            sourceIndices,
            targetIndex,
            MOVE_TARGET_BOOK_START,
            MOVE_TARGET_BOOK_END
        )
    } ?: return false
    val result = prepared.result
    if (!result.success) {
        statusMessage = result.message.ifBlank { "未移动:选中章节或目标位置无效" }
        return false
    }
    return finishPreparedEpubStructureChange(
        prepared,
        result.nextPreviewIndex,
        buildEpubStructureChangeMessage(
            prefix = "已批量移动 EPUB 章节：${result.movedDisplayTitle}",
            resequence = result.resequence
        )
    )
}

internal suspend fun EditorController.deleteEpubBodyLineAsync(
    chapterIndex: Int,
    lineIndex: Int
): Boolean {
    val prepared = prepareCurrentEpubMutation("删除正文行仅支持 EPUB") { book ->
        deleteEpubBodyLineFromBook(book, chapterIndex, lineIndex)
    } ?: return false
    val result = prepared.result
    if (!result.success) {
        statusMessage = result.message
        return false
    }
    return finishPreparedEpubBodyChange(prepared, chapterIndex, "已删除正文行")
}

internal suspend fun EditorController.setEpubVolumeAtBodyLineAsync(
    chapterIndex: Int,
    lineIndex: Int,
    lineCountText: String,
    volumeTitleText: String
): Boolean {
    val prepared = prepareCurrentEpubMutation("设为卷仅支持 EPUB") { book ->
        setEpubVolumeAtBodyLineInBook(book, chapterIndex, lineIndex, lineCountText, volumeTitleText)
    } ?: return false
    val result = prepared.result
    if (!result.success) {
        statusMessage = result.message
        return false
    }
    return finishPreparedEpubStructureChange(
        prepared,
        result.nextPreviewIndex,
        "已设为卷：${result.volumeDisplayTitle}",
        preserveTextSearch = true
    )
}

internal suspend fun EditorController.setEpubVolumeFromBodySelectionAsync(
    chapterIndex: Int,
    sourceStart: Int,
    sourceEnd: Int
): Boolean {
    val prepared = prepareCurrentEpubMutation("设为卷仅支持 EPUB") { book ->
        setEpubVolumeFromBodySelectionInBook(book, chapterIndex, sourceStart, sourceEnd)
    } ?: return false
    val result = prepared.result
    if (!result.success) {
        statusMessage = result.message
        return false
    }
    return finishPreparedEpubStructureChange(
        prepared,
        chapterIndex,
        "已设为卷：${result.volumeDisplayTitle}",
        preserveTextSearch = true
    )
}

internal suspend fun EditorController.deleteEpubBodySelectionAsync(
    chapterIndex: Int,
    sourceStart: Int,
    sourceEnd: Int
): Boolean {
    val prepared = prepareCurrentEpubMutation("删除所选文字仅支持 EPUB") { book ->
        deleteEpubBodySelectionFromBook(book, chapterIndex, sourceStart, sourceEnd)
    } ?: return false
    val result = prepared.result
    if (!result.success) {
        statusMessage = result.message
        return false
    }
    return finishPreparedEpubBodyChange(prepared, chapterIndex, "已删除所选文字")
}

internal suspend fun EditorController.wrapEpubBodySelectionWithParagraphsAsync(
    chapterIndex: Int,
    sourceStart: Int,
    sourceEnd: Int
): Boolean {
    val prepared = prepareCurrentEpubMutation("加标签仅支持 EPUB") { book ->
        wrapEpubBodySelectionParagraphsInBook(book, chapterIndex, sourceStart, sourceEnd)
    } ?: return false
    val result = prepared.result
    if (!result.success) {
        statusMessage = result.message
        return false
    }
    return finishPreparedEpubBodyChange(prepared, chapterIndex, "已加标签")
}
