package com.eteditor

import com.eteditor.core.DocumentKind
import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument

internal fun EditorController.setPreviewTextFromSource(
    source: String,
    chapterIndex: Int,
    highlightRange: Pair<Int, Int>? = null,
    showFull: Boolean = false
) {
    val sourceHighlightStart = highlightRange?.first ?: previewHighlightSourceStart
    val sourceHighlightEnd = highlightRange?.second ?: previewHighlightSourceEnd
    val hasHighlight = previewHighlightChapterIndex == chapterIndex &&
        sourceHighlightStart in 0..source.length &&
        sourceHighlightEnd in sourceHighlightStart..source.length

    if (!hasHighlight) {
        previewVisibleSourceOffset = 0
        previewVisibleSourceLineOffset = 0
        previewText = if (showFull) source else source.take(PREVIEW_LIMIT)
        previewHighlightStart = -1
        previewHighlightEnd = -1
        return
    }

    val visibleStart = if (showFull || source.length <= PREVIEW_LIMIT) {
        0
    } else {
        (sourceHighlightStart - PREVIEW_CONTEXT_BEFORE).coerceAtLeast(0)
    }
    val visibleEnd = if (showFull) {
        source.length
    } else {
        (visibleStart + PREVIEW_LIMIT).coerceAtMost(source.length)
    }
    previewVisibleSourceOffset = visibleStart
    previewVisibleSourceLineOffset = source
        .take(visibleStart.coerceIn(0, source.length))
        .count { it == '\n' }
    previewText = source.substring(visibleStart, visibleEnd)
    previewHighlightStart = (sourceHighlightStart - visibleStart).coerceIn(0, previewText.length)
    previewHighlightEnd = (sourceHighlightEnd - visibleStart).coerceIn(previewHighlightStart, previewText.length)
}

internal fun EditorController.previewDisplayChapterIndex(): Int {
    return previewDisplayChapterIndexOverride ?: previewChapterIndex
}

internal fun EditorController.previewHeaderTitle(): String {
    return when (kind) {
        DocumentKind.Epub -> {
            if (isEpubPackageTextPreviewSource()) {
                previewTitle
            } else {
                val displayChapterIndex = previewDisplayChapterIndex()
                epub
                    ?.chapters
                    ?.getOrNull(displayChapterIndex)
                    ?.title
                    ?.ifBlank { "无标题" }
                    ?: previewTitle
            }
        }
        DocumentKind.Txt -> {
            val document = txt ?: return previewTitle
            if (txtPreviewMode == TXT_PREVIEW_MODE_FULL) {
                "TXT 全文"
            } else if (document.chapters.isEmpty()) {
                "TXT 正文预览"
            } else if (previewChapterIndex < 0 && txtHasPreface()) {
                "前言"
            } else {
                document.chapters
                    .getOrNull(previewChapterIndex)
                    ?.title
                    ?.ifBlank { "无标题" }
                    ?: previewTitle
            }
        }
        DocumentKind.None -> previewTitle
    }
}

private fun EditorController.mappedTxtChapterPreviewSource(
    document: TxtDocument,
    chapter: TxtChapter,
    chapterIndex: Int
): TxtChapterPreviewSource {
    val start = chapter.startIndex.coerceIn(0, document.text.length)
    val end = chapter.endIndex.coerceIn(start, document.text.length)
    val source = document.text.substring(start, end)
    if (source.isEmpty()) return TxtChapterPreviewSource(source)
    val rawLineEnd = source.indexOf('\n').let { if (it < 0) source.length else it }
    val titleLineEnd = if (rawLineEnd > 0 && source[rawLineEnd - 1] == '\r') {
        rawLineEnd - 1
    } else {
        rawLineEnd
    }
    val mappedTitle = chapter.title.takeIf { it.isNotBlank() } ?: return TxtChapterPreviewSource(source)
    val rawTitle = source.substring(0, titleLineEnd)
    if (rawTitle == mappedTitle) return TxtChapterPreviewSource(source)

    val mappedSource = mappedTitle + source.substring(titleLineEnd)
    val highlightRange = mappedTxtChapterPreviewHighlightRange(
        chapterIndex = chapterIndex,
        originalTitleLength = titleLineEnd,
        mappedTitleLength = mappedTitle.length,
        mappedSourceLength = mappedSource.length
    )
    return TxtChapterPreviewSource(mappedSource, highlightRange)
}

private fun EditorController.mappedTxtChapterPreviewHighlightRange(
    chapterIndex: Int,
    originalTitleLength: Int,
    mappedTitleLength: Int,
    mappedSourceLength: Int
): Pair<Int, Int>? {
    if (previewHighlightChapterIndex != chapterIndex) return null
    if (previewHighlightSourceStart < 0 || previewHighlightSourceEnd < previewHighlightSourceStart) return null
    val delta = mappedTitleLength - originalTitleLength
    val mappedStart = if (previewHighlightSourceStart < originalTitleLength) {
        0
    } else {
        previewHighlightSourceStart + delta
    }
    val mappedEnd = if (previewHighlightSourceEnd <= originalTitleLength) {
        mappedTitleLength
    } else {
        previewHighlightSourceEnd + delta
    }
    return mappedStart.coerceIn(0, mappedSourceLength) to
        mappedEnd.coerceIn(mappedStart.coerceIn(0, mappedSourceLength), mappedSourceLength)
}

internal fun EditorController.refreshPreview() {
    when (kind) {
        DocumentKind.Epub -> {
            val book = epub
            if (book == null || book.chapters.isEmpty()) {
                previewChapterIndex = 0
                previewChapterCount = 0
                previewVisibleSourceOffset = 0
                previewVisibleSourceLineOffset = 0
                previewTitle = "EPUB 正文预览"
                previewText = ""
                previewHighlightStart = -1
                previewHighlightEnd = -1
            } else {
                previewChapterIndex = previewChapterIndex.coerceIn(0, book.chapters.lastIndex)
                val displayChapterIndex = previewDisplayChapterIndexOverride
                    ?.takeIf { it in book.chapters.indices }
                    ?: previewChapterIndex.also {
                        if (previewDisplayChapterIndexOverride != null) {
                            previewDisplayChapterIndexOverride = null
                        }
                    }
                previewChapterCount = book.chapters.size
                val chapter = book.chapters[displayChapterIndex]
                previewTitle = chapter.title.ifBlank { "无标题" }
                setPreviewTextFromSource(epubVisibleBodyParts(chapter.html).body, displayChapterIndex, showFull = true)
            }
        }
        DocumentKind.Txt -> {
            val document = txt
            if (document == null) {
                previewVisibleSourceOffset = 0
                previewVisibleSourceLineOffset = 0
                previewChapterCount = 0
                previewTitle = "TXT 正文预览"
                previewText = ""
                previewHighlightStart = -1
                previewHighlightEnd = -1
            } else if (txtPreviewMode == TXT_PREVIEW_MODE_FULL) {
                previewVisibleSourceOffset = 0
                previewVisibleSourceLineOffset = 0
                val minIndex = if (txtHasPreface()) -1 else 0
                previewChapterIndex = previewChapterIndex.coerceIn(
                    minIndex,
                    document.chapters.lastIndex.coerceAtLeast(0)
                )
                previewChapterCount = document.chapters.size + if (txtHasPreface()) 1 else 0
                previewTitle = "TXT 全文"
                previewText = txtFullPreviewText()
                previewHighlightStart = -1
                previewHighlightEnd = -1
            } else if (document.chapters.isEmpty()) {
                previewChapterIndex = 0
                previewChapterCount = 0
                previewTitle = "TXT 正文预览"
                setPreviewTextFromSource(document.text, 0)
            } else if ((previewDisplayChapterIndexOverride ?: previewChapterIndex) < 0 && txtHasPreface()) {
                val firstChapter = txtFirstChapterByTextOrder(document) ?: document.chapters.first()
                val end = firstChapter.startIndex
                previewChapterCount = document.chapters.size + 1
                previewTitle = "前言"
                setPreviewTextFromSource(document.text.substring(0, end.coerceAtMost(document.text.length)), -1, showFull = true)
            } else {
                previewChapterIndex = previewChapterIndex.coerceIn(0, document.chapters.lastIndex)
                val displayChapterIndex = previewDisplayChapterIndexOverride
                    ?.takeIf { it in document.chapters.indices }
                    ?: previewChapterIndex.also {
                        if (previewDisplayChapterIndexOverride != null) {
                            previewDisplayChapterIndexOverride = null
                        }
                    }
                previewChapterCount = document.chapters.size + if (txtHasPreface()) 1 else 0
                val chapter = document.chapters[displayChapterIndex]
                previewTitle = chapter.title.ifBlank { "无标题" }
                val source = mappedTxtChapterPreviewSource(document, chapter, displayChapterIndex)
                setPreviewTextFromSource(source.text, displayChapterIndex, source.highlightRange, showFull = true)
            }
        }
        DocumentKind.None -> {
            previewVisibleSourceOffset = 0
            previewVisibleSourceLineOffset = 0
            previewChapterCount = 0
            previewTitle = ""
            previewText = ""
            previewHighlightStart = -1
            previewHighlightEnd = -1
        }
    }
}

internal fun EditorController.epubVisibleBodyParts(html: String): HtmlBodyContentParts {
    epubVisibleBodyCache?.let { (cachedHtml, cachedParts) ->
        if (cachedHtml === html) return cachedParts
    }
    val parts = htmlBodyContentParts(html)
    epubVisibleBodyCache = html to parts
    return parts
}

internal fun EditorController.locateEpubPreviewAtBodyOffset(chapterIndex: Int, bodyOffset: Int) {
    val book = epub ?: return
    val chapter = book.chapters.getOrNull(chapterIndex) ?: return
    val body = epubVisibleBodyParts(chapter.html).body
    val safeOffset = bodyOffset.coerceIn(0, body.length)
    previewHighlightChapterIndex = chapterIndex
    previewHighlightSourceStart = safeOffset
    previewHighlightSourceEnd = (safeOffset + 1).coerceAtMost(body.length)
    refreshPreview()
}
