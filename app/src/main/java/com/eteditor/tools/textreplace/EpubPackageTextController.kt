package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.updateEpubChapterHtmlEntry

internal fun EditorController.epubPackageTextReplaceTarget(sourceIndex: Int): EpubPackageTextTarget? {
    val book = epub ?: return null
    return epubPackageTextReplaceTarget(
        book = book,
        sourceIndex = sourceIndex,
        introPath = defaultFetchInfoIntroTarget(book)
    )
}

private fun EditorController.epubPackageText(path: String): String? {
    val book = epub ?: return null
    return epubPackageText(book, path)
}

private fun EditorController.updateEpubPackageText(path: String, text: String) {
    val book = epub ?: return
    updateEpubPackageText(book, path, text)
}

internal fun EditorController.previewEpubPackageTextReplaceSource(
    sourceIndex: Int,
    sourceStart: Int,
    sourceEnd: Int
): Unit? {
    val target = epubPackageTextReplaceTarget(sourceIndex) ?: return null
    val source = epubPackageText(target.path) ?: return null
    val bodyParts = htmlBodyContentParts(source)
    val highlightRange = htmlVisibleBodyRelativeRange(source, sourceStart, sourceEnd) ?: return null
    previewDisplayChapterIndexOverride = null
    previewTitle = target.title
    val book = epub ?: return null
    previewChapterCount = book.chapters.size
    previewHighlightChapterIndex = sourceIndex
    previewHighlightSourceStart = highlightRange.first
    previewHighlightSourceEnd = highlightRange.second
    setPreviewTextFromSource(bodyParts.body, sourceIndex)
    return Unit
}

internal fun EditorController.isEpubPackageTextPreviewSource(): Boolean {
    return previewHighlightChapterIndex?.let { it < 0 } == true &&
        previewDisplayChapterIndexOverride == null
}

private fun EditorController.currentEpubPackageTextPreviewSourceIndex(): Int? {
    return previewHighlightChapterIndex?.takeIf { it < 0 && previewDisplayChapterIndexOverride == null }
}

fun EditorController.setEpubPackageTextVolumeFromBodySelection(
    sourceStart: Int,
    sourceEnd: Int
): Boolean {
    val book = epub ?: run {
        statusMessage = "设为卷仅支持 EPUB"
        return false
    }
    val sourceIndex = currentEpubPackageTextPreviewSourceIndex() ?: run {
        statusMessage = "请先定位包内 HTML 正文"
        return false
    }
    val target = epubPackageTextReplaceTarget(sourceIndex) ?: run {
        statusMessage = "未找到包内 HTML"
        return false
    }
    val source = epubPackageText(target.path) ?: run {
        statusMessage = "未找到包内 HTML"
        return false
    }
    val bodyParts = htmlBodyContentParts(source)
    val body = bodyParts.body
    val start = sourceStart.coerceIn(0, body.length)
    val end = sourceEnd.coerceIn(start, body.length)
    if (end <= start) {
        statusMessage = "请先选择要设为卷的文字"
        return false
    }
    val wholeLineSelection = epubBodyWholeLineSelection(body, start, end)
    val selectedLines = epubSelectedBodyPlainLines(wholeLineSelection.selectedLines.joinToString("\n"))
    val volumeTitle = selectedLines.firstOrNull().orEmpty()
    if (volumeTitle.isBlank()) {
        statusMessage = "所选文字没有可设为卷名的内容"
        return false
    }
    val nextBody = wholeLineSelection.nextBody
    if (ChapterDetector.stripHtml(nextBody).isBlank()) {
        statusMessage = "设为卷后当前正文为空"
        return false
    }

    var errorMessage = ""
    val insertIndex = (previewChapterIndex + 1).coerceIn(0, book.chapters.size)
    val (insertedIndex, insertedChapter) = insertEpubVolumeChapter(
        book = book,
        kind = VOLUME_KIND_NORMAL,
        volumeTitle = volumeTitle,
        insertIndex = insertIndex,
        onError = { message -> errorMessage = message }
    ) ?: run {
        if (errorMessage.isNotBlank()) statusMessage = errorMessage
        return false
    }

    val nextSource = rebuildHtmlWithBodyContent(bodyParts.prefix, nextBody, bodyParts.suffix)
        .toCrlfLineEndings()
    updateEpubPackageText(target.path, nextSource)

    insertedChapter.html = volumeHtml(volumeTitle, selectedLines.drop(1)).toCrlfLineEndings()
    insertedChapter.wordCount = ChapterDetector.countHtmlChars(insertedChapter.html)
    updateEpubChapterHtmlEntry(book, insertedChapter)
    normalizeEpubChapterLineEndingsToCrlf(book, insertedChapter)
    resequenceEpubVolumeFileNames(book, VOLUME_KIND_NORMAL)
    applyVolumeTocLevels(book)

    previewChapterIndex = insertedIndex.coerceIn(0, book.chapters.lastIndex)
    checkReport = null
    markDocumentChanged()
    clearFileRenamePlan()
    val shouldRebuildTextSearchPreview = textSearchToolId != null && replacementFilePreview == null
    if (shouldRebuildTextSearchPreview) {
        selectedTextSearchResultId = null
        selectedReplacementPreviewMatchId = null
    } else {
        clearTextSearchState()
    }
    refreshChapters(refreshPreview = false)
    previewDisplayChapterIndexOverride = null
    previewTitle = target.title
    previewChapterCount = book.chapters.size
    previewHighlightChapterIndex = sourceIndex
    previewHighlightSourceStart = -1
    previewHighlightSourceEnd = -1
    setPreviewTextFromSource(nextBody, sourceIndex)
    if (shouldRebuildTextSearchPreview) {
        rebuildCurrentTextSearchPreviewAfterDocumentChange()
    }
    statusMessage = "已设为卷：${insertedChapter.title}"
    return true
}

fun EditorController.deleteEpubPackageTextBodySelection(
    sourceStart: Int,
    sourceEnd: Int
): Boolean {
    val book = epub ?: run {
        statusMessage = "删除所选文字仅支持 EPUB"
        return false
    }
    val sourceIndex = currentEpubPackageTextPreviewSourceIndex() ?: run {
        statusMessage = "请先定位包内 HTML 正文"
        return false
    }
    val target = epubPackageTextReplaceTarget(sourceIndex) ?: run {
        statusMessage = "未找到包内 HTML"
        return false
    }
    val source = epubPackageText(target.path) ?: run {
        statusMessage = "未找到包内 HTML"
        return false
    }
    val bodyParts = htmlBodyContentParts(source)
    val body = bodyParts.body
    val start = sourceStart.coerceIn(0, body.length)
    val end = sourceEnd.coerceIn(start, body.length)
    if (end <= start) {
        statusMessage = "请先选择要删除的文字"
        return false
    }
    val nextBody = collapseEpubBodyBlankLineAtSeam(body.replaceRange(start, end, ""), start)
    if (ChapterDetector.stripHtml(nextBody).isBlank()) {
        statusMessage = "删除后当前正文为空"
        return false
    }

    val nextSource = rebuildHtmlWithBodyContent(bodyParts.prefix, nextBody, bodyParts.suffix)
        .toCrlfLineEndings()
    updateEpubPackageText(target.path, nextSource)

    checkReport = null
    markDocumentChanged()
    clearFileRenamePlan()
    val shouldRebuildTextSearchPreview = textSearchToolId != null && replacementFilePreview == null
    if (shouldRebuildTextSearchPreview) {
        selectedTextSearchResultId = null
        selectedReplacementPreviewMatchId = null
    } else {
        clearTextSearchState()
    }
    previewDisplayChapterIndexOverride = null
    previewTitle = target.title
    previewChapterCount = book.chapters.size
    previewHighlightChapterIndex = sourceIndex
    previewHighlightSourceStart = -1
    previewHighlightSourceEnd = -1
    setPreviewTextFromSource(nextBody, sourceIndex)
    if (shouldRebuildTextSearchPreview) {
        rebuildCurrentTextSearchPreviewAfterDocumentChange()
    }
    statusMessage = "已删除所选文字"
    return true
}

fun EditorController.wrapEpubPackageTextBodySelection(
    sourceStart: Int,
    sourceEnd: Int
): Boolean {
    val book = epub ?: run {
        statusMessage = "加标签仅支持 EPUB"
        return false
    }
    val sourceIndex = currentEpubPackageTextPreviewSourceIndex() ?: run {
        statusMessage = "请先定位包内 HTML 正文"
        return false
    }
    val target = epubPackageTextReplaceTarget(sourceIndex) ?: run {
        statusMessage = "未找到包内 HTML"
        return false
    }
    val source = epubPackageText(target.path) ?: run {
        statusMessage = "未找到包内 HTML"
        return false
    }
    val bodyParts = htmlBodyContentParts(source)
    val result = wrapEpubBodySelectionParagraphs(bodyParts.body, sourceStart, sourceEnd)
    if (!result.success) {
        if (result.message.isNotBlank()) statusMessage = result.message
        return false
    }
    val nextBody = result.nextBody

    val nextSource = rebuildHtmlWithBodyContent(bodyParts.prefix, nextBody, bodyParts.suffix)
        .toCrlfLineEndings()
    updateEpubPackageText(target.path, nextSource)

    checkReport = null
    markDocumentChanged()
    clearFileRenamePlan()
    val shouldRebuildTextSearchPreview = textSearchToolId != null && replacementFilePreview == null
    if (shouldRebuildTextSearchPreview) {
        selectedTextSearchResultId = null
        selectedReplacementPreviewMatchId = null
    } else {
        clearTextSearchState()
    }
    previewDisplayChapterIndexOverride = null
    previewTitle = target.title
    previewChapterCount = book.chapters.size
    previewHighlightChapterIndex = sourceIndex
    previewHighlightSourceStart = -1
    previewHighlightSourceEnd = -1
    setPreviewTextFromSource(nextBody, sourceIndex)
    if (shouldRebuildTextSearchPreview) {
        rebuildCurrentTextSearchPreviewAfterDocumentChange()
    }
    statusMessage = "已加标签"
    return true
}
