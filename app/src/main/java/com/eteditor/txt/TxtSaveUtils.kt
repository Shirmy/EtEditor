package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.TextCodec
import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument

private const val TXT_SAVE_NUMBER_CHARS = "0-9０-９零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟"

internal fun prepareTxtDocumentSave(
    document: TxtDocument,
    renumberTitles: Boolean,
    numberStartAtOne: Boolean = true
): TxtSavePrepareResult {
    val mapping = mapTxtChapterTitlesForSave(
        text = document.text,
        chapters = document.chapters,
        renumberTitles = renumberTitles,
        numberStartAtOne = numberStartAtOne
    )
    val saveText = mapping.text.toCrlfLineEndings()
    val encoded = TextCodec.encode(saveText, document.encoding)
    return TxtSavePrepareResult(
        mapping = mapping.copy(text = saveText),
        bytes = encoded.first,
        encodingLabel = encoded.second,
        keepMappedCatalog = mapping.changedCount > 0
    )
}

internal fun mapTxtChapterTitlesForSave(
    text: String,
    chapters: List<TxtChapter>,
    renumberTitles: Boolean,
    numberStartAtOne: Boolean = true
): TxtSaveChapterMappingResult {
    if (chapters.isEmpty()) return TxtSaveChapterMappingResult(text, 0)
    val saveTitles = if (renumberTitles) {
        txtSaveChapterTitles(chapters, numberStartAtOne)
    } else {
        chapters.map { it.title }
    }
    val replacements = chapters.mapIndexedNotNull { index, chapter ->
        val title = saveTitles.getOrNull(index)?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
        val lineStart = chapter.startIndex
        if (lineStart < 0 || lineStart >= text.length) return@mapIndexedNotNull null
        var lineEnd = lineStart
        while (lineEnd < text.length && text[lineEnd] != '\n' && text[lineEnd] != '\r') {
            lineEnd += 1
        }
        val rawTitleLine = text.substring(lineStart, lineEnd)
        if (rawTitleLine == title) {
            null
        } else {
            Triple(lineStart, lineEnd, title)
        }
    }
    if (replacements.isEmpty()) return TxtSaveChapterMappingResult(text, 0)
    var nextText = text
    replacements
        .sortedByDescending { it.first }
        .forEach { (start, end, title) ->
            nextText = nextText.replaceRange(start, end, title)
        }
    return TxtSaveChapterMappingResult(nextText, replacements.size)
}

internal fun rebuildTxtChaptersFromSavedLines(
    text: String,
    chapters: List<TxtChapter>,
    config: TxtChapterDetectionConfig
): List<TxtChapter> {
    if (chapters.isEmpty()) return emptyList()
    val lines = txtLinePositions(text)
    val rebuilt = chapters.mapIndexedNotNull { index, chapter ->
        val position = lines.getOrNull(chapter.lineIndex) ?: return@mapIndexedNotNull null
        val nextChapter = chapters.getOrNull(index + 1)
        val nextChapterPosition = nextChapter?.let { lines.getOrNull(it.lineIndex) }
        val endIndex = nextChapterPosition?.startIndex ?: text.length
        val bodyStart = position.nextIndex.coerceIn(0, text.length)
        val bodyEnd = endIndex.coerceIn(bodyStart, text.length)
        val title = txtLineText(text, position.startIndex).ifBlank { chapter.title }
        chapter.copy(
            index = index + 1,
            title = title,
            wordCount = ChapterDetector.countVisibleChars(text.substring(bodyStart, bodyEnd)),
            startIndex = position.startIndex,
            bodyStartIndex = bodyStart,
            endIndex = bodyEnd,
            endLineIndex = nextChapter?.lineIndex?.takeIf { nextChapterPosition != null } ?: lines.size,
            number = ChapterDetector.txtChapterNumberFromTitle(title)
        )
    }
    return refreshTxtChapterStatuses(rebuilt, config)
}

internal fun refreshTxtChapterStatuses(
    chapters: List<TxtChapter>,
    config: TxtChapterDetectionConfig
): List<TxtChapter> {
    if (chapters.isEmpty()) return chapters

    // Precompute, in a single linear pass, the per-chapter signals that previously
    // required re-scanning all earlier chapters (O(n^2)) and re-parsing numbers repeatedly:
    //   - each chapter's number (parsed once instead of once per comparison),
    //   - whether its title already appeared earlier (重名),
    //   - whether any earlier chapter carried a number (used by the 疑似缺章 check).
    val numbers = chapters.map { ChapterDetector.txtChapterNumberFromTitle(it.title) }
    val duplicateFlags = ArrayList<Boolean>(chapters.size)
    val hasPreviousNumberedFlags = ArrayList<Boolean>(chapters.size)
    run {
        val seenTitleKeys = HashSet<String>(chapters.size * 2)
        var seenNumbered = false
        for (index in chapters.indices) {
            val key = txtChapterStatusTitleKey(chapters[index].title)
            duplicateFlags += key in seenTitleKeys
            seenTitleKeys += key
            hasPreviousNumberedFlags += seenNumbered
            if (numbers[index] != null) seenNumbered = true
        }
    }

    // First pass computes only the threshold-independent statuses (重名/缺章/重号/回退); it feeds
    // the auto length-hint threshold derivation. 短章/超长章 stay empty here (thresholds = 0).
    val baseStatusChapters = chapters.mapIndexed { index, chapter ->
        chapter.copy(
            number = numbers[index],
            status = refreshChapterStatus(
                isDuplicateTitle = duplicateFlags[index],
                wordCount = chapter.wordCount,
                shortThreshold = 0,
                longThreshold = 0,
                number = numbers[index],
                previousNumber = numbers.getOrNull(index - 1),
                hasPreviousNumberedChapter = hasPreviousNumberedFlags[index]
            )
        )
    }
    val effectiveConfig = resolveTxtChapterLengthHintConfig(config, baseStatusChapters)
    return chapters.mapIndexed { index, chapter ->
        chapter.copy(
            number = numbers[index],
            status = refreshChapterStatus(
                isDuplicateTitle = duplicateFlags[index],
                wordCount = chapter.wordCount,
                shortThreshold = effectiveConfig.shortThreshold,
                longThreshold = effectiveConfig.longThreshold,
                number = numbers[index],
                previousNumber = numbers.getOrNull(index - 1),
                hasPreviousNumberedChapter = hasPreviousNumberedFlags[index]
            )
        )
    }
}

private fun refreshChapterStatus(
    isDuplicateTitle: Boolean,
    wordCount: Int,
    shortThreshold: Int,
    longThreshold: Int,
    number: Int?,
    previousNumber: Int?,
    hasPreviousNumberedChapter: Boolean
): List<String> {
    val statuses = mutableListOf<String>()
    if (isDuplicateTitle) statuses += "重名"
    if (wordCount in 1 until shortThreshold) statuses += "短章"
    if (longThreshold > 0 && wordCount > longThreshold) statuses += "超长章"

    if (number != null && !hasPreviousNumberedChapter && number > 1) {
        statuses += "疑似缺章"
    } else if (number != null && previousNumber != null) {
        when {
            number == previousNumber -> statuses += "重复序号"
            number < previousNumber -> statuses += "序号回退"
            number > previousNumber + 1 -> statuses += "疑似缺章"
        }
    }
    return statuses
}

internal fun txtLinePositions(text: String): List<TxtLinePosition> {
    val result = mutableListOf<TxtLinePosition>()
    var start = 0
    while (start < text.length) {
        var end = start
        while (end < text.length && text[end] != '\n' && text[end] != '\r') {
            end += 1
        }
        var next = end
        if (next < text.length) {
            next += if (text[next] == '\r' && next + 1 < text.length && text[next + 1] == '\n') {
                2
            } else {
                1
            }
        }
        result += TxtLinePosition(startIndex = start, nextIndex = next)
        start = next
    }
    if (text.isEmpty()) {
        result += TxtLinePosition(startIndex = 0, nextIndex = 0)
    }
    return result
}

internal fun txtLineText(text: String, lineStart: Int): String {
    if (lineStart < 0 || lineStart > text.length) return ""
    var lineEnd = lineStart
    while (lineEnd < text.length && text[lineEnd] != '\n' && text[lineEnd] != '\r') {
        lineEnd += 1
    }
    return text.substring(lineStart, lineEnd)
}

private fun txtSaveChapterTitles(
    chapters: List<TxtChapter>,
    numberStartAtOne: Boolean
): List<String> {
    val firstNumber = chapters
        .firstNotNullOfOrNull { chapter -> parseTxtSaveNumberedTitle(chapter.title)?.number }
    if (firstNumber == null) return chapters.map { it.title }

    var nextNumber = if (numberStartAtOne) 1 else 0
    return chapters.map { chapter ->
        val parts = parseTxtSaveNumberedTitle(chapter.title) ?: return@map chapter.title
        val nextTitle = renderTxtSaveNumberedTitle(nextNumber, parts)
        nextNumber += 1
        nextTitle
    }
}

private val txtRefreshWhitespaceRegex = Regex("""\s+""")

private fun txtChapterStatusTitleKey(title: String): String {
    return title.replace(txtRefreshWhitespaceRegex, "").lowercase()
}

private fun parseTxtSaveNumberedTitle(title: String): TxtSaveChapterTitleParts? {
    val match = Regex("""^\s*\u7b2c\s*([$TXT_SAVE_NUMBER_CHARS]+)\s*\u7ae0([\s\S]*)${'$'}""")
        .find(title)
        ?: return null
    val number = match.groupValues.getOrNull(1).orEmpty()
    val parsedNumber = ChapterDetector.parseNumber(number) ?: return null
    val suffix = match.groupValues.getOrNull(2).orEmpty()
        .replace(Regex("""^[\s:：、.．\-—_丨|]+"""), "")
        .trim()
    return TxtSaveChapterTitleParts(number = parsedNumber, unit = "章", suffix = suffix)
}

private fun renderTxtSaveNumberedTitle(number: Int, parts: TxtSaveChapterTitleParts): String {
    val prefix = "第$number${parts.unit}"
    return if (parts.suffix.isBlank()) prefix else "$prefix ${parts.suffix}"
}
