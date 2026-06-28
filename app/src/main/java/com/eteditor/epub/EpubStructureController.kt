package com.eteditor

import com.eteditor.core.DocumentKind

internal fun EditorController.updateEpubChapterItem(
    chapterIndex: Int,
    fileName: String,
    chapterTitle: String
): Boolean {
    val book = (epub ?: return false).mutableDeepCopy()
    val result = updateEpubChapterItemModel(book, chapterIndex, fileName, chapterTitle)
    if (!result.success) {
        if (result.message.isNotBlank()) statusMessage = result.message
        return false
    }
    epub = book
    checkReport = null
    markDocumentChanged()
    clearFileRenamePlan()
    refreshChapters()
    selectPreviewChapter(chapterIndex)
    return true
}

fun EditorController.deleteEpubChapter(chapterIndex: Int): Boolean {
    val book = (epub ?: run {
        statusMessage = "删除章节仅支持 EPUB"
        return false
    }).mutableDeepCopy()
    val result = deleteEpubChapterFromBook(book, chapterIndex)
    if (!result.success) {
        if (result.message.isNotBlank()) statusMessage = result.message
        return false
    }
    epub = book
    previewChapterIndex = result.nextPreviewIndex
    checkReport = null
    markDocumentChanged()
    clearFileRenamePlan()
    clearTextSearchState()
    refreshChapters()
    statusMessage = buildEpubStructureChangeMessage(
        prefix = "已删除 EPUB 章节：${result.deletedDisplayTitle}",
        resequence = result.resequence
    )
    return true
}

fun EditorController.epubChapterBodyLineCount(chapterIndex: Int): Int {
    val chapter = epub?.chapters?.getOrNull(chapterIndex) ?: return 0
    return epubChapterBodyLines(chapter).size
}

fun EditorController.suggestEpubSplitChapterTitle(chapterIndex: Int, lineNumberText: String): String {
    val chapter = epub?.chapters?.getOrNull(chapterIndex) ?: return ""
    return suggestEpubSplitChapterTitleFromBodyLines(epubChapterBodyLines(chapter), lineNumberText)
}

fun EditorController.splitEpubChapterAtLine(
    chapterIndex: Int,
    lineNumberText: String,
    newTitleText: String,
    dropSplitLineFromBody: Boolean = false
): Boolean {
    val book = (epub ?: run {
        statusMessage = "分章仅支持 EPUB"
        return false
    }).mutableDeepCopy()
    val result = splitEpubChapterAtLineInBook(
        book = book,
        chapterIndex = chapterIndex,
        lineNumberText = lineNumberText,
        newTitleText = newTitleText,
        dropSplitLineFromBody = dropSplitLineFromBody
    )
    if (!result.success) {
        if (result.message.isNotBlank()) statusMessage = result.message
        return false
    }
    epub = book
    previewChapterIndex = result.nextPreviewIndex
    checkReport = null
    markDocumentChanged()
    clearFileRenamePlan()
    clearTextSearchState()
    refreshChapters()
    statusMessage = buildEpubStructureChangeMessage(
        prefix = "已分章：${result.sourceDisplayTitle} -> ${result.newTitle}",
        resequence = result.resequence
    )
    return true
}

fun EditorController.defaultVolumeTitle(kind: String): String {
    return defaultEpubVolumeTitle(kind)
}

fun EditorController.volumeFileNamePreview(kind: String): String {
    return epubVolumeFileNamePreview(epub, kind)
}

fun EditorController.hasExtraVolume(): Boolean {
    return hasExtraEpubVolume(epub)
}

fun EditorController.addEpubVolume(kind: String, volumeTitle: String, insertIndex: Int): Boolean {
    val book = (epub ?: run {
        statusMessage = "增加卷仅支持 EPUB"
        return false
    }).mutableDeepCopy()
    val result = addEpubVolumeToBook(
        book = book,
        kind = kind,
        volumeTitle = volumeTitle,
        insertIndex = insertIndex
    )
    if (!result.success) {
        if (result.message.isNotBlank()) statusMessage = result.message
        return false
    }
    epub = book
    previewChapterIndex = result.nextPreviewIndex
    checkReport = null
    markDocumentChanged()
    clearFileRenamePlan()
    clearTextSearchState()
    refreshChapters()
    statusMessage = "已增加卷：${result.fileName}"
    return true
}

fun EditorController.epubMoveChapterAfter(sourceIndex: Int, targetIndex: Int): Boolean {
    val book = (epub ?: run {
        statusMessage = "移动章节仅支持 EPUB"
        return false
    }).mutableDeepCopy()
    val result = moveEpubChapterAfterInBook(
        book = book,
        sourceIndex = sourceIndex,
        targetIndex = targetIndex,
        bookStartTarget = MOVE_TARGET_BOOK_START,
        bookEndTarget = MOVE_TARGET_BOOK_END
    )
    if (!result.success) return false
    epub = book
    previewChapterIndex = result.nextPreviewIndex
    checkReport = null
    markDocumentChanged()
    clearFileRenamePlan()
    clearTextSearchState()
    refreshChapters()
    statusMessage = "已移动章节：${result.movedDisplayTitle}"
    return true
}

fun EditorController.isSection0001HiddenFromDirectoryToc(pathOrFileName: String): Boolean {
    return kind == DocumentKind.Epub &&
        epubHideSection0001FromNcx &&
        isSection0001Path(pathOrFileName)
}

fun EditorController.epubSplitChapterDefaultTitle(chapterIndex: Int, lineIndex: Int): String {
    val chapter = epub?.chapters?.getOrNull(chapterIndex) ?: return ""
    val line = epubChapterBodyLines(chapter).getOrNull(lineIndex) ?: return ""
    return cleanEpubBodyLineTitle(line)
}

fun EditorController.epubBodyLinePlainText(chapterIndex: Int, lineIndex: Int): String {
    val chapter = epub?.chapters?.getOrNull(chapterIndex) ?: return ""
    val line = epubChapterBodyLines(chapter).getOrNull(lineIndex) ?: return ""
    return cleanEpubBodyLinePlainText(line)
}

fun EditorController.epubVolumeDefaultTitle(
    chapterIndex: Int,
    lineIndex: Int,
    lineCountText: String
): String {
    val chapter = epub?.chapters?.getOrNull(chapterIndex) ?: return ""
    return epubVolumeDefaultTitleFromBodyLines(epubChapterBodyLines(chapter), lineIndex, lineCountText)
}

fun EditorController.splitEpubChapterAtBodyLine(chapterIndex: Int, lineIndex: Int, newTitleText: String): Boolean {
    return splitEpubChapterAtLine(
        chapterIndex = chapterIndex,
        lineNumberText = (lineIndex + 1).toString(),
        newTitleText = newTitleText,
        dropSplitLineFromBody = true
    )
}

fun EditorController.deleteEpubBodyLine(chapterIndex: Int, lineIndex: Int): Boolean {
    val book = (epub ?: run {
        statusMessage = "删除正文行仅支持 EPUB"
        return false
    }).mutableDeepCopy()
    val result = deleteEpubBodyLineFromBook(book, chapterIndex, lineIndex)
    if (!result.success) {
        if (result.message.isNotBlank()) statusMessage = result.message
        return false
    }
    epub = book
    previewChapterIndex = chapterIndex.coerceIn(0, book.chapters.lastIndex)
    checkReport = null
    markDocumentChanged()
    clearFileRenamePlan()
    clearTextSearchState()
    refreshChapters()
    statusMessage = "已删除正文行"
    return true
}

fun EditorController.setEpubVolumeAtBodyLine(
    chapterIndex: Int,
    lineIndex: Int,
    lineCountText: String,
    volumeTitleText: String
): Boolean {
    val book = (epub ?: run {
        statusMessage = "设为卷仅支持 EPUB"
        return false
    }).mutableDeepCopy()
    val result = setEpubVolumeAtBodyLineInBook(
        book = book,
        chapterIndex = chapterIndex,
        lineIndex = lineIndex,
        lineCountText = lineCountText,
        volumeTitleText = volumeTitleText
    )
    if (!result.success) {
        if (result.message.isNotBlank()) statusMessage = result.message
        return false
    }
    epub = book
    previewChapterIndex = result.nextPreviewIndex.coerceIn(0, book.chapters.lastIndex)
    checkReport = null
    markDocumentChanged()
    clearFileRenamePlan()
    clearTextSearchState()
    refreshChapters()
    statusMessage = "已设为卷：${result.volumeDisplayTitle}"
    return true
}

fun EditorController.setEpubVolumeFromBodySelection(
    chapterIndex: Int,
    sourceStart: Int,
    sourceEnd: Int
): Boolean {
    val book = (epub ?: run {
        statusMessage = "设为卷仅支持 EPUB"
        return false
    }).mutableDeepCopy()
    val result = setEpubVolumeFromBodySelectionInBook(
        book = book,
        chapterIndex = chapterIndex,
        sourceStart = sourceStart,
        sourceEnd = sourceEnd
    )
    if (!result.success) {
        if (result.message.isNotBlank()) statusMessage = result.message
        return false
    }
    epub = book
    previewChapterIndex = chapterIndex.coerceIn(0, book.chapters.lastIndex)
    checkReport = null
    markDocumentChanged()
    clearFileRenamePlan()
    val shouldRebuildTextSearchPreview = textSearchToolId != null && replacementFilePreview == null
    if (shouldRebuildTextSearchPreview) {
        clearPreviewHighlight()
    } else {
        clearTextSearchState()
    }
    refreshChapters()
    if (shouldRebuildTextSearchPreview) {
        rebuildCurrentTextSearchPreviewAfterDocumentChange()
    }
    statusMessage = "已设为卷：${result.volumeDisplayTitle}"
    return true
}

fun EditorController.deleteEpubBodySelection(
    chapterIndex: Int,
    sourceStart: Int,
    sourceEnd: Int
): Boolean {
    val book = (epub ?: run {
        statusMessage = "删除所选文字仅支持 EPUB"
        return false
    }).mutableDeepCopy()
    val result = deleteEpubBodySelectionFromBook(book, chapterIndex, sourceStart, sourceEnd)
    if (!result.success) {
        if (result.message.isNotBlank()) statusMessage = result.message
        return false
    }
    epub = book
    previewChapterIndex = chapterIndex.coerceIn(0, book.chapters.lastIndex)
    checkReport = null
    markDocumentChanged()
    clearFileRenamePlan()
    val shouldRebuildTextSearchPreview = textSearchToolId != null && replacementFilePreview == null
    if (shouldRebuildTextSearchPreview) {
        clearPreviewHighlight()
    } else {
        clearTextSearchState()
    }
    refreshChapters()
    if (shouldRebuildTextSearchPreview) {
        rebuildCurrentTextSearchPreviewAfterDocumentChange()
    }
    statusMessage = "已删除所选文字"
    return true
}

fun EditorController.wrapEpubBodySelectionWithParagraphs(
    chapterIndex: Int,
    sourceStart: Int,
    sourceEnd: Int
): Boolean {
    val book = (epub ?: run {
        statusMessage = "加标签仅支持 EPUB"
        return false
    }).mutableDeepCopy()
    val result = wrapEpubBodySelectionParagraphsInBook(book, chapterIndex, sourceStart, sourceEnd)
    if (!result.success) {
        if (result.message.isNotBlank()) statusMessage = result.message
        return false
    }
    epub = book
    previewChapterIndex = chapterIndex.coerceIn(0, book.chapters.lastIndex)
    checkReport = null
    markDocumentChanged()
    clearFileRenamePlan()
    val shouldRebuildTextSearchPreview = textSearchToolId != null && replacementFilePreview == null
    if (shouldRebuildTextSearchPreview) {
        clearPreviewHighlight()
    } else {
        clearTextSearchState()
    }
    refreshChapters()
    if (shouldRebuildTextSearchPreview) {
        rebuildCurrentTextSearchPreviewAfterDocumentChange()
    }
    statusMessage = "已加标签"
    return true
}
