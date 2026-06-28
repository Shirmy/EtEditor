package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.DocumentKind
import com.eteditor.core.EpubBook
import com.eteditor.core.TxtDocument

internal fun lineEndingLabel(text: String): String {
    val accumulator = LineEndingAccumulator()
    accumulator.scan(text)
    return accumulator.label()
}

internal fun bodyLineEndingHintForDocument(
    kind: DocumentKind,
    epub: EpubBook?,
    txt: TxtDocument?,
    previewChapterIndex: Int
): String {
    val full = fullBodyLineEndingLabelForDocument(kind, epub, txt)
    if (!full.startsWith("混合")) return "全文：$full"
    return "全文：$full | 本章：${lineEndingLabel(currentBodyLineEndingSampleForDocument(kind, epub, txt, previewChapterIndex))}"
}

// Computes the whole-document line-ending label WITHOUT concatenating the entire book into one
// huge string: EPUB chapters are scanned one at a time, and scanning stops as soon as two distinct
// line-ending kinds appear (the label is already "混合" and cannot change). This mirrors the lighter,
// per-chapter style the background word-count already uses, instead of building a giant text.
private fun fullBodyLineEndingLabelForDocument(
    kind: DocumentKind,
    epub: EpubBook?,
    txt: TxtDocument?
): String {
    val accumulator = LineEndingAccumulator()
    when (kind) {
        DocumentKind.Epub -> {
            for (chapter in epub?.chapters.orEmpty()) {
                accumulator.scan(ChapterDetector.extractBodyMarkup(chapter.html))
                if (accumulator.isMixed) break
            }
        }
        DocumentKind.Txt -> accumulator.scan(txt?.text.orEmpty())
        DocumentKind.None -> {}
    }
    return accumulator.label()
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

// Accumulates which line-ending kinds (CRLF / CR / LF) appear across one or more text segments.
// Scanning can stop early once two distinct kinds are seen, because the resulting label is then
// already "混合" and further scanning cannot change it.
private class LineEndingAccumulator {
    private var hasCrlf = false
    private var hasCr = false
    private var hasLf = false

    val isMixed: Boolean
        get() = distinctKindCount() >= 2

    fun scan(text: String) {
        if (isMixed) return
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
            if (isMixed) return
        }
    }

    fun label(): String {
        return when (distinctKindCount()) {
            0 -> "无"
            1 -> when {
                hasCrlf -> "CRLF"
                hasCr -> "CR"
                else -> "LF"
            }
            else -> "混合"
        }
    }

    private fun distinctKindCount(): Int {
        var count = 0
        if (hasCrlf) count += 1
        if (hasCr) count += 1
        if (hasLf) count += 1
        return count
    }
}
