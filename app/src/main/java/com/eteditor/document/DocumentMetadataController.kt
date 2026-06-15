package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.DocumentKind
import com.eteditor.core.EpubBook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun EditorController.bodyLineEndingHint(): String {
    return bodyLineEndingHintForDocument(kind, epub, txt, previewChapterIndex)
}

internal fun buildEpubSummaryLoadingMeta(fileSizeBytes: Long? = null): String {
    return buildEpubSummaryMeta(
        lineEnding = EPUB_SUMMARY_LOADING_LABEL,
        wordCountLabel = EPUB_SUMMARY_LOADING_LABEL,
        fileSizeBytes = fileSizeBytes
    )
}

internal fun buildEpubSummaryInitialMeta(fileSizeBytes: Long? = null): String {
    return listOfNotNull(
        "UTF-8",
        fileSizeBytes?.let(::compactByteSize)
    ).joinToString("|")
}

internal fun buildEpubSummaryMeta(
    lineEnding: String,
    wordCountLabel: String,
    fileSizeBytes: Long? = null
): String {
    return listOfNotNull(
        "UTF-8",
        lineEnding,
        wordCountLabel,
        fileSizeBytes?.let(::compactByteSize)
    ).joinToString("|")
}

internal fun EditorController.refreshEpubSummaryMeta() {
    val book = epub
    if (book == null) {
        epubSummaryMeta = ""
        cancelEpubWordCountCalculation()
        return
    }
    startEpubWordCountCalculation(
        book = book,
        sessionKey = documentSessionKey,
        fileSizeBytes = epubFileSizeBytes
    )
}

internal fun EditorController.cancelEpubWordCountCalculation() {
    epubWordCountJob?.cancel()
    epubWordCountJob = null
    epubWordCountProgress = null
    epubWordCountProgressText = ""
}

internal fun EditorController.startEpubWordCountCalculation(
    book: EpubBook,
    sessionKey: Int,
    fileSizeBytes: Long? = epubFileSizeBytes
) {
    epubWordCountJob?.cancel()
    if (!isCurrentEpubWordCountTarget(book, sessionKey)) {
        epubWordCountJob = null
        epubWordCountProgress = null
        epubWordCountProgressText = ""
        return
    }
    epubSummaryMeta = buildEpubSummaryLoadingMeta(fileSizeBytes)
    val total = book.chapters.size
    if (total > 0) {
        epubWordCountProgress = 0f
        epubWordCountProgressText = "字数 0/$total"
    } else {
        epubWordCountProgress = null
        epubWordCountProgressText = ""
    }
    val job = controllerScope.launch(start = CoroutineStart.LAZY) {
        val lineEndings = EpubSummaryLineEndings()
        try {
            var wordCount = 0
            for ((index, chapter) in book.chapters.withIndex()) {
                val scan = withContext(Dispatchers.Default) {
                    epubChapterSummaryScan(chapter.html)
                }
                if (!isCurrentEpubWordCountTarget(book, sessionKey)) return@launch
                lineEndings.add(scan)
                chapter.wordCount = scan.wordCount
                wordCount += scan.wordCount
                val done = index + 1
                if (total > 0) {
                    epubWordCountProgress = done.toFloat() / total.toFloat()
                    epubWordCountProgressText = "字数 $done/$total"
                }
            }
            if (!isCurrentEpubWordCountTarget(book, sessionKey)) return@launch
            epubSummaryMeta = buildEpubSummaryMeta(
                lineEnding = lineEndings.label(),
                wordCountLabel = compactCountLabel(wordCount),
                fileSizeBytes = fileSizeBytes
            )
            refreshChapters(refreshPreview = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (isCurrentEpubWordCountTarget(book, sessionKey)) {
                statusMessage = "EPUB 字数计算失败：${error.message ?: error.javaClass.simpleName}"
                log(statusMessage)
            }
        } finally {
            if (epubWordCountJob == coroutineContext[Job]) {
                epubWordCountJob = null
                epubWordCountProgress = null
                epubWordCountProgressText = ""
            }
        }
    }
    epubWordCountJob = job
    job.start()
}

private fun epubParagraphWordCount(html: String): Int {
    return epubParagraphRegex.findAll(html).sumOf { match ->
        ChapterDetector.countVisibleChars(ChapterDetector.stripHtml(match.groupValues[1]))
    }
}

private fun epubChapterSummaryScan(html: String): EpubChapterSummaryScan {
    val body = ChapterDetector.extractBodyMarkup(html)
    val lineEndings = scanLineEndings(body)
    return EpubChapterSummaryScan(
        wordCount = epubParagraphWordCount(html),
        hasCrlf = lineEndings.hasCrlf,
        hasCr = lineEndings.hasCr,
        hasLf = lineEndings.hasLf
    )
}

private fun scanLineEndings(text: String): EpubSummaryLineEndings {
    val lineEndings = EpubSummaryLineEndings()
    var index = 0
    while (index < text.length) {
        when (text[index]) {
            '\r' -> {
                if (index + 1 < text.length && text[index + 1] == '\n') {
                    lineEndings.hasCrlf = true
                    index += 2
                } else {
                    lineEndings.hasCr = true
                    index += 1
                }
            }
            '\n' -> {
                lineEndings.hasLf = true
                index += 1
            }
            else -> index += 1
        }
    }
    return lineEndings
}

private val epubParagraphRegex = Regex(
    """<p\b[^>]*>(.*?)</p>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

private fun EditorController.isCurrentEpubWordCountTarget(book: EpubBook, sessionKey: Int): Boolean {
    return documentSessionKey == sessionKey && kind == DocumentKind.Epub && epub === book
}

private data class EpubChapterSummaryScan(
    val wordCount: Int,
    val hasCrlf: Boolean,
    val hasCr: Boolean,
    val hasLf: Boolean
)

private class EpubSummaryLineEndings {
    var hasCrlf: Boolean = false
    var hasCr: Boolean = false
    var hasLf: Boolean = false

    fun add(scan: EpubChapterSummaryScan) {
        hasCrlf = hasCrlf || scan.hasCrlf
        hasCr = hasCr || scan.hasCr
        hasLf = hasLf || scan.hasLf
    }

    fun label(): String {
        val count = listOf(hasCrlf, hasCr, hasLf).count { it }
        return when {
            count == 0 -> "无"
            count > 1 -> "混合"
            hasCrlf -> "CRLF"
            hasCr -> "CR"
            else -> "LF"
        }
    }
}

private const val EPUB_SUMMARY_LOADING_LABEL = "..."
