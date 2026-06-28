package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.TextCodec
import com.eteditor.core.TxtDocument

internal fun txtSubtitle(document: TxtDocument): String {
    val stats = txtBodyStatsCache.statsFor(document.text, document.encoding)
    val issueCount = document.chapters.sumOf { chapter -> chapter.status.size }
    return "${document.encoding} | ${stats.lineEnding} | ${stats.wordCount} | ${compactByteSize(stats.byteCount)} | 章节 ${document.chapters.size}" +
        if (issueCount > 0) " | 问题 $issueCount" else ""
}

// The whole-text figures (字数 / 体积 / 换行类型) depend only on the body text (体积 also on encoding),
// not on chapter/issue counts. They used to be recomputed on every status-bar refresh — scanning and
// re-encoding the entire book even when the text had not changed (e.g. selecting a chapter, refreshing
// the catalog, UI recomposition). Cache them keyed by the text reference: an edit produces a new String
// (cache miss -> recompute), while a refresh without a text change reuses the previous result.
private val txtBodyStatsCache = TxtBodyStatsCache()

private class TxtBodyStats(
    val wordCount: Int,
    val byteCount: Int,
    val lineEnding: String
)

private class TxtBodyStatsCache {
    private var cachedText: String? = null
    private var cachedEncoding: String? = null
    private var cachedStats: TxtBodyStats? = null

    fun statsFor(text: String, encoding: String): TxtBodyStats {
        val current = cachedStats
        if (current != null && text === cachedText && encoding == cachedEncoding) {
            return current
        }
        val stats = TxtBodyStats(
            wordCount = ChapterDetector.countVisibleChars(text),
            byteCount = TextCodec.encode(text, encoding).first.size,
            lineEnding = lineEndingLabel(text)
        )
        cachedText = text
        cachedEncoding = encoding
        cachedStats = stats
        return stats
    }
}
