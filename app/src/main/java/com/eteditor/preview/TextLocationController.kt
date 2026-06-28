package com.eteditor

import com.eteditor.core.DocumentKind

fun EditorController.selectTextSearchResult(resultId: String): Boolean {
    val result = textSearchResults.firstOrNull { it.id == resultId } ?: return false
    selectedTextSearchResultId = result.id
    if (kind == DocumentKind.Txt) {
        return selectTxtTextLocation(result.chapterIndex, result.sourceStart, result.sourceEnd, result.chapterTitle)
    }
    if (result.chapterIndex < 0) {
        return selectEpubPackageTextLocation(
            result.chapterIndex,
            result.sourceStart,
            result.sourceEnd,
            result.chapterTitle
        )
    }
    return selectEpubTextLocation(result.chapterIndex, result.sourceStart, result.sourceEnd, result.chapterTitle)
}

fun EditorController.selectReplacementPreviewMatch(matchId: String): Boolean {
    val preview = replacementFilePreview ?: return false
    val match = (preview.multiRules + preview.singleRules)
        .flatMap { it.matches }
        .firstOrNull { it.id == matchId }
        ?: return false
    selectedReplacementPreviewMatchId = match.id
    selectedTextSearchResultId = null
    if (kind == DocumentKind.Txt) {
        return selectTxtTextLocation(match.chapterIndex, match.sourceStart, match.sourceEnd, match.chapterTitle)
    }
    if (match.chapterIndex < 0) {
        return selectEpubPackageTextLocation(match.chapterIndex, match.sourceStart, match.sourceEnd, match.chapterTitle)
    }
    return selectEpubTextLocation(match.chapterIndex, match.sourceStart, match.sourceEnd, match.chapterTitle)
}

private fun EditorController.selectEpubPackageTextLocation(
    sourceIndex: Int,
    sourceStart: Int,
    sourceEnd: Int,
    title: String
): Boolean {
    val chapterIndex = epubChapterIndexForPackageTextSource(sourceIndex)
    if (chapterIndex != null) {
        return selectEpubTextLocation(chapterIndex, sourceStart, sourceEnd, title)
    }
    previewEpubPackageTextReplaceSource(sourceIndex, sourceStart, sourceEnd) ?: return false
    statusMessage = "已定位：$title"
    return true
}

private fun EditorController.selectEpubTextLocation(
    chapterIndex: Int,
    sourceStart: Int,
    sourceEnd: Int,
    title: String
): Boolean {
    val book = epub ?: return false
    val chapter = book.chapters.getOrNull(chapterIndex) ?: return false
    val highlightRange = htmlVisibleBodyRelativeRange(epubVisibleBodyParts(chapter.html), sourceStart, sourceEnd) ?: return false
    previewChapterIndex = chapterIndex
    previewDisplayChapterIndexOverride = chapterIndex
    previewHighlightChapterIndex = chapterIndex
    previewHighlightSourceStart = highlightRange.first
    previewHighlightSourceEnd = highlightRange.second
    refreshPreview()
    statusMessage = "已定位：$title"
    return true
}

private fun EditorController.epubChapterIndexForPackageTextSource(sourceIndex: Int): Int? {
    val book = epub ?: return null
    val target = epubPackageTextReplaceTarget(sourceIndex) ?: return null
    val targetPath = normalizeEpubPath(target.path).lowercase()
    return book.chapters.indexOfFirst { chapter ->
        (chapter.pathAliases + chapter.path + chapter.originalPath).any { path ->
            normalizeEpubPath(path).lowercase() == targetPath
        }
    }.takeIf { it >= 0 }
}

internal fun EditorController.selectTxtTextLocation(
    chapterIndex: Int,
    absoluteStart: Int,
    absoluteEnd: Int,
    title: String
): Boolean {
    val document = txt ?: return false
    if (absoluteStart < 0 || absoluteEnd <= absoluteStart || absoluteStart > document.text.length) return false
    if (document.chapters.isEmpty()) {
        previewChapterIndex = 0
        txtFullPreviewCachedAnchor = TxtFullPreviewAnchor(
            offset = absoluteStart.coerceIn(0, document.text.length),
            lineIndex = countLineBreaksBefore(document.text, absoluteStart.coerceIn(0, document.text.length))
        )
        previewDisplayChapterIndexOverride = 0
        previewHighlightChapterIndex = 0
        previewHighlightSourceStart = absoluteStart.coerceIn(0, document.text.length)
        previewHighlightSourceEnd = absoluteEnd.coerceIn(previewHighlightSourceStart, document.text.length)
        refreshPreview()
        statusMessage = "已定位：${title.ifBlank { document.originalName }}"
        return true
    }

    txtPrefaceEndIndex(document)?.let { prefaceEnd ->
        if (absoluteStart < prefaceEnd) {
            previewChapterIndex = TXT_PREFACE_CHAPTER_INDEX
            txtFullPreviewCachedAnchor = TxtFullPreviewAnchor(
                offset = absoluteStart.coerceIn(0, prefaceEnd),
                lineIndex = countLineBreaksBefore(document.text, absoluteStart.coerceIn(0, prefaceEnd))
            )
            previewDisplayChapterIndexOverride = TXT_PREFACE_CHAPTER_INDEX
            previewHighlightChapterIndex = TXT_PREFACE_CHAPTER_INDEX
            previewHighlightSourceStart = absoluteStart.coerceIn(0, prefaceEnd)
            previewHighlightSourceEnd = absoluteEnd.coerceIn(previewHighlightSourceStart, prefaceEnd)
            refreshPreview()
            statusMessage = "已定位：${title.ifBlank { "前言" }}"
            return true
        }
    }

    val fallbackIndex = document.chapters.indexOfLast { chapter ->
        chapter.startIndex <= absoluteStart
    }
    val resolvedIndex = chapterIndex
        .takeIf { it in document.chapters.indices }
        ?.takeIf { index ->
            val chapter = document.chapters[index]
            absoluteStart in chapter.startIndex..chapter.endIndex.coerceAtLeast(chapter.startIndex)
        }
        ?: fallbackIndex
    val chapter = document.chapters.getOrNull(resolvedIndex) ?: return false
    val chapterStart = chapter.startIndex.coerceIn(0, document.text.length)
    val chapterEnd = chapter.endIndex.coerceIn(chapterStart, document.text.length)
    previewChapterIndex = txtCurrentChapterIndexForLocation(document, chapterIndex, absoluteStart)
    txtFullPreviewCachedAnchor = TxtFullPreviewAnchor(
        offset = chapterStart,
        lineIndex = chapter.lineIndex.coerceAtLeast(0)
    )
    previewDisplayChapterIndexOverride = resolvedIndex
    previewHighlightChapterIndex = resolvedIndex
    previewHighlightSourceStart = (absoluteStart - chapterStart).coerceIn(0, chapterEnd - chapterStart)
    previewHighlightSourceEnd = (absoluteEnd - chapterStart).coerceIn(previewHighlightSourceStart, chapterEnd - chapterStart)
    refreshPreview()
    statusMessage = "已定位：${title.ifBlank { chapter.title }}"
    return true
}
