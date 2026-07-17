package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.EpubBook
import com.eteditor.core.updateEpubChapterHtmlEntry

internal data class EpubPackageTextBodyMutationResult(
    val success: Boolean,
    val message: String = "",
    val nextBody: String = "",
    val insertedIndex: Int = -1,
    val insertedTitle: String = ""
)

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

internal fun setEpubPackageTextVolumeFromBodySelectionInBook(
    book: EpubBook,
    path: String,
    insertIndex: Int,
    sourceStart: Int,
    sourceEnd: Int
): EpubPackageTextBodyMutationResult {
    val source = epubPackageText(book, path)
        ?: return EpubPackageTextBodyMutationResult(success = false, message = "未找到包内 HTML")
    val bodyParts = htmlBodyContentParts(source)
    val body = bodyParts.body
    val start = sourceStart.coerceIn(0, body.length)
    val end = sourceEnd.coerceIn(start, body.length)
    if (end <= start) {
        return EpubPackageTextBodyMutationResult(success = false, message = "请先选择要设为卷的文字")
    }
    val wholeLineSelection = epubBodyWholeLineSelection(body, start, end)
    val selectedLines = epubSelectedBodyPlainLines(wholeLineSelection.selectedLines.joinToString("\n"))
    val volumeTitle = selectedLines.firstOrNull().orEmpty()
    if (volumeTitle.isBlank()) {
        return EpubPackageTextBodyMutationResult(success = false, message = "所选文字没有可设为卷名的内容")
    }
    val nextBody = wholeLineSelection.nextBody
    if (ChapterDetector.stripHtml(nextBody).isBlank()) {
        return EpubPackageTextBodyMutationResult(success = false, message = "设为卷后当前正文为空")
    }

    var errorMessage = ""
    val (insertedIndex, insertedChapter) = insertEpubVolumeChapter(
        book = book,
        kind = VOLUME_KIND_NORMAL,
        volumeTitle = volumeTitle,
        insertIndex = insertIndex.coerceIn(0, book.chapters.size),
        onError = { message -> errorMessage = message }
    ) ?: return EpubPackageTextBodyMutationResult(success = false, message = errorMessage)

    val nextSource = rebuildHtmlWithBodyContent(bodyParts.prefix, nextBody, bodyParts.suffix)
        .toCrlfLineEndings()
    updateEpubPackageText(book, path, nextSource)

    insertedChapter.html = volumeHtml(volumeTitle, selectedLines.drop(1)).toCrlfLineEndings()
    insertedChapter.wordCount = ChapterDetector.countHtmlChars(insertedChapter.html)
    updateEpubChapterHtmlEntry(book, insertedChapter)
    normalizeEpubChapterLineEndingsToCrlf(book, insertedChapter)
    resequenceEpubVolumeFileNames(book, VOLUME_KIND_NORMAL)
    applyVolumeTocLevels(book)
    return EpubPackageTextBodyMutationResult(
        success = true,
        nextBody = nextBody,
        insertedIndex = insertedIndex,
        insertedTitle = insertedChapter.title
    )
}

internal fun deleteEpubPackageTextBodySelectionFromBook(
    book: EpubBook,
    path: String,
    sourceStart: Int,
    sourceEnd: Int
): EpubPackageTextBodyMutationResult {
    val source = epubPackageText(book, path)
        ?: return EpubPackageTextBodyMutationResult(success = false, message = "未找到包内 HTML")
    val bodyParts = htmlBodyContentParts(source)
    val body = bodyParts.body
    val start = sourceStart.coerceIn(0, body.length)
    val end = sourceEnd.coerceIn(start, body.length)
    if (end <= start) {
        return EpubPackageTextBodyMutationResult(success = false, message = "请先选择要删除的文字")
    }
    val nextBody = collapseEpubBodyBlankLineAtSeam(body.replaceRange(start, end, ""), start)
    if (ChapterDetector.stripHtml(nextBody).isBlank()) {
        return EpubPackageTextBodyMutationResult(success = false, message = "删除后当前正文为空")
    }

    val nextSource = rebuildHtmlWithBodyContent(bodyParts.prefix, nextBody, bodyParts.suffix)
        .toCrlfLineEndings()
    updateEpubPackageText(book, path, nextSource)
    return EpubPackageTextBodyMutationResult(success = true, nextBody = nextBody)
}

internal fun wrapEpubPackageTextBodySelectionInBook(
    book: EpubBook,
    path: String,
    sourceStart: Int,
    sourceEnd: Int
): EpubPackageTextBodyMutationResult {
    val source = epubPackageText(book, path)
        ?: return EpubPackageTextBodyMutationResult(success = false, message = "未找到包内 HTML")
    val bodyParts = htmlBodyContentParts(source)
    val result = wrapEpubBodySelectionParagraphs(bodyParts.body, sourceStart, sourceEnd)
    if (!result.success) {
        return EpubPackageTextBodyMutationResult(success = false, message = result.message)
    }
    val nextBody = result.nextBody

    val nextSource = rebuildHtmlWithBodyContent(bodyParts.prefix, nextBody, bodyParts.suffix)
        .toCrlfLineEndings()
    updateEpubPackageText(book, path, nextSource)
    return EpubPackageTextBodyMutationResult(success = true, nextBody = nextBody)
}

private fun EditorController.finishPreparedEpubPackageTextMutation(
    prepared: PreparedEpubMutation<EpubPackageTextBodyMutationResult>,
    target: EpubPackageTextTarget,
    refreshDirectory: Boolean,
    successMessage: String
): Boolean {
    val result = prepared.result
    if (!publishPreparedEpubMutation(prepared)) return false
    if (result.insertedIndex >= 0) {
        previewChapterIndex = result.insertedIndex.coerceIn(0, prepared.book.chapters.lastIndex.coerceAtLeast(0))
    }

    checkReport = null
    markDocumentChanged()
    clearFileRenamePlan()
    val shouldPreserveTextSearch = shouldPreserveEpubTextSearchAfterBodyChange(
        textSearchToolId = textSearchToolId,
        replacementPreviewPresent = replacementFilePreview != null
    )
    if (shouldPreserveTextSearch) {
        clearTextSearchStateAfterBodyTextChange()
    } else {
        clearTextSearchState()
    }
    if (refreshDirectory) refreshChapters(refreshPreview = false)
    previewDisplayChapterIndexOverride = null
    previewTitle = target.title
    previewChapterCount = prepared.book.chapters.size
    previewHighlightChapterIndex = target.sourceIndex
    previewHighlightSourceStart = -1
    previewHighlightSourceEnd = -1
    setPreviewTextFromSource(result.nextBody, target.sourceIndex)
    statusMessage = successMessage
    return true
}

private fun EditorController.currentEpubPackageTextMutationTarget(
    book: EpubBook,
    sourceIndex: Int
): EpubPackageTextTarget? {
    return epubPackageTextReplaceTarget(book, sourceIndex, defaultFetchInfoIntroTarget(book))
}

internal suspend fun EditorController.setEpubPackageTextVolumeFromBodySelectionAsync(
    sourceStart: Int,
    sourceEnd: Int
): Boolean {
    val source = epub ?: run {
        statusMessage = "设为卷仅支持 EPUB"
        return false
    }
    val sourceIndex = currentEpubPackageTextPreviewSourceIndex() ?: run {
        statusMessage = "请先定位包内 HTML 正文"
        return false
    }
    val target = currentEpubPackageTextMutationTarget(source, sourceIndex) ?: run {
        statusMessage = "未找到包内 HTML"
        return false
    }
    val sourceContentVersion = documentContentVersion
    val insertIndex = (previewChapterIndex + 1).coerceIn(0, source.chapters.size)
    val prepared = prepareEpubMutation(source, sourceContentVersion) { book ->
        setEpubPackageTextVolumeFromBodySelectionInBook(
            book = book,
            path = target.path,
            insertIndex = insertIndex,
            sourceStart = sourceStart,
            sourceEnd = sourceEnd
        )
    }
    val result = prepared.result
    if (!result.success) {
        statusMessage = result.message
        return false
    }
    return finishPreparedEpubPackageTextMutation(
        prepared = prepared,
        target = target,
        refreshDirectory = true,
        successMessage = "已设为卷：${result.insertedTitle}"
    )
}

internal suspend fun EditorController.deleteEpubPackageTextBodySelectionAsync(
    sourceStart: Int,
    sourceEnd: Int
): Boolean {
    val source = epub ?: run {
        statusMessage = "删除所选文字仅支持 EPUB"
        return false
    }
    val sourceIndex = currentEpubPackageTextPreviewSourceIndex() ?: run {
        statusMessage = "请先定位包内 HTML 正文"
        return false
    }
    val target = currentEpubPackageTextMutationTarget(source, sourceIndex) ?: run {
        statusMessage = "未找到包内 HTML"
        return false
    }
    val prepared = prepareEpubMutation(source, documentContentVersion) { book ->
        deleteEpubPackageTextBodySelectionFromBook(book, target.path, sourceStart, sourceEnd)
    }
    if (!prepared.result.success) {
        statusMessage = prepared.result.message
        return false
    }
    return finishPreparedEpubPackageTextMutation(
        prepared = prepared,
        target = target,
        refreshDirectory = false,
        successMessage = "已删除所选文字"
    )
}

internal suspend fun EditorController.wrapEpubPackageTextBodySelectionAsync(
    sourceStart: Int,
    sourceEnd: Int
): Boolean {
    val source = epub ?: run {
        statusMessage = "加标签仅支持 EPUB"
        return false
    }
    val sourceIndex = currentEpubPackageTextPreviewSourceIndex() ?: run {
        statusMessage = "请先定位包内 HTML 正文"
        return false
    }
    val target = currentEpubPackageTextMutationTarget(source, sourceIndex) ?: run {
        statusMessage = "未找到包内 HTML"
        return false
    }
    val prepared = prepareEpubMutation(source, documentContentVersion) { book ->
        wrapEpubPackageTextBodySelectionInBook(book, target.path, sourceStart, sourceEnd)
    }
    if (!prepared.result.success) {
        statusMessage = prepared.result.message
        return false
    }
    return finishPreparedEpubPackageTextMutation(
        prepared = prepared,
        target = target,
        refreshDirectory = false,
        successMessage = "已加标签"
    )
}
