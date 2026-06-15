package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.TextCodec
import com.eteditor.core.TxtDocument

internal fun txtSubtitle(document: TxtDocument): String {
    val wordCount = ChapterDetector.countVisibleChars(document.text)
    val byteCount = TextCodec.encode(document.text, document.encoding).first.size
    val issueCount = document.chapters.sumOf { chapter -> chapter.status.size }
    return "${document.encoding} | ${lineEndingLabel(document.text)} | $wordCount | ${compactByteSize(byteCount)} | 章节 ${document.chapters.size}" +
        if (issueCount > 0) " | 问题 $issueCount" else ""
}
