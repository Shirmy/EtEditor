package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import com.eteditor.core.updateEpubChapterHtmlEntry

internal fun defaultEpubVolumeTitle(kind: String): String {
    return when (kind) {
        VOLUME_KIND_EXTRA -> "番外卷"
        VOLUME_KIND_SPECIAL_EXTRA -> ""
        else -> ""
    }
}

internal fun epubVolumeFileNamePreview(book: EpubBook?, kind: String): String {
    val currentBook = book ?: return ""
    if (kind == VOLUME_KIND_EXTRA) return "Vol00.xhtml"
    return "${nextVolumeStem(currentBook, kind)}.xhtml"
}

internal fun hasExtraEpubVolume(book: EpubBook?): Boolean {
    val currentBook = book ?: return false
    return usedFileStems(currentBook).any { it.equals("Vol00", ignoreCase = true) }
}

internal fun addEpubVolumeToBook(
    book: EpubBook,
    kind: String,
    volumeTitle: String,
    insertIndex: Int
): EpubVolumeAddResult {
    if (kind == VOLUME_KIND_EXTRA && hasExtraEpubVolume(book)) {
        return EpubVolumeAddResult(success = false, message = "番外卷已存在")
    }
    var errorMessage = ""
    val (position, chapter) = insertEpubVolumeChapter(
        book,
        kind,
        volumeTitle,
        insertIndex,
        onError = { message -> errorMessage = message }
    ) ?: return EpubVolumeAddResult(success = false, message = errorMessage)
    resequenceEpubVolumeFileNames(book, kind)
    applyVolumeTocLevels(book)
    return EpubVolumeAddResult(
        success = true,
        nextPreviewIndex = position,
        fileName = chapter.path.substringAfterLast('/')
    )
}

internal fun nextVolumeStem(book: EpubBook, kind: String): String {
    return when (kind) {
        VOLUME_KIND_EXTRA -> "Vol00"
        VOLUME_KIND_SPECIAL_EXTRA -> {
            "VolF${nextVolumeNumber(book, kind).toString().padStart(2, '0')}"
        }
        else -> "Vol${nextVolumeNumber(book, VOLUME_KIND_NORMAL).toString().padStart(2, '0')}"
    }.let { stem ->
        if (stem.lowercase() !in usedFileStems(book).map { it.lowercase() }.toSet()) return stem
        uniqueVolumeStem(stem, book)
    }
}

internal fun cleanVolumeTitleInput(title: String, fallback: String = ""): String {
    val cleaned = ChapterDetector.cleanTitle(title)
    return if (ChapterDetector.cleanTitleLineBreaksAsSpace(cleaned).isBlank()) fallback else cleaned
}

internal fun cleanVolumeTitleWithBreaks(title: String): String {
    return title
        .lineSequence()
        .map { line -> ChapterDetector.cleanTitle(ChapterDetector.stripHtml(line)) }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

internal fun cleanEpubBodyLineTitle(line: String): String {
    return ChapterDetector.cleanTitle(ChapterDetector.stripHtml(line))
}

internal fun cleanEpubBodyLinePlainText(line: String): String {
    return ChapterDetector.stripHtml(line)
}

internal fun epubVolumeDefaultTitleFromBodyLines(
    lines: List<String>,
    lineIndex: Int,
    lineCountText: String
): String {
    val lineCount = lineCountText.toIntOrNull() ?: return ""
    if (lineIndex !in lines.indices || lineCount <= 0) return ""
    return lines
        .drop(lineIndex)
        .take(lineCount)
        .map(::cleanEpubBodyLinePlainText)
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

internal fun epubBodyWithoutVolumeLines(
    lines: List<String>,
    lineIndex: Int,
    lineCount: Int
): String {
    val removeRange = lineIndex until (lineIndex + lineCount)
    return lines
        .filterIndexed { index, _ -> index !in removeRange }
        .joinToString("\r\n")
        .trim()
}

internal fun setEpubVolumeAtBodyLineInBook(
    book: EpubBook,
    chapterIndex: Int,
    lineIndex: Int,
    lineCountText: String,
    volumeTitleText: String
): EpubBodyLineVolumeResult {
    val chapter = book.chapters.getOrNull(chapterIndex) ?: return EpubBodyLineVolumeResult(success = false)
    val lines = epubChapterBodyLines(chapter)
    if (lineIndex !in lines.indices) return EpubBodyLineVolumeResult(success = false)
    val remainingLines = lines.size - lineIndex
    val lineCount = lineCountText.toIntOrNull()
    if (lineCount == null || lineCount !in 1..remainingLines) {
        return EpubBodyLineVolumeResult(
            success = false,
            message = "带走行数必须在 1-$remainingLines 行之间"
        )
    }

    val nextBody = epubBodyWithoutVolumeLines(lines, lineIndex, lineCount)
    if (nextBody.isBlank()) {
        return EpubBodyLineVolumeResult(success = false, message = "设为卷后当前章节正文为空")
    }
    val defaultTitle = epubVolumeDefaultTitleFromBodyLines(lines, lineIndex, lineCountText)
    val volumeTitle = cleanVolumeTitleInput(volumeTitleText.ifBlank { defaultTitle })
    var errorMessage = ""
    val (insertedIndex, insertedChapter) = insertEpubVolumeChapter(
        book = book,
        kind = VOLUME_KIND_NORMAL,
        volumeTitle = volumeTitle,
        insertIndex = chapterIndex + 1,
        onError = { message -> errorMessage = message }
    ) ?: return EpubBodyLineVolumeResult(success = false, message = errorMessage)

    val bodyParts = htmlBodyContentParts(chapter.html)
    chapter.html = rebuildHtmlWithBodyContent(bodyParts.prefix, nextBody, bodyParts.suffix).toCrlfLineEndings()
    chapter.wordCount = ChapterDetector.countHtmlChars(chapter.html)
    updateEpubChapterHtmlEntry(book, chapter)
    normalizeEpubChapterLineEndingsToCrlf(book, insertedChapter)
    resequenceEpubVolumeFileNames(book, VOLUME_KIND_NORMAL)
    applyVolumeTocLevels(book)
    return EpubBodyLineVolumeResult(
        success = true,
        nextPreviewIndex = insertedIndex,
        volumeDisplayTitle = insertedChapter.title
    )
}

internal fun setEpubVolumeFromBodySelectionInBook(
    book: EpubBook,
    chapterIndex: Int,
    sourceStart: Int,
    sourceEnd: Int
): EpubBodyLineVolumeResult {
    val chapter = book.chapters.getOrNull(chapterIndex) ?: return EpubBodyLineVolumeResult(success = false)
    val bodyParts = htmlBodyContentParts(chapter.html)
    val body = bodyParts.body
    val start = sourceStart.coerceIn(0, body.length)
    val end = sourceEnd.coerceIn(start, body.length)
    if (end <= start) {
        return EpubBodyLineVolumeResult(success = false, message = "请先选择要设为卷的文字")
    }
    val wholeLineSelection = epubBodyWholeLineSelection(body, start, end)
    val selectedLines = epubSelectedBodyPlainLines(wholeLineSelection.selectedLines.joinToString("\n"))
    val volumeTitle = selectedLines.firstOrNull().orEmpty()
    if (volumeTitle.isBlank()) {
        return EpubBodyLineVolumeResult(success = false, message = "所选文字没有可设为卷名的内容")
    }
    val nextBody = wholeLineSelection.nextBody
    if (ChapterDetector.stripHtml(nextBody).isBlank()) {
        return EpubBodyLineVolumeResult(success = false, message = "设为卷后当前章节正文为空")
    }

    var errorMessage = ""
    val (insertedIndex, insertedChapter) = insertEpubVolumeChapter(
        book = book,
        kind = VOLUME_KIND_NORMAL,
        volumeTitle = volumeTitle,
        insertIndex = chapterIndex + 1,
        onError = { message -> errorMessage = message }
    ) ?: return EpubBodyLineVolumeResult(success = false, message = errorMessage)

    chapter.html = rebuildHtmlWithBodyContent(bodyParts.prefix, nextBody, bodyParts.suffix).toCrlfLineEndings()
    chapter.wordCount = ChapterDetector.countHtmlChars(chapter.html)
    updateEpubChapterHtmlEntry(book, chapter)

    insertedChapter.html = volumeHtml(volumeTitle, selectedLines.drop(1)).toCrlfLineEndings()
    insertedChapter.wordCount = ChapterDetector.countHtmlChars(insertedChapter.html)
    updateEpubChapterHtmlEntry(book, insertedChapter)
    normalizeEpubChapterLineEndingsToCrlf(book, chapter)
    normalizeEpubChapterLineEndingsToCrlf(book, insertedChapter)
    resequenceEpubVolumeFileNames(book, VOLUME_KIND_NORMAL)
    applyVolumeTocLevels(book)
    return EpubBodyLineVolumeResult(
        success = true,
        nextPreviewIndex = insertedIndex,
        volumeDisplayTitle = insertedChapter.title
    )
}

internal fun collapseEpubBodyBlankLineAtSeam(body: String, seam: Int): String {
    if (seam <= 0 || seam >= body.length) return body
    if (body[seam - 1] != '\n') return body
    val after = body[seam]
    if (after != '\r' && after != '\n') return body
    var removeEnd = seam
    if (body[removeEnd] == '\r') removeEnd += 1
    if (removeEnd < body.length && body[removeEnd] == '\n') removeEnd += 1
    return body.removeRange(seam, removeEnd)
}

internal fun deleteEpubBodySelectionFromBook(
    book: EpubBook,
    chapterIndex: Int,
    sourceStart: Int,
    sourceEnd: Int
): EpubBodyLineDeleteResult {
    val chapter = book.chapters.getOrNull(chapterIndex) ?: return EpubBodyLineDeleteResult(success = false)
    val bodyParts = htmlBodyContentParts(chapter.html)
    val body = bodyParts.body
    val start = sourceStart.coerceIn(0, body.length)
    val end = sourceEnd.coerceIn(start, body.length)
    if (end <= start) {
        return EpubBodyLineDeleteResult(success = false, message = "请先选择要删除的文字")
    }
    val nextBody = collapseEpubBodyBlankLineAtSeam(body.replaceRange(start, end, ""), start)
    if (ChapterDetector.stripHtml(nextBody).isBlank()) {
        return EpubBodyLineDeleteResult(success = false, message = "删除后当前章节正文为空")
    }
    chapter.html = rebuildHtmlWithBodyContent(bodyParts.prefix, nextBody, bodyParts.suffix).toCrlfLineEndings()
    chapter.wordCount = ChapterDetector.countHtmlChars(chapter.html)
    updateEpubChapterHtmlEntry(book, chapter)
    normalizeEpubChapterLineEndingsToCrlf(book, chapter)
    return EpubBodyLineDeleteResult(success = true)
}

internal fun wrapEpubBodySelectionParagraphsInBook(
    book: EpubBook,
    chapterIndex: Int,
    sourceStart: Int,
    sourceEnd: Int
): EpubBodyParagraphWrapResult {
    val chapter = book.chapters.getOrNull(chapterIndex)
        ?: return EpubBodyParagraphWrapResult(success = false)
    val bodyParts = htmlBodyContentParts(chapter.html)
    val result = wrapEpubBodySelectionParagraphs(bodyParts.body, sourceStart, sourceEnd)
    if (!result.success) return result
    chapter.html = rebuildHtmlWithBodyContent(bodyParts.prefix, result.nextBody, bodyParts.suffix).toCrlfLineEndings()
    chapter.wordCount = ChapterDetector.countHtmlChars(chapter.html)
    updateEpubChapterHtmlEntry(book, chapter)
    normalizeEpubChapterLineEndingsToCrlf(book, chapter)
    return EpubBodyParagraphWrapResult(success = true)
}

internal fun wrapEpubBodySelectionParagraphs(
    body: String,
    sourceStart: Int,
    sourceEnd: Int
): EpubBodyParagraphWrapResult {
    val start = sourceStart.coerceIn(0, body.length)
    val end = sourceEnd.coerceIn(start, body.length)
    if (end <= start) {
        return EpubBodyParagraphWrapResult(success = false, message = "请先选择要加标签的文字")
    }
    val selectedLines = epubBodyLineSlices(body).filter { it.overlaps(start, end) }
    if (selectedLines.isEmpty()) {
        return EpubBodyParagraphWrapResult(success = false, message = "请先选择要加标签的文字")
    }
    if (selectedLines.any { it.text.containsEpubHtmlTag() }) {
        return EpubBodyParagraphWrapResult(success = false, message = "所选内容已包含标签，未加标签")
    }
    val paragraphs = selectedLines
        .map { it.text.trim() }
        .filter { it.isNotEmpty() }
        .map { "<p>${it.escapeEpubParagraphText()}</p>" }
    if (paragraphs.isEmpty()) {
        return EpubBodyParagraphWrapResult(success = false, message = "所选内容为空")
    }
    val spanStart = selectedLines.first().sourceStart
    val spanEnd = selectedLines.last().sourceEnd
    val nextBody = body.replaceRange(spanStart, spanEnd, paragraphs.joinToString("\r\n"))
    if (nextBody == body) {
        return EpubBodyParagraphWrapResult(success = false, message = "所选内容无需加标签")
    }
    return EpubBodyParagraphWrapResult(success = true, nextBody = nextBody)
}

private fun String.containsEpubHtmlTag(): Boolean {
    return EPUB_HTML_TAG_REGEX.containsMatchIn(this)
}

private fun String.escapeEpubParagraphText(): String {
    return replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

private val EPUB_HTML_TAG_REGEX = Regex("""<\s*(?:!|/?[A-Za-z][A-Za-z0-9:-]*(?:\s|/?>))""")

internal fun epubSelectedBodyPlainLines(selection: String): List<String> {
    return selection
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lineSequence()
        .map(::cleanEpubSelectionPlainTextLine)
        .filter { it.isNotBlank() }
        .toList()
}

internal data class EpubBodyWholeLineSelection(
    val selectedLines: List<String>,
    val nextBody: String
)

private data class EpubBodyLineSlice(
    val text: String,
    val sourceStart: Int,
    val sourceEnd: Int
) {
    fun overlaps(start: Int, end: Int): Boolean {
        return sourceStart < end && sourceEnd > start
    }
}

internal fun epubBodyWholeLineSelection(
    body: String,
    start: Int,
    end: Int
): EpubBodyWholeLineSelection {
    val lines = epubBodyLineSlices(body)
    val selectedIndexes = lines
        .mapIndexedNotNull { index, line -> if (line.overlaps(start, end)) index else null }
        .toSet()
    val selectedLines = lines
        .filterIndexed { index, _ -> index in selectedIndexes }
        .map { it.text }
    val nextBody = lines
        .filterIndexed { index, _ -> index !in selectedIndexes }
        .joinToString("\r\n") { it.text }
        .trim()
    return EpubBodyWholeLineSelection(selectedLines, nextBody)
}

private fun epubBodyLineSlices(body: String): List<EpubBodyLineSlice> {
    if (body.isEmpty()) return emptyList()
    val lines = mutableListOf<EpubBodyLineSlice>()
    var lineStart = 0
    while (lineStart < body.length) {
        val lineFeed = body.indexOf('\n', lineStart)
        val rawLineEnd = if (lineFeed < 0) body.length else lineFeed
        val lineEnd = if (rawLineEnd > lineStart && body[rawLineEnd - 1] == '\r') {
            rawLineEnd - 1
        } else {
            rawLineEnd
        }
        lines += EpubBodyLineSlice(
            text = body.substring(lineStart, lineEnd),
            sourceStart = lineStart,
            sourceEnd = lineEnd
        )
        if (lineFeed < 0) break
        lineStart = lineFeed + 1
    }
    return lines
}

private fun cleanEpubSelectionPlainTextLine(line: String): String {
    val stripped = ChapterDetector.stripHtml(line.cleanEpubSelectionHtmlFragment())
    return ChapterDetector.stripHtml(stripped.cleanEpubSelectionHtmlFragment()).trim()
}

private fun String.cleanEpubSelectionHtmlFragment(): String {
    return stripLeadingDanglingHtmlTagTail()
        .stripTrailingDanglingHtmlTagHead()
}

private fun String.stripLeadingDanglingHtmlTagTail(): String {
    val start = indexOfFirst { !it.isWhitespace() }
    if (start < 0) return this
    val firstClose = indexOf('>', start)
    if (firstClose < 0) return this
    val firstOpen = indexOf('<', start).let { if (it < 0) length else it }
    if (firstOpen < firstClose) return this
    val candidate = substring(start, firstClose)
    if (!candidate.looksLikeDanglingHtmlTag()) return this
    return removeRange(start, firstClose + 1)
}

private fun String.stripTrailingDanglingHtmlTagHead(): String {
    val lastOpen = lastIndexOf('<')
    if (lastOpen < 0) return this
    val lastClose = lastIndexOf('>')
    if (lastClose > lastOpen) return this
    val candidate = substring(lastOpen + 1)
    if (!candidate.looksLikeDanglingHtmlTagHead()) return this
    return substring(0, lastOpen)
}

private fun String.looksLikeDanglingHtmlTagHead(): Boolean {
    val value = trim()
    return value.isEmpty() ||
        value == "/" ||
        value.looksLikeDanglingHtmlTag()
}

private fun String.looksLikeDanglingHtmlTag(): Boolean {
    val value = trim().removeSuffix("/")
    if (value.isBlank()) return false
    val tagName = value.removePrefix("/")
        .trimStart()
        .takeWhile { it.isLetterOrDigit() || it == ':' || it == '_' || it == '-' }
        .lowercase()
    if (tagName.isBlank()) return false
    return tagName in EPUB_SELECTION_HTML_TAG_NAMES
}

private val EPUB_SELECTION_HTML_TAG_NAMES = setOf(
    "a",
    "article",
    "b",
    "blockquote",
    "body",
    "br",
    "center",
    "dd",
    "div",
    "dl",
    "dt",
    "em",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "hr",
    "i",
    "img",
    "li",
    "ol",
    "p",
    "rb",
    "rp",
    "rt",
    "ruby",
    "s",
    "section",
    "span",
    "strong",
    "sub",
    "sup",
    "table",
    "tbody",
    "td",
    "th",
    "thead",
    "tr",
    "u",
    "ul"
)

internal fun applyVolumeTocLevels(book: EpubBook) {
    var insideVolumeChapterRun = false
    book.chapters.forEach { chapter ->
        if (chapter.isVolumeChapter()) {
            chapter.tocLevel = 0
            insideVolumeChapterRun = true
        } else if (insideVolumeChapterRun && chapter.isAutoVolumeChildChapter()) {
            chapter.tocLevel = 1
        } else {
            chapter.tocLevel = 0
            insideVolumeChapterRun = false
        }
    }
}

private fun EpubChapter.isAutoVolumeChildChapter(): Boolean {
    val fileName = path.substringAfterLast('/').substringBefore('#')
    val stem = fileName.substringBeforeLast('.', fileName)
    return Regex("""^(?:Chapter\d+|chapter_\d+)${'$'}""").matches(stem)
}

internal fun resequenceEpubVolumeFileNames(book: EpubBook, kind: String): Int {
    return when (kind) {
        VOLUME_KIND_NORMAL -> resequenceEpubVolumeFileNamesForKind(book, specialExtra = false)
        VOLUME_KIND_SPECIAL_EXTRA -> resequenceEpubVolumeFileNamesForKind(book, specialExtra = true)
        else -> 0
    }
}

private data class EpubVolumeRenamePlan(
    val chapter: EpubChapter,
    val targetPath: String,
    val temporaryPath: String
)

private fun resequenceEpubVolumeFileNamesForKind(book: EpubBook, specialExtra: Boolean): Int {
    val volumes = book.chapters
        .filter { chapter -> epubVolumeStemKind(chapter.path) == specialExtra }
    if (volumes.isEmpty()) return 0

    val targetChapters = volumes.toSet()
    val nonTargetPaths = epubUsedPaths(book)
        .filterNot { path -> targetChapters.any { chapter -> normalizeEpubPath(chapter.path) == path } }
        .toSet()
    val temporaryUsedPaths = epubUsedPaths(book).toMutableSet()
    val plans = volumes.mapIndexedNotNull { index, chapter ->
        val targetStem = if (specialExtra) {
            "VolF${(index + 1).toString().padStart(2, '0')}"
        } else {
            "Vol${(index + 1).toString().padStart(2, '0')}"
        }
        val targetPath = epubVolumePathWithStem(chapter.path, targetStem)
        if (normalizeEpubPath(chapter.path) == targetPath) return@mapIndexedNotNull null
        val temporaryPath = uniqueTemporaryEpubVolumePath(chapter.path, index, temporaryUsedPaths)
        temporaryUsedPaths += temporaryPath
        EpubVolumeRenamePlan(
            chapter = chapter,
            targetPath = targetPath,
            temporaryPath = temporaryPath
        )
    }
    if (plans.isEmpty()) return 0
    if (plans.any { plan -> plan.targetPath in nonTargetPaths }) return 0

    plans.forEach { plan ->
        moveEpubVolumeChapterPath(book, plan.chapter, plan.temporaryPath)
    }
    plans.forEach { plan ->
        moveEpubVolumeChapterPath(book, plan.chapter, plan.targetPath)
    }
    return plans.size
}

private fun epubVolumeStemKind(path: String): Boolean? {
    val stem = path.substringAfterLast('/').substringBeforeLast('.')
    Regex("""(?i)^VolF(\d+)${'$'}""").matchEntire(stem)?.let { return true }
    val normal = Regex("""(?i)^Vol(\d+)${'$'}""").matchEntire(stem) ?: return null
    val number = normal.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
    return if (number > 0) false else null
}

private fun epubVolumePathWithStem(path: String, stem: String): String {
    val directory = path.substringBeforeLast('/', missingDelimiterValue = "")
        .let { if (it.isBlank()) "" else "$it/" }
    val extension = path.substringAfterLast('.', missingDelimiterValue = "xhtml").ifBlank { "xhtml" }
    return normalizeEpubPath("$directory$stem.$extension")
}

private fun uniqueTemporaryEpubVolumePath(path: String, index: Int, used: Set<String>): String {
    val directory = path.substringBeforeLast('/', missingDelimiterValue = "")
        .let { if (it.isBlank()) "" else "$it/" }
    val extension = path.substringAfterLast('.', missingDelimiterValue = "xhtml").ifBlank { "xhtml" }
    var suffix = index + 1
    while (true) {
        val candidate = normalizeEpubPath("${directory}Vol_tmp_${suffix.toString().padStart(2, '0')}.$extension")
        if (candidate !in used) return candidate
        suffix += 1
    }
}

private fun epubUsedPaths(book: EpubBook): Set<String> {
    return (book.entries.keys + book.manifest.values.map { it.path } + book.chapters.map { it.path })
        .map(::normalizeEpubPath)
        .toSet()
}

private fun moveEpubVolumeChapterPath(book: EpubBook, chapter: EpubChapter, nextPath: String) {
    val oldPath = normalizeEpubPath(chapter.path)
    val newPath = normalizeEpubPath(nextPath)
    if (oldPath == newPath) return

    val oldBytes = book.entries.remove(oldPath)
    if (oldBytes != null) {
        book.entries[newPath] = oldBytes
    }
    val opfDir = book.opfPath.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    val nextHref = relativeEpubHref(opfDir, newPath)
    chapter.pathAliases += oldPath
    chapter.pathAliases += newPath
    chapter.path = newPath
    chapter.href = nextHref
    book.manifest[chapter.id]?.path = newPath
    book.manifest[chapter.id]?.href = nextHref
}

internal fun insertEpubVolumeChapter(
    book: EpubBook,
    kind: String,
    volumeTitle: String,
    insertIndex: Int,
    onError: (String) -> Unit = {}
): Pair<Int, EpubChapter>? {
    val stem = nextVolumeStem(book, kind)
    val displayTitle = when (kind) {
        VOLUME_KIND_EXTRA -> cleanVolumeTitleInput(volumeTitle, defaultEpubVolumeTitle(kind))
        else -> cleanVolumeTitleInput(volumeTitle)
    }
    if (displayTitle.isBlank()) {
        onError("无法添加，请输入卷名")
        return null
    }
    val fileName = "$stem.xhtml"
    val chapterDir = book.chapters.firstOrNull()?.path
        ?.substringBeforeLast('/', missingDelimiterValue = "")
        ?.let { if (it.isBlank()) "" else "$it/" }
        ?: "Text/"
    val path = normalizeEpubPath(chapterDir + fileName)
    if (book.entries.containsKey(path)) {
        onError("卷文件已存在：$fileName")
        return null
    }

    val opfDir = book.opfPath.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    val href = relativeEpubHref(opfDir, path)
    val id = uniqueManifestId(book, stem)
    val html = volumeHtml(displayTitle)
    val chapter = EpubChapter(
        id = id,
        href = href,
        path = path,
        originalPath = path,
        pathAliases = mutableSetOf(path),
        title = displayTitle,
        tocLevel = 0,
        html = html,
        wordCount = ChapterDetector.countHtmlChars(html)
    )
    val position = insertIndex.coerceIn(0, book.chapters.size)
    updateEpubChapterHtmlEntry(book, chapter)
    book.manifest[id] = ManifestItem(
        id = id,
        href = href,
        mediaType = "application/xhtml+xml",
        path = path
    )
    book.spineIds.add(position, id)
    book.chapters.add(position, chapter)
    return position to chapter
}

private fun nextVolumeNumber(book: EpubBook, kind: String): Int {
    val regex = when (kind) {
        VOLUME_KIND_SPECIAL_EXTRA -> Regex("""(?i)^VolF(\d+)${'$'}""")
        else -> Regex("""(?i)^Vol(\d+)${'$'}""")
    }
    val usedNumbers = usedFileStems(book).mapNotNull { stem ->
        regex.matchEntire(stem)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }.filter { number ->
        kind != VOLUME_KIND_NORMAL || number > 0
    }
    return ((usedNumbers.maxOrNull() ?: 0) + 1).coerceAtLeast(1)
}

private fun uniqueVolumeStem(stem: String, book: EpubBook): String {
    val used = usedFileStems(book).map { it.lowercase() }.toSet()
    if (stem.lowercase() !in used) return stem
    var index = 1
    while (true) {
        val candidate = "${stem}_$index"
        if (candidate.lowercase() !in used) return candidate
        index += 1
    }
}

private fun usedFileStems(book: EpubBook): Set<String> {
    return (book.entries.keys + book.manifest.values.map { it.path } + book.chapters.map { it.path })
        .map { path -> path.substringAfterLast('/').substringBeforeLast('.') }
        .filter { it.isNotBlank() }
        .toSet()
}
