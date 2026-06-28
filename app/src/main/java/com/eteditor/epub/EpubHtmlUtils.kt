package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.updateEpubChapterHtmlEntry

internal fun String.escapeXmlText(): String {
    return replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

internal fun String.escapeXmlAttribute(quote: String): String {
    val escaped = escapeXmlText()
    return if (quote == "\"") {
        escaped.replace("\"", "&quot;")
    } else {
        escaped.replace("'", "&apos;")
    }
}

internal fun htmlBodyContentRange(html: String): Pair<Int, Int> {
    val open = bodyOpenRegex.find(html)
    if (open != null) {
        val close = bodyCloseRegex.find(html, open.range.last + 1)
        val fallbackClose = htmlCloseRegex.find(html, open.range.last + 1)
        return (open.range.last + 1) to (close?.range?.first ?: fallbackClose?.range?.first ?: html.length)
    }
    val headClose = headCloseRegex.find(html)
    if (headClose != null) {
        val fallbackClose = htmlCloseRegex.find(html, headClose.range.last + 1)
        return (headClose.range.last + 1) to (fallbackClose?.range?.first ?: html.length)
    }
    val htmlOpen = htmlOpenRegex.find(html)
    if (htmlOpen != null) {
        val fallbackClose = htmlCloseRegex.find(html, htmlOpen.range.last + 1)
        return (htmlOpen.range.last + 1) to (fallbackClose?.range?.first ?: html.length)
    }
    return 0 to html.length
}

internal fun htmlBodyContentRangeOrNull(html: String): Pair<Int, Int>? {
    val open = bodyOpenRegex.find(html)
    if (open != null) {
        val close = bodyCloseRegex.find(html, open.range.last + 1)
        val fallbackClose = htmlCloseRegex.find(html, open.range.last + 1)
        return (open.range.last + 1) to (close?.range?.first ?: fallbackClose?.range?.first ?: html.length)
    }
    val headClose = headCloseRegex.find(html)
    if (headClose != null) {
        val fallbackClose = htmlCloseRegex.find(html, headClose.range.last + 1)
        return (headClose.range.last + 1) to (fallbackClose?.range?.first ?: html.length)
    }
    val htmlOpen = htmlOpenRegex.find(html) ?: return null
    val fallbackClose = htmlCloseRegex.find(html, htmlOpen.range.last + 1)
    return (htmlOpen.range.last + 1) to (fallbackClose?.range?.first ?: html.length)
}

internal data class HtmlBodyContentParts(
    val prefix: String,
    val body: String,
    val suffix: String,
    val visibleBodySourceStart: Int,
    val visibleBodySourceEnd: Int
)

internal fun htmlBodyContentParts(html: String): HtmlBodyContentParts {
    val bodyRange = htmlBodyContentRange(html)
    val rawBody = html.substring(bodyRange.first, bodyRange.second)
    val visibleRange = rawBody.visibleBodyContentRange()
    return HtmlBodyContentParts(
        prefix = html.substring(0, bodyRange.first),
        body = rawBody.substring(visibleRange.first, visibleRange.second),
        suffix = html.substring(bodyRange.second),
        visibleBodySourceStart = bodyRange.first + visibleRange.first,
        visibleBodySourceEnd = bodyRange.first + visibleRange.second
    )
}

internal fun htmlVisibleBodyContent(html: String): String {
    return htmlBodyContentParts(html).body
}

internal fun htmlVisibleBodyRelativeRange(
    html: String,
    sourceStart: Int,
    sourceEnd: Int
): Pair<Int, Int>? {
    return htmlVisibleBodyRelativeRange(htmlBodyContentParts(html), sourceStart, sourceEnd)
}

internal fun htmlVisibleBodyRelativeRange(
    parts: HtmlBodyContentParts,
    sourceStart: Int,
    sourceEnd: Int
): Pair<Int, Int>? {
    if (sourceEnd <= parts.visibleBodySourceStart || sourceStart >= parts.visibleBodySourceEnd) {
        return null
    }
    val start = (sourceStart - parts.visibleBodySourceStart).coerceIn(0, parts.body.length)
    val end = (sourceEnd - parts.visibleBodySourceStart).coerceIn(start, parts.body.length)
    return start to end
}

private fun String.visibleBodyContentRange(): Pair<Int, Int> {
    val nestedRange = nestedHtmlBodyContentRange()
    val start = nestedRange.first
        .coerceIn(0, length)
        .let { index -> nextNonLineBreakIndex(index, nestedRange.second) }
    val end = nestedRange.second
        .coerceIn(start, length)
        .let { index -> previousNonLineBreakEnd(index, start) }
    return start to end
}

private fun String.nestedHtmlBodyContentRange(): Pair<Int, Int> {
    val open = bodyOpenRegex.find(this)
    if (open != null) {
        val close = bodyCloseRegex.find(this, open.range.last + 1)
        val fallbackClose = htmlCloseRegex.find(this, open.range.last + 1)
        return (open.range.last + 1) to (close?.range?.first ?: fallbackClose?.range?.first ?: length)
    }
    val headClose = headCloseRegex.find(this)
    if (headClose != null) {
        val fallbackClose = htmlCloseRegex.find(this, headClose.range.last + 1)
        return (headClose.range.last + 1) to (fallbackClose?.range?.first ?: length)
    }
    val htmlOpen = htmlOpenRegex.find(this)
    if (htmlOpen != null) {
        val fallbackClose = htmlCloseRegex.find(this, htmlOpen.range.last + 1)
        return (htmlOpen.range.last + 1) to (fallbackClose?.range?.first ?: length)
    }
    return 0 to length
}

private fun String.nextNonLineBreakIndex(start: Int, limit: Int): Int {
    var index = start
    val safeLimit = limit.coerceIn(start, length)
    while (index < safeLimit && (this[index] == '\r' || this[index] == '\n')) {
        index += 1
    }
    return index
}

private fun String.previousNonLineBreakEnd(end: Int, floor: Int): Int {
    var index = end.coerceIn(floor, length)
    while (index > floor && (this[index - 1] == '\r' || this[index - 1] == '\n')) {
        index -= 1
    }
    return index
}

internal fun htmlDirectoryTitle(html: String): String {
    listOf("h1", "h2", "title").forEach { tag ->
        val title = Regex(
            """<$tag\b[^>]*>(.*?)</$tag>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(ChapterDetector::stripHtml)
            ?.let(ChapterDetector::cleanTitle)
            .orEmpty()
        if (title.isNotBlank()) return title
    }
    return ""
}

internal fun replaceIntroHtmlPreservingStructure(current: String, introBody: String): String {
    val bodyRange = htmlBodyContentRange(current)
    val body = current.substring(bodyRange.first, bodyRange.second)
    val nextBody = replaceIntroBodyPreservingStructure(body, introBody)
        ?: "\n${introBody.trim('\r', '\n')}\n"
    return current.replaceRange(bodyRange.first, bodyRange.second, nextBody)
}

// 路径整理与相对链接的实现统一放在 com.eteditor.core，这里只转发，避免同一套逻辑维护两份。
internal fun normalizeEpubPath(path: String): String = com.eteditor.core.normalizePath(path)

internal fun relativeEpubHref(fromDir: String, targetPath: String): String =
    com.eteditor.core.relativeHref(fromDir, targetPath)

internal fun uniqueManifestId(book: EpubBook, stem: String): String {
    val cleanBase = stem
        .replace(Regex("""[^A-Za-z0-9_.-]"""), "_")
        .let { value -> if (value.firstOrNull()?.isLetter() == true) value else "item_$value" }
    var id = cleanBase
    var counter = 1
    while (book.manifest.containsKey(id)) {
        id = "${cleanBase}_$counter"
        counter += 1
    }
    return id
}

internal fun uniqueEpubEntryPath(book: EpubBook, preferredPath: String): String {
    val normalized = normalizeEpubPath(preferredPath)
    val used = (book.entries.keys + book.manifest.values.map { it.path }).map { it.lowercase() }.toSet()
    if (normalized.lowercase() !in used) return normalized

    val directory = normalized.substringBeforeLast('/', missingDelimiterValue = "")
    val fileName = normalized.substringAfterLast('/')
    val stem = fileName.substringBeforeLast('.', missingDelimiterValue = fileName).ifBlank { "cover" }
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").ifBlank { "jpg" }
    var counter = 1
    while (true) {
        val candidateName = "${stem}_$counter.$extension"
        val candidate = normalizeEpubPath(
            if (directory.isBlank()) candidateName else "$directory/$candidateName"
        )
        if (candidate.lowercase() !in used) return candidate
        counter += 1
    }
}

internal fun isHtmlPath(path: String): Boolean {
    return path.endsWith(".xhtml", ignoreCase = true) ||
        path.endsWith(".html", ignoreCase = true) ||
        path.endsWith(".htm", ignoreCase = true)
}

internal fun guessMediaType(path: String): String {
    return when (path.substringAfterLast('.', "").lowercase()) {
        "xhtml", "html", "htm" -> "application/xhtml+xml"
        "css" -> "text/css"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        "otf" -> "font/otf"
        "ttf" -> "font/ttf"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        else -> "application/octet-stream"
    }
}

internal fun setXmlAttribute(tag: String, name: String, value: String): String {
    val escaped = value.escapeXmlText()
    val pattern = Regex("""\b${Regex.escape(name)}\s*=\s*(['"])([^'"]*)\1""", RegexOption.IGNORE_CASE)
    if (pattern.containsMatchIn(tag)) {
        return pattern.replace(tag, "$name=\"$escaped\"")
    }
    val suffix = if (tag.endsWith("/>")) "/>" else ">"
    val head = if (suffix == "/>") tag.dropLast(2).trimEnd() else tag.dropLast(1).trimEnd()
    return "$head $name=\"$escaped\"$suffix"
}

internal fun String.toCrlfLineEndings(): String {
    return replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\n", "\r\n")
}

internal fun normalizeEpubChapterLineEndingsToCrlf(book: EpubBook, chapter: EpubChapter) {
    val normalized = chapter.html.toCrlfLineEndings()
    chapter.html = normalized
    updateEpubChapterHtmlEntry(book, chapter)
}

internal fun epubSplitTitleLineCandidate(lines: List<String>, startIndex: Int): Pair<Int, String>? {
    return lines
        .drop(startIndex.coerceAtLeast(0))
        .mapIndexedNotNull { offset, line ->
            val title = ChapterDetector.cleanTitle(ChapterDetector.stripHtml(line))
            if (title.isBlank()) null else (startIndex + offset) to title
        }
        .firstOrNull()
}

internal fun epubChapterBodyLines(chapter: EpubChapter): List<String> {
    val body = htmlVisibleBodyContent(chapter.html).trim('\r', '\n')
    if (body.isBlank()) return emptyList()
    return body.split('\n').map { it.removeSuffix("\r") }
}

private data class HtmlElementBounds(
    val openStart: Int,
    val openEnd: Int,
    val closeStart: Int,
    val closeEnd: Int
)

private fun replaceIntroBodyPreservingStructure(body: String, introBody: String): String? {
    val divRegex = Regex("""<div\b[^>]*>""", RegexOption.IGNORE_CASE)
    val divCandidates = divRegex.findAll(body)
        .mapNotNull { divOpen ->
            val divBounds = htmlElementBounds(body, divOpen, "div") ?: return@mapNotNull null
            val divContent = body.substring(divBounds.openEnd, divBounds.closeStart)
            if (divContent.isBlank()) return@mapNotNull null
            divBounds to divContent
        }
        .toList()
    val targetDiv = divCandidates.firstOrNull { (_, divContent) ->
        introHeadingRegex().containsMatchIn(divContent)
    } ?: divCandidates.firstOrNull()
    if (targetDiv != null) {
        val (divBounds, divContent) = targetDiv
        val nextDivContent = replaceIntroContainerContent(divContent, introBody)
        return body.replaceRange(divBounds.openEnd, divBounds.closeStart, nextDivContent)
    }

    val headingMatch = introHeadingRegex().find(body) ?: return null
    val prefix = body.substring(0, headingMatch.range.last + 1).trimEnd('\r', '\n')
    return "$prefix\n${introBody.trim('\r', '\n')}\n"
}

private val bodyOpenRegex = Regex("""<body\b[^>]*>""", RegexOption.IGNORE_CASE)
private val bodyCloseRegex = Regex("""</body>""", RegexOption.IGNORE_CASE)
private val htmlOpenRegex = Regex("""<html\b[^>]*>""", RegexOption.IGNORE_CASE)
private val htmlCloseRegex = Regex("""</html>""", RegexOption.IGNORE_CASE)
private val headCloseRegex = Regex("""</head>""", RegexOption.IGNORE_CASE)

private fun replaceIntroContainerContent(containerContent: String, introBody: String): String {
    val headingMatch = introHeadingRegex().find(containerContent)
    return if (headingMatch != null) {
        val prefix = containerContent.substring(0, headingMatch.range.last + 1).trimEnd('\r', '\n')
        "$prefix\n${introBody.trim('\r', '\n')}\n"
    } else {
        "\n${introBody.trim('\r', '\n')}\n"
    }
}

private fun introHeadingRegex(): Regex {
    return Regex(
        """<((?:h1|h2|h3|h4|h5|h6))\b[^>]*>.*?</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
}

private fun htmlElementBounds(html: String, openMatch: MatchResult, tagName: String): HtmlElementBounds? {
    val tagRegex = Regex(
        """</?\s*${Regex.escape(tagName)}\b[^>]*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    var depth = 0
    tagRegex.findAll(html, openMatch.range.first).forEach { match ->
        val tag = match.value
        val closing = tag.startsWith("</")
        val selfClosing = tag.endsWith("/>")
        if (!closing) {
            depth += 1
            if (selfClosing) depth -= 1
        } else {
            depth -= 1
            if (depth == 0) {
                return HtmlElementBounds(
                    openStart = openMatch.range.first,
                    openEnd = openMatch.range.last + 1,
                    closeStart = match.range.first,
                    closeEnd = match.range.last + 1
                )
            }
        }
    }
    return null
}
