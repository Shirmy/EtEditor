package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.DocumentKind
import com.eteditor.core.EpubBook
import com.eteditor.core.TxtDocument

internal fun lineEndingLabel(text: String): String {
    val kinds = lineEndingKinds(text)
    return when (kinds.size) {
        0 -> "无"
        1 -> kinds.first()
        else -> "混合"
    }
}

internal fun bodyLineEndingHintForDocument(
    kind: DocumentKind,
    epub: EpubBook?,
    txt: TxtDocument?,
    previewChapterIndex: Int
): String {
    val full = lineEndingLabel(fullBodyLineEndingSampleForDocument(kind, epub, txt))
    if (!full.startsWith("混合")) return "全文：$full"
    return "全文：$full | 本章：${lineEndingLabel(currentBodyLineEndingSampleForDocument(kind, epub, txt, previewChapterIndex))}"
}

private fun fullBodyLineEndingSampleForDocument(kind: DocumentKind, epub: EpubBook?, txt: TxtDocument?): String {
    return when (kind) {
        DocumentKind.Epub -> epub?.chapters
            ?.joinToString("") { chapter -> ChapterDetector.extractBodyMarkup(chapter.html) }
            .orEmpty()
        DocumentKind.Txt -> txt?.text.orEmpty()
        DocumentKind.None -> ""
    }
}

private fun currentBodyLineEndingSampleForDocument(
    kind: DocumentKind,
    epub: EpubBook?,
    txt: TxtDocument?,
    previewChapterIndex: Int
): String {
    return when (kind) {
        DocumentKind.Epub -> {
            val chapter = epub?.chapters?.getOrNull(previewChapterIndex)
                ?: epub?.chapters?.firstOrNull()
                ?: return ""
            ChapterDetector.extractBodyMarkup(chapter.html)
        }
        DocumentKind.Txt -> {
            val document = txt ?: return ""
            val chapter = document.chapters.getOrNull(previewChapterIndex) ?: return document.text
            val offsets = txtLineOffsets(document.text)
            val start = offsets.getOrNull(chapter.lineIndex) ?: return document.text
            val end = offsets.getOrNull(chapter.endLineIndex) ?: document.text.length
            document.text.substring(start.coerceIn(0, document.text.length), end.coerceIn(0, document.text.length))
        }
        DocumentKind.None -> ""
    }
}

private fun lineEndingKinds(text: String): List<String> {
    var hasCrlf = false
    var hasLf = false
    var hasCr = false
    var index = 0
    while (index < text.length) {
        when (text[index]) {
            '\r' -> {
                if (index + 1 < text.length && text[index + 1] == '\n') {
                    hasCrlf = true
                    index += 2
                } else {
                    hasCr = true
                    index += 1
                }
            }
            '\n' -> {
                hasLf = true
                index += 1
            }
            else -> index += 1
        }
    }
    return buildList {
        if (hasCrlf) add("CRLF")
        if (hasCr) add("CR")
        if (hasLf) add("LF")
    }
}
