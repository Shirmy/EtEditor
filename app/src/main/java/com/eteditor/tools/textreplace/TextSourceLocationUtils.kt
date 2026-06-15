package com.eteditor

import com.eteditor.core.DocumentKind
import com.eteditor.core.TxtDocument

internal data class TextSourceLocation(
    val chapterIndex: Int,
    val sourceStart: Int,
    val sourceEnd: Int
)

internal fun selectedTxtPreviewLocationModel(
    kind: DocumentKind,
    selectedTextSearchResultId: String?,
    textSearchResults: List<TextSearchResult>,
    selectedReplacementPreviewMatchId: String?,
    replacementFilePreview: ReplacementFilePreview?
): TextSourceLocation? {
    if (kind != DocumentKind.Txt) return null
    selectedTextSearchResultId
        ?.let { selectedId -> textSearchResults.firstOrNull { it.id == selectedId } }
        ?.let { result ->
            return TextSourceLocation(
                chapterIndex = result.chapterIndex,
                sourceStart = result.sourceStart,
                sourceEnd = result.sourceEnd
            )
        }
    selectedReplacementPreviewMatchId
        ?.let { selectedId ->
            replacementFilePreview
                ?.let { preview -> preview.multiRules + preview.singleRules }
                ?.flatMap { it.matches }
                ?.firstOrNull { it.id == selectedId }
        }
        ?.let { match ->
            return TextSourceLocation(
                chapterIndex = match.chapterIndex,
                sourceStart = match.sourceStart,
                sourceEnd = match.sourceEnd
            )
        }
    return null
}

internal fun textSearchResultLocationForDocument(
    kind: DocumentKind,
    document: TxtDocument?,
    absoluteStart: Int,
    absoluteEnd: Int,
    fallbackChapterIndex: Int,
    fallbackTitle: String,
    prefaceEndIndex: Int?
): TextSearchResultLocation {
    if (kind != DocumentKind.Txt) {
        return TextSearchResultLocation(fallbackChapterIndex, fallbackTitle)
    }
    if (document == null || document.chapters.isEmpty()) {
        return TextSearchResultLocation(fallbackChapterIndex, fallbackTitle)
    }
    val start = absoluteStart.coerceIn(0, document.text.length)
    val end = (absoluteEnd - 1).coerceIn(start, document.text.length)
    val startLocation = txtChapterLocationForSourceOffset(document, start, prefaceEndIndex)
        ?: return TextSearchResultLocation(fallbackChapterIndex, fallbackTitle)
    val endLocation = txtChapterLocationForSourceOffset(document, end, prefaceEndIndex)
        ?: startLocation
    return if (startLocation.chapterIndex == endLocation.chapterIndex) {
        startLocation
    } else {
        TextSearchResultLocation(
            chapterIndex = startLocation.chapterIndex,
            title = "${startLocation.title} -> ${endLocation.title}"
        )
    }
}

private fun txtChapterLocationForSourceOffset(
    document: TxtDocument,
    sourceOffset: Int,
    prefaceEndIndex: Int?
): TextSearchResultLocation? {
    val offset = sourceOffset.coerceIn(0, document.text.length)
    prefaceEndIndex?.let { prefaceEnd ->
        if (offset < prefaceEnd) {
            return TextSearchResultLocation(TXT_PREFACE_CHAPTER_INDEX, "\u524d\u8a00")
        }
    }
    val chapterIndex = document.chapters.indexOfLast { chapter ->
        chapter.startIndex <= offset
    }.takeIf { it >= 0 } ?: return null
    val chapter = document.chapters.getOrNull(chapterIndex) ?: return null
    return TextSearchResultLocation(
        chapterIndex = chapterIndex,
        title = chapter.title.ifBlank { "\u7b2c ${chapterIndex + 1} \u7ae0" }
    )
}
