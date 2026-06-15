package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument

private const val TXT_CHAPTER_NUMBER_CHARS = "0-9０-９零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟"

internal data class TxtChapterTitleUpdateResult(
    val success: Boolean,
    val text: String = "",
    val message: String = ""
)

internal fun txtPrefaceEndIndex(document: TxtDocument): Int? {
    val firstChapter = txtFirstChapterByTextOrder(document) ?: return null
    val firstStart = firstChapter.startIndex.coerceIn(0, document.text.length)
    return firstStart.takeIf { document.text.substring(0, it).isNotBlank() }
}

internal fun txtFirstChapterByTextOrder(document: TxtDocument): TxtChapter? {
    return document.chapters.minByOrNull { chapter -> chapter.startIndex }
}

internal fun updateTxtChapterTitleText(
    document: TxtDocument,
    chapterIndex: Int,
    chapterTitle: String
): TxtChapterTitleUpdateResult {
    val chapter = document.chapters.getOrNull(chapterIndex)
        ?: return TxtChapterTitleUpdateResult(success = false)
    val nextTitle = chapterTitle.trim()
    if (nextTitle.isBlank()) {
        return TxtChapterTitleUpdateResult(success = false, message = "章节标题不能为空")
    }
    return TxtChapterTitleUpdateResult(
        success = true,
        text = ChapterDetector.updateTxtTitle(document.text, chapter.lineIndex, nextTitle)
    )
}

internal fun suggestTxtSupplementChapterNumberForLine(
    lineIndex: Int,
    line: String,
    chapters: List<TxtChapter>
): String {
    manualChapterNumberInLine(line)?.let { return it }

    chapters.firstOrNull { it.lineIndex == lineIndex }?.let { chapter ->
        return (chapter.number ?: chapter.index).toString()
    }

    val previousChapters = chapters.filter { it.lineIndex < lineIndex }
    val nextChapters = chapters.filter { it.lineIndex > lineIndex }
    val previousNumber = previousChapters.asReversed().firstNotNullOfOrNull { it.number }
    val nextNumber = nextChapters.firstNotNullOfOrNull { it.number }
    val suggestedNumber = when {
        previousNumber != null && nextNumber != null && nextNumber > previousNumber + 1 -> previousNumber + 1
        previousNumber != null -> previousNumber + 1
        nextNumber != null -> (nextNumber - 1).coerceAtLeast(1)
        else -> previousChapters.size + 1
    }
    return suggestedNumber.toString()
}

internal fun normalizeManualChapterNumber(raw: String): String {
    val value = raw
        .trim()
        .removePrefix("第")
        .removeSuffix("章")
        .replace(Regex("""\s+"""), "")
    return value.takeIf {
        it.isNotBlank() && Regex("""^[$TXT_CHAPTER_NUMBER_CHARS]+${'$'}""").matches(it)
    }.orEmpty()
}

internal fun hasManualChapterPrefix(line: String): Boolean {
    return Regex("""^\s*第\s*[$TXT_CHAPTER_NUMBER_CHARS]+\s*(?:章|节|節|回|集|卷|部|篇|话|話)""")
        .containsMatchIn(line)
}

internal fun manualChapterNumberInLine(line: String): String? {
    return Regex("""^\s*第\s*([$TXT_CHAPTER_NUMBER_CHARS]+)\s*(?:章|节|節|回|集|卷|部|篇|话|話)""")
        .find(line)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
}
