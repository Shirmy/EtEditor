package com.eteditor.core

import kotlin.text.MatchNamedGroupCollection

data class TxtChapterPatternRule(
    val pattern: String,
    val replacement: String = ""
)

object ChapterDetector {
    private val cjkNumber = "零〇一二两三四五六七八九十百千万亿壹贰叁肆伍陆柒捌玖拾佰仟0123456789０-９"
    private val zeroWidthTextRegex = Regex("[\\u00AD\\u180E\\u200B\\u200C\\u200D\\u200E\\u200F\\u202A-\\u202E\\u2060\\uFEFF]")

    fun defaultTxtChapterRules(): String = ""

    fun normalizeTxtCatalogMatchText(raw: String): String {
        return raw.replace(zeroWidthTextRegex, "").trim()
    }

    fun detectTxtChapters(
        text: String,
        shortThreshold: Int = 1000,
        longThreshold: Int = 10000,
        customPatterns: List<String> = emptyList(),
        customRules: List<TxtChapterPatternRule> = customPatterns.map { TxtChapterPatternRule(it) },
        applyRuleReplacementToTitle: Boolean = true,
        hiddenLineIndices: Set<Int> = emptySet(),
        forcedLineIndices: Set<Int> = emptySet()
    ): List<TxtChapter> {
        val compiledCustomPatterns = compileTxtChapterPatternRules(customRules)
        val lines = iterTextLines(text)
        val detectedHitsByLine = lines.mapNotNull { line ->
            val stripped = normalizeTxtCatalogMatchText(line.text)
            if (stripped.isBlank() || (stripped.length > 90 && line.lineIndex !in forcedLineIndices)) {
                null
            } else {
                val chapterMatch = txtChapterMatch(stripped, compiledCustomPatterns)
                if (chapterMatch == null && line.lineIndex !in forcedLineIndices) {
                    null
                } else {
                    val match = chapterMatch ?: forcedTxtChapterMatch(stripped)
                    TxtChapterHit(
                        lineIndex = line.lineIndex,
                        lineNumber = line.lineNumber,
                        rawTitle = line.text,
                        chapterMatch = match,
                        startIndex = line.startIndex,
                        bodyStartIndex = line.nextIndex,
                        number = txtChapterNumber(match.match),
                        suppressNumberStatus = match.replacement.contains("{index}")
                    )
                }
            }
        }.associateBy { it.lineIndex }
        val detectedHits = detectedHitsByLine.values.sortedBy { it.lineIndex }
        val hits = if (hiddenLineIndices.isEmpty()) {
            detectedHits
        } else {
            detectedHits.filterNot { it.lineIndex in hiddenLineIndices }
        }

        val titles = hits.mapIndexed { idx, hit ->
            if (applyRuleReplacementToTitle) {
                mappedTxtChapterTitle(hit.rawTitle, hit.chapterMatch, idx + 1)
            } else {
                hit.rawTitle
            }
        }
        val titleNumbers = titles.map { txtChapterNumberFromTitle(it) }

        // Precompute, in a single linear pass, the two per-chapter signals that
        // previously required re-scanning all earlier chapters (O(n^2) each):
        //   - whether this chapter's whitespace-normalized title already appeared earlier (重名);
        //   - whether any earlier chapter carried a number (used by the 疑似缺章 check).
        val normalizedTitles = titles.map { it.replace(whitespaceRegex, "").lowercase() }
        val duplicateTitleFlags = ArrayList<Boolean>(titles.size)
        val hasPreviousNumberedFlags = ArrayList<Boolean>(titles.size)
        run {
            val seenTitles = HashSet<String>(titles.size * 2)
            var seenNumbered = false
            for (idx in titles.indices) {
                val normalized = normalizedTitles[idx]
                duplicateTitleFlags += normalized in seenTitles
                seenTitles += normalized
                hasPreviousNumberedFlags += seenNumbered
                if (titleNumbers[idx] != null) seenNumbered = true
            }
        }

        return hits.mapIndexed { idx, hit ->
            val endLineIndex = hits.getOrNull(idx + 1)?.lineIndex ?: lines.size
            val endIndex = hits.getOrNull(idx + 1)?.startIndex ?: text.length
            val content = text.substring(hit.bodyStartIndex.coerceAtMost(text.length), endIndex.coerceAtMost(text.length))
            val wordCount = countVisibleChars(content)
            val status = txtChapterStatus(
                titleNumbers = titleNumbers,
                currentIndex = idx,
                wordCount = wordCount,
                shortThreshold = shortThreshold,
                longThreshold = longThreshold,
                isDuplicateTitle = duplicateTitleFlags[idx],
                hasPreviousNumberedChapter = hasPreviousNumberedFlags[idx]
            )
            TxtChapter(
                index = idx + 1,
                lineIndex = hit.lineIndex,
                endLineIndex = endLineIndex,
                title = titles[idx],
                wordCount = wordCount,
                startIndex = hit.startIndex,
                bodyStartIndex = hit.bodyStartIndex,
                endIndex = endIndex,
                number = titleNumbers[idx],
                status = status
            )
        }
    }

    fun formatTxtLayout(
        text: String,
        customPatterns: List<String> = emptyList(),
        customRules: List<TxtChapterPatternRule> = customPatterns.map { TxtChapterPatternRule(it) },
        applyRuleReplacementToTitle: Boolean = true,
        hiddenLineIndices: Set<Int> = emptySet(),
        forcedLineIndices: Set<Int> = emptySet()
    ): TxtFormatResult {
        val compiledCustomPatterns = compileTxtChapterPatternRules(customRules)
        val output = mutableListOf<String>()
        var removedBlank = 0
        var contentLines = 0
        var chapterLines = 0

        text.lineSequence().map { it.removeSuffix("\r") }.forEachIndexed { lineIndex, raw ->
            val stripped = raw.trim()
            val matchText = normalizeTxtCatalogMatchText(raw)
            if (stripped.isBlank()) {
                removedBlank += 1
                return@forEachIndexed
            }
            val chapterMatch = if (lineIndex in hiddenLineIndices) {
                null
            } else {
                txtChapterMatch(matchText, compiledCustomPatterns)
                    ?: if (lineIndex in forcedLineIndices) forcedTxtChapterMatch(matchText) else null
            }
            if (chapterMatch != null) {
                if (output.isNotEmpty()) {
                    var existingBlank = 0
                    for (index in output.lastIndex downTo 0) {
                        if (output[index].isNotBlank()) break
                        existingBlank += 1
                    }
                    repeat((2 - existingBlank).coerceAtLeast(0)) { output += "" }
                }
                output += if (applyRuleReplacementToTitle) {
                    mappedTxtChapterTitle(stripped, chapterMatch, chapterLines + 1)
                } else {
                    stripped
                }
                chapterLines += 1
            } else {
                output += "\u3000\u3000$stripped"
                contentLines += 1
            }
        }

        return TxtFormatResult(
            text = output.joinToString("\n"),
            removedBlankCount = removedBlank,
            contentLineCount = contentLines,
            chapterLineCount = chapterLines
        )
    }

    fun parseNumber(value: String): Int? {
        val normalized = value.trim()
            .map { char -> if (char in '０'..'９') ('0'.code + (char.code - '０'.code)).toChar() else char }
            .joinToString("")
        if (normalized.isBlank()) return null
        normalized.toIntOrNull()?.let { return it }

        val digits = mapOf(
            '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
            '壹' to 1, '贰' to 2, '叁' to 3, '肆' to 4, '伍' to 5, '陆' to 6,
            '柒' to 7, '捌' to 8, '玖' to 9
        )
        val units = mapOf('十' to 10, '拾' to 10, '百' to 100, '佰' to 100, '千' to 1000, '仟' to 1000)
        if (normalized.all { it in digits }) {
            return normalized.map { digits[it].toString() }.joinToString("").toIntOrNull()
        }

        var total = 0
        var section = 0
        var number = 0
        normalized.forEach { char ->
            when {
                char in digits -> number = digits.getValue(char)
                char in units -> {
                    val unit = units.getValue(char)
                    if (number == 0) number = 1
                    section += number * unit
                    number = 0
                }
                char == '万' -> {
                    section += number
                    total += section * 10000
                    section = 0
                    number = 0
                }
                else -> return null
            }
        }
        return total + section + number
    }

    fun txtChapterNumberFromTitle(title: String): Int? {
        val normalized = normalizeTxtCatalogMatchText(cleanTitleLineBreaksAsSpace(title))
        val numberedPrefix = Regex("""^\s*第\s*([$cjkNumber]{1,12})\s*(?:章|节|節|回|集|卷|部|篇|话|話)[\s\S]*${'$'}""")
            .find(normalized)
        if (numberedPrefix != null) {
            return parseNumber(numberedPrefix.groupValues[1])
        }

        val numericPrefix = Regex("""^\s*([0-9０-９]{1,5})(?:[\s.、:：\-_]|${'$'})[\s\S]*${'$'}""")
            .find(normalized)
        return numericPrefix?.groupValues?.getOrNull(1)?.let { parseNumber(it) }
    }

    fun updateTxtTitle(text: String, lineIndex: Int, newTitle: String): String {
        val lines = text.split('\n').map { it.removeSuffix("\r") }.toMutableList()
        if (lineIndex in lines.indices) {
            lines[lineIndex] = newTitle
        }
        return lines.joinToString("\n")
    }

    fun updateTxtTitles(text: String, titlesByLineIndex: Map<Int, String>): String {
        if (titlesByLineIndex.isEmpty()) return text
        val lines = text.split('\n').map { it.removeSuffix("\r") }.toMutableList()
        titlesByLineIndex.forEach { (lineIndex, title) ->
            if (lineIndex in lines.indices && title.isNotBlank()) {
                lines[lineIndex] = title
            }
        }
        return lines.joinToString("\n")
    }

    fun cleanTitle(raw: String): String {
        var title = raw
            .replace('\u3000', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()

        title = title
            .replace(Regex("""^[【\[]?(正文|目录|章节)[】\]]?\s*[:：-]?\s*"""), "")
            .replace(Regex("""\[(VIP|番外|福利番外|作话锁|锁|本章节已锁定)]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""（(VIP|番外|福利番外|作话锁|锁|本章节已锁定)）""", RegexOption.IGNORE_CASE), "")
            .trim()

        val numericPrefix = Regex("""^(\d{1,5})\s*[、.．]\s*(.+)${'$'}""").find(title)
        if (numericPrefix != null) {
            return "${numericPrefix.groupValues[1]}、${numericPrefix.groupValues[2].trim()}"
        }

        val chinesePrefix = Regex("""^(第\s*[$cjkNumber]{1,12}\s*[章节回卷部集])\s*(.*)${'$'}""").find(title)
        if (chinesePrefix != null) {
            val prefix = chinesePrefix.groupValues[1].replace(Regex("""\s+"""), "")
            val suffix = chinesePrefix.groupValues[2].trim()
            return if (suffix.isBlank()) prefix else "$prefix $suffix"
        }

        return title
    }

    fun normalizePastedTitles(raw: String): List<String> {
        return raw.lineSequence()
            .map { parsePastedTitleLine(it) }
            .filter { it.isNotBlank() }
            .toList()
    }

    fun extractTitleFromHtml(html: String, fallback: String): String {
        headingRegex.find(html)?.let { return cleanTitle(stripHtml(it.groupValues[3])) }
        titleRegex.find(html)?.let { return cleanTitle(stripHtml(it.groupValues[1])) }
        return fallback.substringAfterLast('/').substringBeforeLast('.').ifBlank { "未命名章节" }
    }

    fun extractTitleFromHtmlWithBreaks(html: String, fallback: String): String {
        headingRegex.find(html)?.let { return normalizeTitleHtmlTextWithBreaks(it.groupValues[3]) }
        titleRegex.find(html)?.let { return normalizeTitleHtmlTextWithBreaks(it.groupValues[1]) }
        return fallback.substringAfterLast('/').substringBeforeLast('.').ifBlank { "未命名章节" }
    }

    fun updateHtmlTitle(html: String, newTitle: String): String {
        val escaped = escapeHtml(cleanTitle(newTitle))
        var updated = if (titleRegex.containsMatchIn(html)) {
            html.replace(titleRegex, "<title>$escaped</title>")
        } else {
            html
        }

        updated = headingRegex.find(updated)?.let { match ->
            updated.replaceRange(
                match.range,
                "<${match.groupValues[1]}${match.groupValues[2]}>$escaped</${match.groupValues[1]}>"
            )
        } ?: run {
            val body = Regex("""<body([^>]*)>""", RegexOption.IGNORE_CASE)
            body.find(updated)?.let { match ->
                updated.replaceRange(match.range, "${match.value}\n<h1>$escaped</h1>")
            } ?: run {
                "<h1>$escaped</h1>\n$updated"
            }
        }
        return updated
    }

    fun cleanTitleLineBreaksAsSpace(raw: String): String {
        return cleanTitle(raw.replace(htmlBreakRegex, " "))
    }

    fun titleHeadingHtmlWithLineBreaks(raw: String): String {
        val cleaned = cleanTitle(raw)
        if (cleanTitleLineBreaksAsSpace(cleaned).isBlank()) return ""
        return htmlBreakRegex
            .split(cleaned)
            .joinToString("<br/>") { part -> escapeHtml(part) }
    }

    fun updateHtmlTitleWithLineBreaks(html: String, newTitle: String): String {
        val escapedTitle = escapeHtml(cleanTitleLineBreaksAsSpace(newTitle))
        val headingHtml = titleHeadingHtmlWithLineBreaks(newTitle).ifBlank { escapedTitle }
        var updated = if (titleRegex.containsMatchIn(html)) {
            html.replace(titleRegex, "<title>$escapedTitle</title>")
        } else {
            html
        }

        updated = headingRegex.find(updated)?.let { match ->
            updated.replaceRange(
                match.range,
                "<${match.groupValues[1]}${match.groupValues[2]}>$headingHtml</${match.groupValues[1]}>"
            )
        } ?: run {
            val body = Regex("""<body([^>]*)>""", RegexOption.IGNORE_CASE)
            body.find(updated)?.let { match ->
                updated.replaceRange(match.range, "${match.value}\n<h1>$headingHtml</h1>")
            } ?: run {
                "<h1>$headingHtml</h1>\n$updated"
            }
        }
        return updated
    }

    fun countHtmlChars(html: String): Int = countVisibleChars(stripHtml(html))

    fun countVisibleChars(text: String): Int {
        return text.count { !it.isWhitespace() && !it.isISOControl() }
    }

    fun stripHtml(html: String): String {
        return html
            .replace(Regex("""<script\b[^>]*>.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<style\b[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<[^>]+>"""), " ")
            .htmlUnescape()
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun extractBodyMarkup(html: String): String {
        bodyRegex.find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it.trim() }

        bodyOpenRegex.find(html)?.let { open ->
            val start = open.range.last + 1
            val end = htmlCloseRegex.find(html, start)?.range?.first ?: html.length
            return html.substring(start, end)
                .replace(bodyCloseRegex, "")
                .trim()
        }

        headCloseRegex.find(html)?.let { close ->
            val start = close.range.last + 1
            val end = htmlCloseRegex.find(html, start)?.range?.first ?: html.length
            return html.substring(start, end)
                .replace(bodyCloseRegex, "")
                .trim()
        }

        htmlOpenRegex.find(html)?.let { open ->
            val start = open.range.last + 1
            val end = htmlCloseRegex.find(html, start)?.range?.first ?: html.length
            return html.substring(start, end).trim()
        }

        return html
            .replace(xmlDeclarationRegex, "")
            .replace(docTypeRegex, "")
            .replace(headRegex, "")
            .replace(htmlWrapperRegex, "")
            .replace(bodyCloseRegex, "")
            .trim()
    }

    fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun parsePastedTitleLine(raw: String): String {
        val line = raw.trim()
        if (line.isBlank()) return ""
        if (line == "章节" || line == "标题" || line.startsWith("章节 ")) return ""

        val tabParts = line.split('\t').map { it.trim() }.filter { it.isNotBlank() }
        if (tabParts.size >= 2 && tabParts.first().matches(Regex("""\d+"""))) {
            return cleanTitle(tabParts[1])
        }

        Regex("""^\d{1,5}\s+(.+?)\s+\d{2,}\s+""").find(line)?.let {
            return cleanTitle(it.groupValues[1])
        }

        Regex("""^\d{1,5}\s+(.+)${'$'}""").find(line)?.let {
            return cleanTitle(it.groupValues[1])
        }

        return cleanTitle(line)
    }

    private fun String.htmlUnescape(): String {
        return replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }

    private data class TxtLine(
        val lineIndex: Int,
        val lineNumber: Int,
        val text: String,
        val startIndex: Int,
        val nextIndex: Int
    )

    private data class TxtChapterHit(
        val lineIndex: Int,
        val lineNumber: Int,
        val rawTitle: String,
        val chapterMatch: TxtChapterMatch,
        val startIndex: Int,
        val bodyStartIndex: Int,
        val number: Int?,
        val suppressNumberStatus: Boolean
    )

    data class TxtFormatResult(
        val text: String,
        val removedBlankCount: Int,
        val contentLineCount: Int,
        val chapterLineCount: Int
    )

    private data class CompiledTxtChapterPatternRule(
        val regex: Regex,
        val replacement: String
    )

    private data class TxtChapterMatch(
        val match: MatchResult,
        val replacement: String
    )

    private fun compileTxtChapterPatternRules(
        rules: List<TxtChapterPatternRule>
    ): List<CompiledTxtChapterPatternRule> {
        return rules
            .mapNotNull { rule ->
                val pattern = rule.pattern.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                runCatching {
                    CompiledTxtChapterPatternRule(
                        regex = Regex(pattern),
                        replacement = rule.replacement.trim()
                    )
                }.getOrNull()
            }
    }

    private fun iterTextLines(text: String): List<TxtLine> {
        val result = mutableListOf<TxtLine>()
        var start = 0
        var lineIndex = 0
        while (start < text.length) {
            var end = start
            while (end < text.length && text[end] != '\n' && text[end] != '\r') {
                end += 1
            }
            var next = end
            if (next < text.length) {
                if (text[next] == '\r' && next + 1 < text.length && text[next + 1] == '\n') {
                    next += 2
                } else {
                    next += 1
                }
            }
            result += TxtLine(
                lineIndex = lineIndex,
                lineNumber = lineIndex + 1,
                text = text.substring(start, end),
                startIndex = start,
                nextIndex = next
            )
            lineIndex += 1
            start = next
        }
        if (text.isEmpty()) {
            result += TxtLine(0, 1, "", 0, 0)
        }
        return result
    }

    private fun txtChapterMatch(
        value: String,
        customPatterns: List<CompiledTxtChapterPatternRule>
    ): TxtChapterMatch? {
        customPatterns.forEach { pattern ->
            pattern.regex.find(value)?.let { match ->
                if (match.range.first == 0) {
                    return TxtChapterMatch(match, pattern.replacement)
                }
            }
        }
        return null
    }

    private val forcedNumberedTxtChapterRegex =
        Regex("""^\s*第\s*([$cjkNumber]{1,12})\s*(?:章|节|節|回|集|卷|部|篇|话|話)[\s\S]*${'$'}""")
    private val forcedAnyTxtChapterRegex = Regex("""^[\s\S]+${'$'}""")

    private fun forcedTxtChapterMatch(value: String): TxtChapterMatch {
        val match = forcedNumberedTxtChapterRegex.find(value)
            ?: forcedAnyTxtChapterRegex.find(value)
            ?: error("Forced chapter line is empty")
        return TxtChapterMatch(match, "")
    }

    private fun mappedTxtChapterTitle(
        fallbackTitle: String,
        chapterMatch: TxtChapterMatch,
        chapterIndex: Int
    ): String {
        val replacement = chapterMatch.replacement
        if (replacement.isBlank()) return fallbackTitle
        return expandTxtChapterReplacement(chapterMatch.match, replacement, chapterIndex)
            .trim()
            .ifBlank { fallbackTitle }
    }

    private fun expandTxtChapterReplacement(
        match: MatchResult,
        replacement: String,
        chapterIndex: Int
    ): String {
        val builder = StringBuilder()
        var index = 0
        while (index < replacement.length) {
            val char = replacement[index]
            if (replacement.startsWith("{index}", index)) {
                builder.append(chapterIndex)
                index += "{index}".length
                continue
            }
            if (char == '\\' && index + 1 < replacement.length && replacement[index + 1] == '$') {
                builder.append('$')
                index += 2
                continue
            }
            if (char == '$' && index + 1 < replacement.length && replacement[index + 1].isDigit()) {
                var end = index + 1
                while (end < replacement.length && replacement[end].isDigit()) {
                    end += 1
                }
                val groupIndex = replacement.substring(index + 1, end).toIntOrNull()
                val groupValue = if (groupIndex != null && groupIndex < match.groups.size) {
                    match.groups[groupIndex]?.value.orEmpty()
                } else {
                    ""
                }
                builder.append(groupValue)
                index = end
                continue
            }
            builder.append(char)
            index += 1
        }
        return builder.toString()
    }

    private fun txtChapterNumber(match: MatchResult): Int? {
        val named = listOf("num", "num2", "number")
            .firstNotNullOfOrNull { name ->
                runCatching {
                    (match.groups as? MatchNamedGroupCollection)
                        ?.get(name)
                        ?.value
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()
            }
        if (named != null) return parseNumber(named)
        return match.groupValues
            .drop(1)
            .firstNotNullOfOrNull { value -> if (value.isBlank()) null else parseNumber(value) }
    }

    private fun txtChapterStatus(
        titleNumbers: List<Int?>,
        currentIndex: Int,
        wordCount: Int,
        shortThreshold: Int,
        longThreshold: Int,
        isDuplicateTitle: Boolean,
        hasPreviousNumberedChapter: Boolean
    ): List<String> {
        val statuses = mutableListOf<String>()
        if (isDuplicateTitle) {
            statuses += "重名"
        }
        if (wordCount in 1 until shortThreshold) statuses += "短章"
        if (longThreshold > 0 && wordCount > longThreshold) statuses += "超长章"

        val number = titleNumbers.getOrNull(currentIndex)
        val previousNumber = titleNumbers.getOrNull(currentIndex - 1)
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

    private val titleRegex = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val headingRegex = Regex("""<((?:h1|h2|h3))([^>]*)>(.*?)</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val bodyRegex = Regex("""<body\b[^>]*>(.*?)</body>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val bodyOpenRegex = Regex("""<body\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val bodyCloseRegex = Regex("""</body>""", RegexOption.IGNORE_CASE)
    private val htmlOpenRegex = Regex("""<html\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val htmlCloseRegex = Regex("""</html>""", RegexOption.IGNORE_CASE)
    private val headCloseRegex = Regex("""</head>""", RegexOption.IGNORE_CASE)
    private val xmlDeclarationRegex = Regex("""<\?xml\b[^>]*\?>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val docTypeRegex = Regex("""<!DOCTYPE\b.*?>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val headRegex = Regex("""<head\b[^>]*>.*?</head>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val htmlWrapperRegex = Regex("""</?html\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val htmlBreakRegex = Regex("""(?i)<br\s*/?>""")
    private val whitespaceRegex = Regex("""\s+""")

    private fun normalizeTitleHtmlTextWithBreaks(raw: String): String {
        val placeholder = "\u0000BR\u0000"
        return stripHtml(raw.replace(htmlBreakRegex, placeholder))
            .replace(placeholder, "<br/>")
    }
}
