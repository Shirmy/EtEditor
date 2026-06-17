package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.DocumentKind
import com.eteditor.core.TxtDocument

fun EditorController.txtHasPreface(): Boolean {
    val document = txt ?: return false
    return txtPrefaceEndIndex(document) != null
}

fun EditorController.txtPrefaceWordCount(): Int {
    val document = txt ?: return 0
    val prefaceEnd = txtPrefaceEndIndex(document) ?: return 0
    return ChapterDetector.countVisibleChars(document.text.substring(0, prefaceEnd))
}

internal fun txtCurrentChapterIndexForLocation(
    document: TxtDocument,
    requestedChapterIndex: Int,
    absoluteStart: Int
): Int {
    if (document.chapters.isEmpty()) return 0
    val safeStart = absoluteStart.coerceIn(0, document.text.length)
    txtPrefaceEndIndex(document)?.let { prefaceEnd ->
        if (safeStart < prefaceEnd) {
            return TXT_PREFACE_CHAPTER_INDEX
        }
    }
    val resolvedIndex = requestedChapterIndex
        .takeIf { it in document.chapters.indices }
        ?.takeIf { index ->
            val chapter = document.chapters[index]
            safeStart in chapter.startIndex..chapter.endIndex.coerceAtLeast(chapter.startIndex)
        }
        ?: document.chapters.indexOfLast { chapter -> chapter.startIndex <= safeStart }
    return resolvedIndex.takeIf { it >= 0 } ?: 0
}

internal fun EditorController.currentTxtPreviewSourceAnchorOffset(document: TxtDocument): Int {
    selectedTxtPreviewLocation()?.let { location ->
        return location.sourceStart.coerceIn(0, document.text.length)
    }
    if (txtPreviewMode == TXT_PREVIEW_MODE_FULL) {
        return txtFullPreviewAnchorOffset(document)
    }
    val displayChapterIndex = previewDisplayChapterIndex()
    if (document.chapters.isEmpty() || displayChapterIndex < 0) {
        return previewVisibleSourceOffset.coerceIn(0, document.text.length)
    }
    val chapterStart = document.chapters
        .getOrNull(displayChapterIndex)
        ?.startIndex
        ?.coerceIn(0, document.text.length)
        ?: return 0
    return (chapterStart + previewVisibleSourceOffset.coerceAtLeast(0))
        .coerceIn(0, document.text.length)
}

internal fun EditorController.restoreTxtPreviewPositionForSourceOffset(document: TxtDocument, sourceOffset: Int) {
    val selectedLocation = selectedTxtPreviewLocation()
    val safeOffset = (selectedLocation?.sourceStart ?: sourceOffset).coerceIn(0, document.text.length)
    if (txtPreviewMode == TXT_PREVIEW_MODE_FULL) {
        txtFullPreviewCachedAnchor = TxtFullPreviewAnchor(
            offset = safeOffset,
            lineIndex = countLineBreaksBefore(document.text, safeOffset)
        )
    }

    val shouldKeepHighlight = selectedLocation != null
    fun setPreviewPosition(index: Int) {
        if (shouldKeepHighlight) {
            previewDisplayChapterIndexOverride = index
        } else {
            previewChapterIndex = index
        }
    }
    if (document.text.isEmpty()) {
        setPreviewPosition(0)
        clearPreviewHighlightRangeOnly()
        return
    }
    val anchorStart = if (safeOffset >= document.text.length) {
        document.text.lastIndex
    } else {
        safeOffset
    }.coerceAtLeast(0)
    val anchorEnd = selectedLocation
        ?.sourceEnd
        ?.coerceIn(anchorStart, document.text.length)
        ?: anchorStart

    if (document.chapters.isEmpty()) {
        setPreviewPosition(0)
        if (shouldKeepHighlight || (txtPreviewMode != TXT_PREVIEW_MODE_FULL && anchorStart > 0)) {
            setPreviewSourceHighlight(0, anchorStart, anchorEnd)
        } else {
            clearPreviewHighlightRangeOnly()
        }
        return
    }

    txtPrefaceEndIndex(document)?.let { prefaceEnd ->
        if (anchorStart < prefaceEnd) {
            setPreviewPosition(TXT_PREFACE_CHAPTER_INDEX)
            if (shouldKeepHighlight || (txtPreviewMode != TXT_PREVIEW_MODE_FULL && anchorStart > 0)) {
                setPreviewSourceHighlight(
                    TXT_PREFACE_CHAPTER_INDEX,
                    anchorStart.coerceIn(0, prefaceEnd),
                    anchorEnd.coerceIn(anchorStart.coerceIn(0, prefaceEnd), prefaceEnd)
                )
            } else {
                clearPreviewHighlightRangeOnly()
            }
            return
        }
    }

    val resolvedIndex = document.chapters
        .indexOfLast { chapter -> chapter.startIndex <= anchorStart }
        .takeIf { it >= 0 }
        ?: 0
    val chapter = document.chapters.getOrNull(resolvedIndex) ?: run {
        setPreviewPosition(0)
        clearPreviewHighlightRangeOnly()
        return
    }
    val chapterStart = chapter.startIndex.coerceIn(0, document.text.length)
    val chapterEnd = chapter.endIndex.coerceIn(chapterStart, document.text.length)
    val relativeStart = (anchorStart - chapterStart).coerceIn(0, chapterEnd - chapterStart)
    val relativeEnd = (anchorEnd - chapterStart).coerceIn(relativeStart, chapterEnd - chapterStart)
    setPreviewPosition(resolvedIndex)
    if (shouldKeepHighlight || (txtPreviewMode != TXT_PREVIEW_MODE_FULL && relativeStart > 0)) {
        setPreviewSourceHighlight(resolvedIndex, relativeStart, relativeEnd)
    } else {
        clearPreviewHighlightRangeOnly()
    }
}

private fun EditorController.setPreviewSourceHighlight(chapterIndex: Int, sourceStart: Int, sourceEnd: Int) {
    previewHighlightChapterIndex = chapterIndex
    previewHighlightSourceStart = sourceStart
    previewHighlightSourceEnd = sourceEnd
}

private fun EditorController.clearPreviewHighlightRangeOnly() {
    previewHighlightChapterIndex = null
    previewHighlightSourceStart = -1
    previewHighlightSourceEnd = -1
    previewHighlightStart = -1
    previewHighlightEnd = -1
}

fun EditorController.txtPreviewTargetOffset(): Int {
    val document = txt ?: return 0
    val displayChapterIndex = previewDisplayChapterIndex()
    if (displayChapterIndex < 0) return 0
    val chapter = document.chapters.getOrNull(displayChapterIndex) ?: return 0
    return chapter.startIndex.coerceIn(0, document.text.length)
}

internal fun EditorController.txtPreviewAnchor(): TxtFullPreviewAnchor {
    val document = txt ?: return TxtFullPreviewAnchor(offset = 0, lineIndex = 0)
    val displayChapterIndex = previewDisplayChapterIndex()
    if (displayChapterIndex < 0) return TxtFullPreviewAnchor(offset = 0, lineIndex = 0)
    val chapter = document.chapters.getOrNull(displayChapterIndex)
        ?: return TxtFullPreviewAnchor(offset = 0, lineIndex = 0)
    return TxtFullPreviewAnchor(
        offset = chapter.startIndex.coerceIn(0, document.text.length),
        lineIndex = chapter.lineIndex.coerceAtLeast(0)
    )
}

fun EditorController.txtFullPreviewTargetOffset(): Int? {
    val document = txt ?: return null
    val anchor = txtFullPreviewCachedAnchor ?: return null
    val window = txtFullPreviewWindow()
    return (anchor.offset.coerceIn(0, document.text.length) - window.startOffset)
        .coerceIn(0, window.text.length)
}

fun EditorController.txtFullPreviewTargetLineIndex(): Int? {
    val anchor = txtFullPreviewCachedAnchor ?: return null
    val window = txtFullPreviewWindow()
    return anchor
        .lineIndex
        .minus(window.startLineIndex)
        .coerceAtLeast(0)
}

fun EditorController.txtFullPreviewText(): String {
    return txtFullPreviewWindow().text
}

fun EditorController.shouldUseTxtFullEditWindow(): Boolean {
    return kind == DocumentKind.Txt &&
        txtPreviewMode == TXT_PREVIEW_MODE_FULL &&
        txtRequiresFullEditWindow(txt?.text?.length ?: 0)
}

fun EditorController.txtFullEditWindowSeed(anchorSourceOffset: Int? = null): TxtFullEditWindowSeed {
    val document = txt ?: return TxtFullEditWindowSeed("", 0, 0, 0, 0, false)
    val source = document.text
    val anchorOffset = anchorSourceOffset?.coerceIn(0, source.length)
        ?: txtFullPreviewAnchorOffset(document)
    return buildTxtFullEditWindowSeed(source, anchorOffset)
}

fun EditorController.updateTxtFullEditWindowText(text: String, sourceStart: Int, sourceEnd: Int): Boolean {
    if (warnTxtMoveChapterSyncPending("编辑全文")) return false
    val document = txt ?: return false
    val start = sourceStart.coerceIn(0, document.text.length)
    val end = sourceEnd.coerceIn(start, document.text.length)
    val current = document.text.substring(start, end)
    if (current == text) {
        statusMessage = "正文未修改"
        return true
    }
    document.text = buildString(document.text.length - (end - start) + text.length) {
        append(document.text, 0, start)
        append(text)
        append(document.text, end, document.text.length)
    }
    document.chapters = detectCurrentTxtChapters(document.text)
    checkReport = null
    markDocumentChanged()
    clearTextSearchState()
    refreshPreview()
    statusMessage = "全文窗口已更新，正在重建目录"
    startTxtCatalogDetection(
        document,
        documentSessionKey,
        "TXT 目录重建完成",
        applyCatalogMappings = false
    )
    return true
}

fun EditorController.txtFullPreviewState(): TxtFullPreviewState {
    val document = txt
    val window = txtFullPreviewWindow()
    val anchor = if (document == null) null else txtFullPreviewCachedAnchor
    val highlightRange = txtFullPreviewHighlightRange(window)
    return buildTxtFullPreviewState(
        sourceLength = document?.text?.length,
        window = window,
        anchor = anchor,
        highlightRange = highlightRange
    )
}

fun EditorController.txtFullPreviewWindowKey(): String {
    val window = txtFullPreviewWindow()
    return "${window.startOffset}:${window.endOffset}"
}

fun EditorController.txtFullPreviewStartLineIndex(): Int {
    return txtFullPreviewWindow().startLineIndex
}

fun EditorController.txtFullPreviewHighlightRange(): Pair<Int, Int>? {
    return txtFullPreviewHighlightRange(txtFullPreviewWindow())
}

private fun EditorController.txtFullPreviewHighlightRange(window: TxtFullPreviewWindow): Pair<Int, Int>? {
    return txtFullPreviewHighlightRange(
        window = window,
        selectedLocation = selectedTxtPreviewLocation()
    )
}

private fun EditorController.txtFullPreviewWindow(): TxtFullPreviewWindow {
    val document = txt ?: return TxtFullPreviewWindow("", 0, 0, 0)
    val source = document.text
    val anchorOffset = txtFullPreviewAnchorOffset(document)
    return buildTxtFullPreviewWindow(source, anchorOffset)
}

private fun EditorController.txtFullPreviewAnchorOffset(document: TxtDocument): Int {
    return txtFullPreviewAnchorOffset(
        document = document,
        selectedLocation = selectedTxtPreviewLocation(),
        cachedAnchor = txtFullPreviewCachedAnchor,
        previewChapterIndex = previewDisplayChapterIndex()
    )
}

fun EditorController.txtPreviewTargetLineIndex(): Int {
    val document = txt ?: return 0
    txtPreviewSelectedLineHighlight()?.let { return it.lineIndex }
    if (document.chapters.isEmpty()) return 0
    val displayChapterIndex = previewDisplayChapterIndex()
    if (displayChapterIndex < 0) return 0
    return document.chapters.getOrNull(displayChapterIndex)?.lineIndex ?: 0
}

fun EditorController.txtPreviewSelectedLineHighlight(): TxtPreviewLineHighlight? {
    val document = txt ?: return null
    return txtPreviewSelectedLineHighlight(
        document = document,
        selectedLocation = selectedTxtPreviewLocation(),
        fullPreviewMode = txtPreviewMode == TXT_PREVIEW_MODE_FULL,
        prefaceEndIndex = txtPrefaceEndIndex(document)
    )
}

private fun EditorController.selectedTxtPreviewLocation(): TextSourceLocation? {
    return selectedTxtPreviewLocationModel(
        kind = kind,
        selectedTextSearchResultId = selectedTextSearchResultId,
        textSearchResults = textSearchResults,
        selectedReplacementPreviewMatchId = selectedReplacementPreviewMatchId,
        replacementFilePreview = replacementFilePreview
    )
}

fun EditorController.txtPreviewStartLineIndex(): Int {
    val document = txt ?: return 0
    if (txtPreviewMode == TXT_PREVIEW_MODE_FULL) return txtFullPreviewStartLineIndex()
    val displayChapterIndex = previewDisplayChapterIndex()
    if (displayChapterIndex < 0) return 0
    return document.chapters.getOrNull(displayChapterIndex)?.lineIndex ?: 0
}

fun EditorController.txtPreviewVisibleStartLineIndex(): Int {
    return txtPreviewStartLineIndex() + previewVisibleSourceLineOffset
}

fun EditorController.epubPreviewVisibleStartLineIndex(): Int {
    return previewVisibleSourceLineOffset
}

fun EditorController.previewVisibleSourceOffsetValue(): Int {
    return previewVisibleSourceOffset
}

fun EditorController.showStatus(message: String) {
    statusMessage = message
}

fun EditorController.txtPreviewSourceOffsetFromVisibleOffset(visibleOffset: Int): Int {
    val document = txt ?: return 0
    val safeVisibleOffset = visibleOffset.coerceAtLeast(0)
    if (txtPreviewMode == TXT_PREVIEW_MODE_FULL) {
        val window = txtFullPreviewWindow()
        return (window.startOffset + safeVisibleOffset).coerceIn(0, document.text.length)
    }
    val displayChapterIndex = previewDisplayChapterIndex()
    if (document.chapters.isEmpty() || displayChapterIndex < 0) {
        return (previewVisibleSourceOffset + safeVisibleOffset).coerceIn(0, document.text.length)
    }
    val chapter = document.chapters.getOrNull(displayChapterIndex) ?: return 0
    val chapterStart = chapter.startIndex.coerceIn(0, document.text.length)
    val chapterEnd = chapter.endIndex.coerceIn(chapterStart, document.text.length)
    val visibleSourceOffset = mappedTxtChapterPreviewOffsetToSourceOffset(
        document.text.substring(chapterStart, chapterEnd),
        chapter.title,
        previewVisibleSourceOffset + safeVisibleOffset
    )
    return (chapterStart + visibleSourceOffset)
        .coerceIn(0, document.text.length)
}

fun EditorController.txtEditableBodyOffsetFromSourceOffset(sourceOffset: Int): Int {
    val document = txt ?: return 0
    val safeSourceOffset = sourceOffset.coerceIn(0, document.text.length)
    if (txtPreviewMode == TXT_PREVIEW_MODE_FULL || document.chapters.isEmpty()) {
        return safeSourceOffset
    }
    val displayChapterIndex = previewDisplayChapterIndex()
    if (displayChapterIndex < 0) return safeSourceOffset
    val chapterStart = document.chapters
        .getOrNull(displayChapterIndex)
        ?.startIndex
        ?.coerceIn(0, document.text.length)
        ?: 0
    return (safeSourceOffset - chapterStart).coerceAtLeast(0)
}

fun EditorController.txtSourceLineIndexForOffset(sourceOffset: Int): Int {
    val document = txt ?: return 0
    return countLineBreaksBefore(document.text, sourceOffset.coerceIn(0, document.text.length))
}
