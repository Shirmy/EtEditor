package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.EpubBook
import com.eteditor.core.updateEpubChapterHtmlEntry

/** 与废文抓取「作者有话说」分隔线一致。 */
internal const val EPUB_AUTHOR_NOTE_SEPARATOR_TEXT = "-----------------------"

internal const val EPUB_AUTHOR_NOTE_SEPARATOR_PARAGRAPH =
    "<p>$EPUB_AUTHOR_NOTE_SEPARATOR_TEXT</p>"

internal const val EPUB_WARNING_IMAGE_FILE_NAME = "wenli.png"
internal const val EPUB_NOTE_IMAGE_FILE_NAME = "note.webp"

internal fun wrapEpubBodySelectionAsWarningInBook(
    book: EpubBook,
    chapterIndex: Int,
    sourceStart: Int,
    sourceEnd: Int,
    warningImageBytes: ByteArray,
    warningImageMediaType: String = "image/png"
): EpubBodyParagraphWrapResult {
    val chapter = book.chapters.getOrNull(chapterIndex)
        ?: return EpubBodyParagraphWrapResult(success = false)
    val bodyParts = htmlBodyContentParts(chapter.html)
    val result = wrapEpubBodySelectionAsWarning(bodyParts.body, sourceStart, sourceEnd)
    if (!result.success) return result
    ensureEpubInsertImageResource(
        book = book,
        fileName = EPUB_WARNING_IMAGE_FILE_NAME,
        bytes = warningImageBytes,
        mediaType = warningImageMediaType
    )
    chapter.html = rebuildHtmlWithBodyContent(bodyParts.prefix, result.nextBody, bodyParts.suffix)
        .toCrlfLineEndings()
    chapter.wordCount = ChapterDetector.countHtmlChars(chapter.html)
    updateEpubChapterHtmlEntry(book, chapter)
    normalizeEpubChapterLineEndingsToCrlf(book, chapter)
    return EpubBodyParagraphWrapResult(success = true)
}

internal fun insertEpubBodyAuthorNoteSeparatorInBook(
    book: EpubBook,
    chapterIndex: Int,
    sourceStart: Int,
    sourceEnd: Int
): EpubBodyParagraphWrapResult {
    val chapter = book.chapters.getOrNull(chapterIndex)
        ?: return EpubBodyParagraphWrapResult(success = false)
    val bodyParts = htmlBodyContentParts(chapter.html)
    val result = insertEpubBodyAuthorNoteSeparator(bodyParts.body, sourceStart, sourceEnd)
    if (!result.success) return result
    chapter.html = rebuildHtmlWithBodyContent(bodyParts.prefix, result.nextBody, bodyParts.suffix)
        .toCrlfLineEndings()
    chapter.wordCount = ChapterDetector.countHtmlChars(chapter.html)
    updateEpubChapterHtmlEntry(book, chapter)
    normalizeEpubChapterLineEndingsToCrlf(book, chapter)
    return EpubBodyParagraphWrapResult(success = true)
}

internal fun insertEpubBodyAnnotationInBook(
    book: EpubBook,
    chapterIndex: Int,
    sourceStart: Int,
    sourceEnd: Int,
    noteImageBytes: ByteArray,
    noteImageMediaType: String = "image/webp"
): EpubBodyParagraphWrapResult {
    val chapter = book.chapters.getOrNull(chapterIndex)
        ?: return EpubBodyParagraphWrapResult(success = false)
    val bodyParts = htmlBodyContentParts(chapter.html)
    val imagePath = ensureEpubInsertImageResource(
        book = book,
        fileName = EPUB_NOTE_IMAGE_FILE_NAME,
        bytes = noteImageBytes,
        mediaType = noteImageMediaType
    )
    val chapterDir = chapter.path.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    val imageHref = relativeEpubHref(chapterDir, imagePath)
    val result = insertEpubBodyAnnotation(
        body = bodyParts.body,
        sourceStart = sourceStart,
        sourceEnd = sourceEnd,
        noteImageHref = imageHref
    )
    if (!result.success) return result
    chapter.html = rebuildHtmlWithBodyContent(bodyParts.prefix, result.nextBody, bodyParts.suffix)
        .toCrlfLineEndings()
    chapter.wordCount = ChapterDetector.countHtmlChars(chapter.html)
    updateEpubChapterHtmlEntry(book, chapter)
    normalizeEpubChapterLineEndingsToCrlf(book, chapter)
    return EpubBodyParagraphWrapResult(success = true)
}

internal fun wrapEpubPackageTextBodySelectionAsWarningInBook(
    book: EpubBook,
    path: String,
    sourceStart: Int,
    sourceEnd: Int,
    warningImageBytes: ByteArray,
    warningImageMediaType: String = "image/png"
): EpubPackageTextBodyMutationResult {
    val source = epubPackageText(book, path)
        ?: return EpubPackageTextBodyMutationResult(success = false, message = "未找到包内 HTML")
    val bodyParts = htmlBodyContentParts(source)
    val result = wrapEpubBodySelectionAsWarning(bodyParts.body, sourceStart, sourceEnd)
    if (!result.success) {
        return EpubPackageTextBodyMutationResult(success = false, message = result.message)
    }
    ensureEpubInsertImageResource(
        book = book,
        fileName = EPUB_WARNING_IMAGE_FILE_NAME,
        bytes = warningImageBytes,
        mediaType = warningImageMediaType
    )
    val nextSource = rebuildHtmlWithBodyContent(bodyParts.prefix, result.nextBody, bodyParts.suffix)
        .toCrlfLineEndings()
    updateEpubPackageText(book, path, nextSource)
    return EpubPackageTextBodyMutationResult(success = true, nextBody = result.nextBody)
}

internal fun insertEpubPackageTextAuthorNoteSeparatorInBook(
    book: EpubBook,
    path: String,
    sourceStart: Int,
    sourceEnd: Int
): EpubPackageTextBodyMutationResult {
    val source = epubPackageText(book, path)
        ?: return EpubPackageTextBodyMutationResult(success = false, message = "未找到包内 HTML")
    val bodyParts = htmlBodyContentParts(source)
    val result = insertEpubBodyAuthorNoteSeparator(bodyParts.body, sourceStart, sourceEnd)
    if (!result.success) {
        return EpubPackageTextBodyMutationResult(success = false, message = result.message)
    }
    val nextSource = rebuildHtmlWithBodyContent(bodyParts.prefix, result.nextBody, bodyParts.suffix)
        .toCrlfLineEndings()
    updateEpubPackageText(book, path, nextSource)
    return EpubPackageTextBodyMutationResult(success = true, nextBody = result.nextBody)
}

internal fun insertEpubPackageTextAnnotationInBook(
    book: EpubBook,
    path: String,
    sourceStart: Int,
    sourceEnd: Int,
    noteImageBytes: ByteArray,
    noteImageMediaType: String = "image/webp"
): EpubPackageTextBodyMutationResult {
    val source = epubPackageText(book, path)
        ?: return EpubPackageTextBodyMutationResult(success = false, message = "未找到包内 HTML")
    val bodyParts = htmlBodyContentParts(source)
    val imagePath = ensureEpubInsertImageResource(
        book = book,
        fileName = EPUB_NOTE_IMAGE_FILE_NAME,
        bytes = noteImageBytes,
        mediaType = noteImageMediaType
    )
    val chapterDir = path.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    val imageHref = relativeEpubHref(chapterDir, imagePath)
    val result = insertEpubBodyAnnotation(
        body = bodyParts.body,
        sourceStart = sourceStart,
        sourceEnd = sourceEnd,
        noteImageHref = imageHref
    )
    if (!result.success) {
        return EpubPackageTextBodyMutationResult(success = false, message = result.message)
    }
    val nextSource = rebuildHtmlWithBodyContent(bodyParts.prefix, result.nextBody, bodyParts.suffix)
        .toCrlfLineEndings()
    updateEpubPackageText(book, path, nextSource)
    return EpubPackageTextBodyMutationResult(success = true, nextBody = result.nextBody)
}

internal fun wrapEpubBodySelectionAsWarning(
    body: String,
    sourceStart: Int,
    sourceEnd: Int
): EpubBodyParagraphWrapResult {
    val start = sourceStart.coerceIn(0, body.length)
    val end = sourceEnd.coerceIn(start, body.length)
    if (end <= start) {
        return EpubBodyParagraphWrapResult(success = false, message = "请先选中文字")
    }
    val lines = epubBodyLineSlices(body)
    val selectedLines = lines.filter { it.overlaps(start, end) }
    if (selectedLines.isEmpty()) {
        return EpubBodyParagraphWrapResult(success = false, message = "请先选中文字")
    }
    if (selectedLines.any { it.text.isEpubSysWarningMarkup() } ||
        isOffsetInsideSysDiv(body, selectedLines.first().sourceStart)
    ) {
        return EpubBodyParagraphWrapResult(success = false, message = "所选内容已在预警中，未处理")
    }
    val contentLines = selectedLines.mapNotNull { line ->
        val text = line.text.trim()
        when {
            text.isEmpty() -> null
            !text.containsEpubHtmlTag() -> "<p>${text.escapeEpubParagraphText()}</p>"
            else -> text
        }
    }
    if (contentLines.isEmpty()) {
        return EpubBodyParagraphWrapResult(success = false, message = "所选内容为空")
    }
    val wrapped = buildString {
        append("<div class=\"sys\">")
        contentLines.forEach { line ->
            append("\r\n")
            append(line)
        }
        append("</div>")
    }
    val spanStart = selectedLines.first().sourceStart
    val spanEnd = selectedLines.last().sourceEnd
    val nextBody = body.replaceRange(spanStart, spanEnd, wrapped)
    if (nextBody == body) {
        return EpubBodyParagraphWrapResult(success = false, message = "所选内容无需处理")
    }
    return EpubBodyParagraphWrapResult(success = true, nextBody = nextBody)
}

internal fun insertEpubBodyAuthorNoteSeparator(
    body: String,
    sourceStart: Int,
    sourceEnd: Int
): EpubBodyParagraphWrapResult {
    val start = sourceStart.coerceIn(0, body.length)
    val end = sourceEnd.coerceIn(start, body.length)
    if (end <= start) {
        return EpubBodyParagraphWrapResult(success = false, message = "请先选中文字")
    }
    val lines = epubBodyLineSlices(body)
    val selectedIndexes = lines.mapIndexedNotNull { index, line ->
        if (line.overlaps(start, end)) index else null
    }
    if (selectedIndexes.isEmpty()) {
        return EpubBodyParagraphWrapResult(success = false, message = "请先选中文字")
    }
    val firstIndex = selectedIndexes.first()
    val firstLine = lines[firstIndex]
    if (firstIndex > 0 && lines[firstIndex - 1].text.isEpubAuthorNoteSeparatorLine()) {
        return EpubBodyParagraphWrapResult(success = false, message = "所选前方已有分隔线，未处理")
    }
    val insertText = "$EPUB_AUTHOR_NOTE_SEPARATOR_PARAGRAPH\r\n"
    val nextBody = body.replaceRange(firstLine.sourceStart, firstLine.sourceStart, insertText)
    return EpubBodyParagraphWrapResult(success = true, nextBody = nextBody)
}

internal fun insertEpubBodyAnnotation(
    body: String,
    sourceStart: Int,
    sourceEnd: Int,
    noteImageHref: String
): EpubBodyParagraphWrapResult {
    val start = sourceStart.coerceIn(0, body.length)
    val end = sourceEnd.coerceIn(start, body.length)
    if (end <= start) {
        return EpubBodyParagraphWrapResult(success = false, message = "请先选中文字")
    }
    val lines = epubBodyLineSlices(body)
    val selectedLines = lines.filter { it.overlaps(start, end) }
    if (selectedLines.isEmpty()) {
        return EpubBodyParagraphWrapResult(success = false, message = "请先选中文字")
    }
    if (selectedLines.size > 1) {
        return EpubBodyParagraphWrapResult(success = false, message = "注解不支持跨段选择")
    }
    val noteId = nextEpubFootnoteId(body)
    val noteRef = epubNoteReferenceHtml(noteId, noteImageHref)
    val footnote = epubFootnoteAsideHtml(noteId)
    val withRef = body.replaceRange(end, end, noteRef)
    val hostLine = selectedLines.single()
    // 角标插在选区后，脚注插在该行内容结束后、换行前。
    val insertAt = if (end <= hostLine.sourceEnd) {
        hostLine.sourceEnd + noteRef.length
    } else {
        hostLine.sourceEnd
    }
    val nextBody = withRef.replaceRange(insertAt, insertAt, footnote)
    return EpubBodyParagraphWrapResult(success = true, nextBody = nextBody)
}

internal fun ensureEpubInsertImageResource(
    book: EpubBook,
    fileName: String,
    bytes: ByteArray,
    mediaType: String
): String {
    findEpubImagePathByFileName(book, fileName)?.let { return it }
    if (bytes.isEmpty()) {
        error("图片内容为空：$fileName")
    }
    return writeImageResourceToEpub(book, fileName, bytes, mediaType)
}

internal fun findEpubImagePathByFileName(book: EpubBook, fileName: String): String? {
    val clean = fileName.substringAfterLast('/').ifBlank { return null }
    book.entries.keys.firstOrNull { path ->
        path.substringAfterLast('/').equals(clean, ignoreCase = true)
    }?.let { return it }
    return book.manifest.values.firstOrNull { item ->
        item.path.substringAfterLast('/').equals(clean, ignoreCase = true)
    }?.path
}

internal fun nextEpubFootnoteId(body: String): String {
    var max = 0
    FOOTNOTE_ID_REGEX.findAll(body).forEach { match ->
        val value = match.groupValues[1].ifBlank { match.groupValues[2] }.toIntOrNull()
            ?: return@forEach
        if (value > max) max = value
    }
    val next = max + 1
    return if (next < 100) next.toString().padStart(2, '0') else next.toString()
}

internal fun epubNoteReferenceHtml(noteId: String, noteImageHref: String): String {
    val href = "#$noteId"
    val src = noteImageHref.escapeXmlAttribute("\"")
    return """<a epub:type="noteref" href="$href"><sup><img style="width: 0.85em;" alt="note" src="$src"/></sup></a>"""
}

internal fun epubFootnoteAsideHtml(noteId: String): String {
    // 脚注块前换行，块内保留空行，闭合后不额外吞掉原行尾换行。
    return "\r\n<aside epub:type=\"footnote\" id=\"$noteId\">\r\n\r\n</aside>"
}

private fun String.isEpubSysWarningMarkup(): Boolean {
    return SYS_CLASS_REGEX.containsMatchIn(this)
}

private fun isOffsetInsideSysDiv(body: String, offset: Int): Boolean {
    var index = 0
    var depth = 0
    while (index < body.length && index < offset) {
        val nextOpen = body.indexOf("<div", index, ignoreCase = true)
        val nextClose = body.indexOf("</div", index, ignoreCase = true)
        if (nextOpen < 0 && nextClose < 0) break
        val useOpen = nextOpen >= 0 && (nextClose < 0 || nextOpen < nextClose)
        if (useOpen) {
            if (nextOpen >= offset) break
            val tagEnd = body.indexOf('>', nextOpen)
            if (tagEnd < 0) break
            val tag = body.substring(nextOpen, tagEnd + 1)
            if (depth > 0 || SYS_CLASS_REGEX.containsMatchIn(tag)) {
                if (!tag.endsWith("/>")) depth += 1
            }
            index = tagEnd + 1
        } else {
            if (nextClose >= offset) break
            val tagEnd = body.indexOf('>', nextClose)
            if (tagEnd < 0) break
            if (depth > 0) depth -= 1
            index = tagEnd + 1
        }
    }
    return depth > 0
}

private fun String.isEpubAuthorNoteSeparatorLine(): Boolean {
    val trimmed = trim()
    if (trimmed == EPUB_AUTHOR_NOTE_SEPARATOR_TEXT) return true
    if (trimmed == EPUB_AUTHOR_NOTE_SEPARATOR_PARAGRAPH) return true
    val plain = ChapterDetector.stripHtml(trimmed).trim()
    return plain == EPUB_AUTHOR_NOTE_SEPARATOR_TEXT
}

private fun String.containsEpubHtmlTag(): Boolean {
    return EPUB_HTML_TAG_REGEX.containsMatchIn(this)
}

private fun String.escapeEpubParagraphText(): String {
    return replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

// 与 EpubVolumeUtils 行切片一致，供标签操作复用。
internal fun epubBodyLineSlices(body: String): List<EpubBodyLineSlice> {
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

internal data class EpubBodyLineSlice(
    val text: String,
    val sourceStart: Int,
    val sourceEnd: Int
) {
    fun overlaps(start: Int, end: Int): Boolean {
        return sourceStart < end && sourceEnd > start
    }
}

private val EPUB_HTML_TAG_REGEX = Regex("""<\s*(?:!|/?[A-Za-z][A-Za-z0-9:-]*(?:\s|/?>))""")
private val SYS_CLASS_REGEX = Regex("""class\s*=\s*["']sys["']""", RegexOption.IGNORE_CASE)
private val FOOTNOTE_ID_REGEX = Regex(
    """<aside\b[^>]*epub:type\s*=\s*["']footnote["'][^>]*\bid\s*=\s*["'](\d+)["'][^>]*>|<aside\b[^>]*\bid\s*=\s*["'](\d+)["'][^>]*epub:type\s*=\s*["']footnote["'][^>]*>""",
    RegexOption.IGNORE_CASE
)
