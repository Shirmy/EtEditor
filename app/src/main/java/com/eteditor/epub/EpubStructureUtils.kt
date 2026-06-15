package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.EPUB_COVER_DIRECTORY_TITLE
import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import com.eteditor.core.isEpubCoverDirectoryCandidate
import com.eteditor.core.updateEpubChapterHtmlEntry

internal data class EpubChapterItemUpdateResult(
    val success: Boolean,
    val message: String = ""
)

internal fun EpubChapter.isCoverSection0001Or0002(): Boolean {
    val fileName = path.substringAfterLast('/').ifBlank { path }
    val stem = fileName.substringBeforeLast('.', fileName).lowercase()
    return stem == "cover" || stem == "section0001" || stem == "section0002"
}

internal fun EpubChapter.isVolumeChapter(): Boolean {
    return (pathAliases + path + originalPath).any { itemPath ->
        val stem = itemPath.substringAfterLast('/').substringBeforeLast('.')
        Regex("""(?i)^Vol(?:\d+|F\d+)${'$'}""").matches(stem)
    }
}

internal fun resequenceEpubBodyChaptersAfterStructureChange(
    book: EpubBook,
    preferredTitleSource: String,
    forceNumberedIndex: Int? = null
): EpubStructureResequenceResult {
    val targetIndices = epubBodyChapterIndices(book)
    val titleChanges = resequenceEpubNumberedTitles(
        book = book,
        targetIndices = targetIndices,
        preferredTitleSource = preferredTitleSource,
        forceNumberedIndex = forceNumberedIndex
    )
    val fileChanges = resequenceEpubBodyChapterFileNames(book, targetIndices)
    return EpubStructureResequenceResult(
        renamedFiles = fileChanges,
        renamedTitles = titleChanges
    )
}

internal fun epubBodyChapterIndices(book: EpubBook): List<Int> {
    return book.chapters.indices.filter { index ->
        book.chapters[index].isEpubBodyNumberedChapter()
    }
}

internal fun resequenceEpubBodyChapterFileNames(
    book: EpubBook,
    targetIndices: List<Int>,
    preferredTemplate: EpubChapterFileNameTemplate? = null
): Int {
    if (targetIndices.isEmpty()) return 0
    val template = preferredTemplate ?: dominantEpubChapterFileNameTemplate(book, targetIndices) ?: return 0
    val opfDir = book.opfPath.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    val targetOldPaths = targetIndices
        .mapNotNull { index -> book.chapters.getOrNull(index)?.path }
        .map { it.lowercase() }
        .toSet()
    val usedByNonTargets = (book.entries.keys + book.manifest.values.map { it.path })
        .map { it.lowercase() }
        .filterNot { it in targetOldPaths }
        .toSet()
    var startNumber = template.startNumber
    var plannedPaths: List<String> = emptyList()
    var guard = 0
    while (true) {
        plannedPaths = targetIndices.mapIndexed { offset, _ ->
            renderEpubChapterPath(template.copy(startNumber = startNumber), offset)
        }
        val lowerPlanned = plannedPaths.map { it.lowercase() }
        val hasConflict = lowerPlanned.toSet().size != lowerPlanned.size ||
            lowerPlanned.any { it in usedByNonTargets }
        if (!hasConflict) break
        if (guard > 10000) return 0
        startNumber += 1
        guard += 1
    }

    val changed = targetIndices
        .zip(plannedPaths)
        .count { (index, newPath) -> book.chapters[index].path != newPath }
    if (changed == 0) return 0

    val removedEntries = targetIndices.associateWith { index ->
        book.entries.remove(book.chapters[index].path)
    }
    targetIndices.zip(plannedPaths).forEach { (index, newPath) ->
        val chapter = book.chapters[index]
        val oldPath = chapter.path
        val oldBytes = removedEntries[index]
        if (oldBytes != null) {
            book.entries[newPath] = oldBytes
        }
        chapter.pathAliases += oldPath
        chapter.pathAliases += newPath
        chapter.path = newPath
        chapter.href = relativeEpubHref(opfDir, newPath)
        book.manifest[chapter.id]?.path = newPath
        book.manifest[chapter.id]?.href = chapter.href
    }
    return changed
}

internal fun dominantEpubChapterFileNameTemplate(
    book: EpubBook,
    targetIndices: List<Int>
): EpubChapterFileNameTemplate? {
    val matches = targetIndices.mapNotNull { index ->
        val chapter = book.chapters.getOrNull(index) ?: return@mapNotNull null
        parseEpubChapterFileName(chapter.path)
    }
    if (matches.isEmpty()) return fallbackEpubChapterFileNameTemplate(book, targetIndices)
    val dominant = matches
        .groupBy { match ->
            listOf(
                match.directory,
                match.prefix,
                match.suffix,
                match.extension,
                match.numberWidth.toString()
            ).joinToString("\u0001")
        }
        .maxByOrNull { it.value.size }
        ?.value
        .orEmpty()
    val sample = dominant.firstOrNull() ?: return null
    val startNumber = if (dominant.any { it.number == 0 }) {
        0
    } else {
        1
    }
    return EpubChapterFileNameTemplate(
        directory = sample.directory,
        prefix = sample.prefix,
        suffix = sample.suffix,
        extension = sample.extension,
        numberWidth = sample.numberWidth,
        startNumber = startNumber
    )
}

internal fun buildEpubStructureChangeMessage(
    prefix: String,
    resequence: EpubStructureResequenceResult
): String {
    val parts = buildList {
        if (resequence.renamedFiles > 0) add("文件名连号 ${resequence.renamedFiles}")
        if (resequence.renamedTitles > 0) add("标题顺序 ${resequence.renamedTitles}")
    }
    return if (parts.isEmpty()) prefix else "$prefix；${parts.joinToString("，")}"
}

internal fun deleteEpubChapterFromBook(
    book: EpubBook,
    chapterIndex: Int
): EpubChapterDeleteResult {
    if (chapterIndex !in book.chapters.indices) return EpubChapterDeleteResult(success = false)
    if (book.chapters.size <= 1) {
        return EpubChapterDeleteResult(success = false, message = "至少需要保留 1 个 HTML 章节")
    }
    val chapter = book.chapters.removeAt(chapterIndex)
    val displayTitle = chapter.title.ifBlank { chapter.path.substringAfterLast('/') }
    book.spineIds.removeAll { it == chapter.id }
    book.manifest.remove(chapter.id)
    book.entries.remove(chapter.path)
    chapter.pathAliases.forEach { alias ->
        if (alias != chapter.path && book.chapters.none { it.path == alias }) {
            book.entries.remove(alias)
        }
    }
    if (book.chapters.any { it.isVolumeChapter() }) applyVolumeTocLevels(book)
    val resequence = resequenceEpubBodyChaptersAfterStructureChange(
        book = book,
        preferredTitleSource = chapter.title
    )
    return EpubChapterDeleteResult(
        success = true,
        deletedDisplayTitle = displayTitle,
        nextPreviewIndex = chapterIndex.coerceAtMost(book.chapters.lastIndex).coerceAtLeast(0),
        resequence = resequence
    )
}

internal fun deleteEpubBodyLineFromBook(
    book: EpubBook,
    chapterIndex: Int,
    lineIndex: Int
): EpubBodyLineDeleteResult {
    val chapter = book.chapters.getOrNull(chapterIndex) ?: return EpubBodyLineDeleteResult(success = false)
    val lines = epubChapterBodyLines(chapter)
    if (lineIndex !in lines.indices) return EpubBodyLineDeleteResult(success = false)
    val nextBody = epubBodyWithoutLine(lines, lineIndex)
    if (nextBody.isBlank()) {
        return EpubBodyLineDeleteResult(success = false, message = "删除后当前章节正文为空")
    }
    val bodyParts = htmlBodyContentParts(chapter.html)
    chapter.html = rebuildHtmlWithBodyContent(bodyParts.prefix, nextBody, bodyParts.suffix).toCrlfLineEndings()
    chapter.wordCount = ChapterDetector.countHtmlChars(chapter.html)
    updateEpubChapterHtmlEntry(book, chapter)
    return EpubBodyLineDeleteResult(success = true)
}

internal fun moveEpubChapterAfterInBook(
    book: EpubBook,
    sourceIndex: Int,
    targetIndex: Int,
    bookStartTarget: Int,
    bookEndTarget: Int
): EpubChapterMoveResult {
    if (sourceIndex !in book.chapters.indices) return EpubChapterMoveResult(success = false)
    if (targetIndex !in setOf(bookStartTarget, bookEndTarget) && sourceIndex == targetIndex) {
        return EpubChapterMoveResult(success = false)
    }
    val originalLastIndex = book.chapters.lastIndex
    val clampedTarget = targetIndex.coerceIn(0, originalLastIndex)
    val source = book.chapters.removeAt(sourceIndex)
    val sourceSpineId = if (sourceIndex in book.spineIds.indices) {
        book.spineIds.removeAt(sourceIndex)
    } else {
        source.id
    }
    val insertIndex = when (targetIndex) {
        bookStartTarget -> 0
        bookEndTarget -> book.chapters.size
        else -> {
            val adjustedTarget = if (clampedTarget > sourceIndex) clampedTarget - 1 else clampedTarget
            (adjustedTarget + 1).coerceIn(0, book.chapters.size)
        }
    }
    book.chapters.add(insertIndex, source)
    if (sourceIndex in book.spineIds.indices || book.spineIds.size == book.chapters.size - 1) {
        book.spineIds.add(insertIndex.coerceIn(0, book.spineIds.size), sourceSpineId)
    } else {
        book.spineIds.clear()
        book.spineIds.addAll(book.chapters.map { it.id })
    }
    applyVolumeTocLevels(book)
    return EpubChapterMoveResult(
        success = true,
        movedDisplayTitle = source.title.ifBlank { source.path.substringAfterLast('/') },
        nextPreviewIndex = insertIndex
    )
}

internal fun suggestEpubSplitChapterTitleFromBodyLines(
    lines: List<String>,
    lineNumberText: String
): String {
    val lineNumber = lineNumberText.toIntOrNull() ?: return ""
    val lineIndex = lineNumber - 1
    return lines
        .drop(lineIndex.coerceAtLeast(0))
        .firstNotNullOfOrNull { line ->
            ChapterDetector.cleanTitle(ChapterDetector.stripHtml(line))
                .takeIf { it.isNotBlank() }
        }
        .orEmpty()
}

internal fun splitEpubChapterAtLineInBook(
    book: EpubBook,
    chapterIndex: Int,
    lineNumberText: String,
    newTitleText: String,
    dropSplitLineFromBody: Boolean = false
): EpubChapterSplitResult {
    val chapter = book.chapters.getOrNull(chapterIndex) ?: return EpubChapterSplitResult(success = false)
    val lines = epubChapterBodyLines(chapter)
    if (lines.size < 2) {
        return EpubChapterSplitResult(success = false, message = "当前章节正文行数不足，无法分章")
    }
    val lineNumber = lineNumberText.toIntOrNull()
    if (lineNumber == null || lineNumber !in 2..lines.size) {
        return EpubChapterSplitResult(success = false, message = "分章位置必须在 2-${lines.size} 行之间")
    }
    val splitIndex = lineNumber - 1
    val firstBody = lines.take(splitIndex).joinToString("\r\n").trimEnd()
    val typedTitle = ChapterDetector.cleanTitle(newTitleText)
    val titleLineCandidate = epubSplitTitleLineCandidate(lines, splitIndex)
    val titleLineIndex = when {
        dropSplitLineFromBody -> splitIndex
        typedTitle.isBlank() -> titleLineCandidate?.first
        titleLineCandidate != null &&
            titleLineCandidate.first == splitIndex &&
            titleLineCandidate.second == typedTitle -> splitIndex
        else -> null
    }
    val secondBody = lines
        .drop(splitIndex)
        .filterIndexed { offset, _ -> splitIndex + offset != titleLineIndex }
        .joinToString("\r\n")
        .trim()
    if (firstBody.isBlank() || secondBody.isBlank()) {
        return EpubChapterSplitResult(success = false, message = "分章后不能产生空章节")
    }

    val sourceDisplayTitle = chapter.title.ifBlank { chapter.path.substringAfterLast('/') }
    val bodyParts = htmlBodyContentParts(chapter.html)
    val prefix = bodyParts.prefix
    val suffix = bodyParts.suffix
    val inheritedTitleFormat = inheritedTitleHeadingFormat(chapter.html)
    val cleanTitle = ChapterDetector.cleanTitle(newTitleText)
        .ifBlank { suggestEpubSplitChapterTitleFromBodyLines(lines, lineNumberText) }
        .ifBlank { "${chapter.title.ifBlank { "新章节" }} 下" }
    val newPath = splitEpubChapterPath(book, chapter.path)
    val opfDir = book.opfPath.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    val href = relativeEpubHref(opfDir, newPath)
    val id = uniqueManifestId(book, newPath.substringAfterLast('/').substringBeforeLast('.'))

    chapter.html = rebuildHtmlWithBodyContent(prefix, firstBody, suffix).toCrlfLineEndings()
    chapter.wordCount = ChapterDetector.countHtmlChars(chapter.html)
    updateEpubChapterHtmlEntry(book, chapter)

    val newHtml = updateHtmlTitleWithInheritedFormat(
        rebuildHtmlWithBodyContent(prefix, secondBody, suffix),
        cleanTitle,
        inheritedTitleFormat
    )
        .toCrlfLineEndings()
    val newChapter = EpubChapter(
        id = id,
        href = href,
        path = newPath,
        originalPath = newPath,
        pathAliases = mutableSetOf(newPath),
        title = cleanTitle,
        tocLevel = chapter.tocLevel,
        html = newHtml,
        wordCount = ChapterDetector.countHtmlChars(newHtml)
    )
    val insertIndex = (chapterIndex + 1).coerceIn(0, book.chapters.size)
    updateEpubChapterHtmlEntry(book, newChapter)
    book.manifest[id] = ManifestItem(
        id = id,
        href = href,
        mediaType = book.manifest[chapter.id]?.mediaType?.takeIf { it.isNotBlank() } ?: "application/xhtml+xml",
        path = newPath
    )
    book.chapters.add(insertIndex, newChapter)
    book.spineIds.add(insertIndex.coerceIn(0, book.spineIds.size), id)
    if (book.chapters.any { it.isVolumeChapter() }) applyVolumeTocLevels(book)
    val resequence = resequenceEpubBodyChaptersAfterStructureChange(
        book = book,
        preferredTitleSource = chapter.title,
        forceNumberedIndex = insertIndex
    )
    normalizeEpubChapterLineEndingsToCrlf(book, chapter)
    normalizeEpubChapterLineEndingsToCrlf(book, newChapter)
    return EpubChapterSplitResult(
        success = true,
        sourceDisplayTitle = sourceDisplayTitle,
        newTitle = cleanTitle,
        nextPreviewIndex = insertIndex,
        resequence = resequence
    )
}

internal fun epubBodyWithoutLine(lines: List<String>, lineIndex: Int): String {
    return lines
        .filterIndexed { index, _ -> index != lineIndex }
        .joinToString("\r\n")
        .trim()
}

internal fun rebuildHtmlWithBodyContent(
    prefix: String,
    bodyContent: String,
    suffix: String
): String {
    return buildString {
        append(prefix.trimEnd('\r', '\n'))
        append("\r\n")
        append(bodyContent.trim('\r', '\n'))
        append("\r\n")
        append(suffix.trimStart('\r', '\n'))
    }
}

internal fun updateEpubChapterItemModel(
    book: EpubBook,
    chapterIndex: Int,
    fileName: String,
    chapterTitle: String
): EpubChapterItemUpdateResult {
    val chapter = book.chapters.getOrNull(chapterIndex) ?: return EpubChapterItemUpdateResult(success = false)
    val manifestItem = book.manifest[chapter.id]
    val cleanedTitle = ChapterDetector.cleanTitle(chapterTitle)
    var errorMessage = ""
    val cleanFileName = normalizeChapterFileName(fileName, chapter.path) { message ->
        errorMessage = message
    } ?: return EpubChapterItemUpdateResult(success = false, message = errorMessage)
    val newPath = chapter.path.replaceFileName(cleanFileName)
    val newHref = chapter.href.replaceHrefFileName(cleanFileName)
    val isCover = isEpubCoverDirectoryCandidate(newPath, chapter.html)
    if (!isCover && ChapterDetector.cleanTitleLineBreaksAsSpace(cleanedTitle).isBlank()) {
        return EpubChapterItemUpdateResult(success = false, message = "章节标题不能为空")
    }

    if (newPath != chapter.path) {
        if (book.entries.containsKey(newPath)) {
            return EpubChapterItemUpdateResult(success = false, message = "文件名已存在：$cleanFileName")
        }
        val oldPath = chapter.path
        val oldBytes = book.entries.remove(oldPath)
        if (oldBytes != null) {
            book.entries[newPath] = oldBytes
        }
        chapter.pathAliases += oldPath
        chapter.pathAliases += newPath
        chapter.path = newPath
        chapter.href = newHref
        manifestItem?.path = newPath
        manifestItem?.href = newHref
    }

    val nextTitle = if (isCover) EPUB_COVER_DIRECTORY_TITLE else cleanedTitle
    if (nextTitle.isNotBlank() && nextTitle != chapter.title) {
        chapter.title = nextTitle
        if (!isCover) {
            chapter.html = if (chapter.isVolumeChapter()) {
                ChapterDetector.updateHtmlTitleWithLineBreaks(chapter.html, nextTitle)
            } else {
                ChapterDetector.updateHtmlTitle(chapter.html, nextTitle)
            }
        }
    }
    chapter.wordCount = ChapterDetector.countHtmlChars(chapter.html)
    return EpubChapterItemUpdateResult(success = true)
}

internal fun splitEpubChapterPath(book: EpubBook, sourcePath: String): String {
    val directory = sourcePath.substringBeforeLast('/', missingDelimiterValue = "")
        .let { if (it.isBlank()) "" else "$it/" }
    val fileName = sourcePath.substringAfterLast('/')
    val stem = fileName.substringBeforeLast('.', missingDelimiterValue = fileName).ifBlank { "Chapter" }
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "xhtml").ifBlank { "xhtml" }
    return uniqueEpubEntryPath(book, "$directory${stem}_split.$extension")
}

internal fun EpubChapter.isEpubBodyNumberedChapter(): Boolean {
    return isHtmlPath(path) &&
        !isVolumeChapter() &&
        !isCoverSection0001Or0002()
}

private fun fallbackEpubChapterFileNameTemplate(
    book: EpubBook,
    targetIndices: List<Int>
): EpubChapterFileNameTemplate? {
    val chapter = targetIndices.firstNotNullOfOrNull { index -> book.chapters.getOrNull(index) }
        ?: return null
    val directory = chapter.path.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    val extension = chapter.path.substringAfterLast('.', missingDelimiterValue = "xhtml").ifBlank { "xhtml" }
    return EpubChapterFileNameTemplate(
        directory = directory,
        prefix = "Chapter",
        suffix = "",
        extension = extension,
        numberWidth = 4,
        startNumber = 1
    )
}

private fun parseEpubChapterFileName(path: String): EpubChapterFileNameMatch? {
    val directory = path.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    val fileName = path.substringAfterLast('/')
    val stem = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "xhtml").ifBlank { "xhtml" }
    val match = Regex("""^(.*?)(\d+)(\D*)${'$'}""").find(stem) ?: return null
    val numberText = match.groupValues[2]
    return EpubChapterFileNameMatch(
        directory = directory,
        prefix = match.groupValues[1],
        suffix = match.groupValues[3],
        extension = extension,
        numberWidth = numberText.length,
        number = numberText.toIntOrNull() ?: return null
    )
}

private fun renderEpubChapterPath(template: EpubChapterFileNameTemplate, offset: Int): String {
    val number = (template.startNumber + offset).toString().padStart(template.numberWidth, '0')
    return normalizeEpubPath(
        "${template.directory}${template.prefix}$number${template.suffix}.${template.extension}"
    )
}

private fun resequenceEpubNumberedTitles(
    book: EpubBook,
    targetIndices: List<Int>,
    preferredTitleSource: String,
    forceNumberedIndex: Int?
): Int {
    if (targetIndices.isEmpty()) return 0
    val preferredPattern = parseEpubNumberedTitle(preferredTitleSource)
    val numbered = targetIndices.mapNotNull { index ->
        val chapter = book.chapters.getOrNull(index) ?: return@mapNotNull null
        parseEpubNumberedTitle(chapter.title)?.let { parts ->
            index to parts
        } ?: if (index == forceNumberedIndex && preferredPattern != null) {
            val cleanTitle = ChapterDetector.cleanTitle(chapter.title)
            val tail = if (cleanTitle.isBlank()) "" else " $cleanTitle"
            index to preferredPattern.copy(tail = tail)
        } else {
            null
        }
    }
    if (numbered.isEmpty()) return 0
    val pattern = preferredPattern ?: numbered.first().second
    var nextNumber = if (pattern.number == 0 || numbered.any { it.second.number == 0 }) 0 else 1
    var changed = 0
    numbered.forEach { (index, parts) ->
        val chapter = book.chapters.getOrNull(index) ?: return@forEach
        val nextTitle = renderEpubNumberedTitle(pattern, nextNumber, parts.tail)
        nextNumber += 1
        if (chapter.title != nextTitle) {
            chapter.title = nextTitle
            chapter.html = ChapterDetector.updateHtmlTitle(chapter.html, nextTitle)
            chapter.wordCount = ChapterDetector.countHtmlChars(chapter.html)
            updateEpubChapterHtmlEntry(book, chapter)
            changed += 1
        }
    }
    return changed
}

private fun parseEpubNumberedTitle(title: String): EpubNumberedTitleParts? {
    val cleanTitle = ChapterDetector.cleanTitle(title)
    val match = Regex(
        """^(第\s*)([0-9０-９零〇○一二两兩三四五六七八九十百千万萬壹贰叁肆伍陆柒捌玖拾佰仟]+)(\s*)(章|节|節|回|集|话|話)([\s\S]*)${'$'}"""
    ).find(cleanTitle) ?: return null
    val numberText = match.groupValues[2]
    val number = ChapterDetector.parseNumber(numberText) ?: return null
    return EpubNumberedTitleParts(
        beforeNumber = match.groupValues[1],
        numberText = numberText,
        afterNumber = match.groupValues[3],
        unit = match.groupValues[4],
        tail = match.groupValues[5],
        number = number
    )
}

private fun renderEpubNumberedTitle(
    pattern: EpubNumberedTitleParts,
    number: Int,
    tail: String
): String {
    return ChapterDetector.cleanTitle(
        pattern.beforeNumber +
            renderEpubTitleNumber(number, pattern.numberText) +
            pattern.afterNumber +
            pattern.unit +
            tail
    )
}

private fun renderEpubTitleNumber(number: Int, sample: String): String {
    val normalized = sample.map { char ->
        if (char in '０'..'９') ('0'.code + (char.code - '０'.code)).toChar() else char
    }.joinToString("")
    val asciiDigits = normalized.all { it in '0'..'9' }
    if (asciiDigits) {
        val padded = number.toString().padStart(normalized.length, '0')
        return if (sample.all { it in '０'..'９' }) {
            padded.map { char -> ('０'.code + (char.code - '0'.code)).toChar() }.joinToString("")
        } else {
            padded
        }
    }
    return number.toChineseChapterNumber()
}

private fun Int.toChineseChapterNumber(): String {
    if (this == 0) return "零"
    if (this < 0) return this.toString()
    val digits = charArrayOf('零', '一', '二', '三', '四', '五', '六', '七', '八', '九')
    val units = charArrayOf('\u0000', '十', '百', '千')
    fun sectionToChinese(value: Int): String {
        var remaining = value
        val parts = mutableListOf<Char>()
        for (place in 3 downTo 0) {
            val divisor = when (place) {
                3 -> 1000
                2 -> 100
                1 -> 10
                else -> 1
            }
            val digit = remaining / divisor
            remaining %= divisor
            if (digit > 0) {
                parts += digits[digit]
                if (units[place] != '\u0000') parts += units[place]
            } else if (parts.isNotEmpty() && remaining > 0 && parts.last() != '零') {
                parts += '零'
            }
        }
        return parts.joinToString("").trimEnd('零')
            .removePrefix("一十")
            .ifBlank { "零" }
    }
    if (this < 10000) return sectionToChinese(this)
    val high = this / 10000
    val low = this % 10000
    return if (low == 0) {
        "${sectionToChinese(high)}万"
    } else {
        "${sectionToChinese(high)}万${if (low < 1000) "零" else ""}${sectionToChinese(low)}"
    }
}
