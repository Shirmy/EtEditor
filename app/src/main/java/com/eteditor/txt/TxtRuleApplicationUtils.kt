package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.TxtChapter
import com.eteditor.core.TxtDocument

internal fun isLegacyDisabledRuleLine(line: String): Boolean {
    return line.firstOrNull() == '#' && line.getOrNull(1)?.isWhitespace() == true
}

internal fun stripLegacyDisabledRulePrefix(line: String): String {
    return line.drop(1).dropWhile { it.isWhitespace() }
}

// 书名/净化规则按 "查找=>替换" 存盘。若分隔符左侧字段(名称、查找式)的内容里本身含 "=>",
// 读回时会被当成分隔符切错。存盘时把左侧字段里的 "=>" 临时换成占位符,读取时还原;
// 替换内容在分隔符右侧,可以原样保留 "=>",无需处理。占位符为私有区字符,正常文本不会出现。
private const val RULE_ARROW_PLACEHOLDER = "\uE000"

internal fun encodeTxtRuleArrowSeparator(value: String): String {
    if (value.isEmpty()) return value
    return value.replace(RULE_ARROW_PLACEHOLDER, "").replace("=>", RULE_ARROW_PLACEHOLDER)
}

internal fun decodeTxtRuleArrowSeparator(value: String): String {
    if (value.isEmpty()) return value
    return value.replace(RULE_ARROW_PLACEHOLDER, "=>")
}

internal fun txtRuleRegexErrorMessage(
    label: String,
    pattern: String,
    regex: Boolean = true,
    options: Set<RegexOption> = emptySet()
): String? {
    if (!regex || pattern.isBlank()) return null
    val error = runCatching { Regex(pattern, options) }.exceptionOrNull() ?: return null
    return "$label 正则错误：${error.message ?: pattern}"
}

internal fun compileTxtPurifyRules(
    rules: List<TxtPurifyRuleItem>,
    onInvalidRule: (TxtPurifyRuleItem, Throwable) -> Unit = { _, _ -> }
): List<Pair<TxtPurifyRuleItem, Regex>>? {
    val compiled = mutableListOf<Pair<TxtPurifyRuleItem, Regex>>()
    rules.forEach { rule ->
        val regex = try {
            val pattern = if (rule.regex) rule.pattern else Regex.escape(rule.pattern)
            Regex(pattern, setOf(RegexOption.MULTILINE))
        } catch (error: IllegalArgumentException) {
            onInvalidRule(rule, error)
            return null
        }
        compiled += rule to regex
    }
    return compiled
}

internal fun applyTxtBodyPurifyRules(
    text: String,
    chapters: List<TxtChapter>,
    rules: List<Pair<TxtPurifyRuleItem, Regex>>
): Pair<String, Int> {
    if (rules.isEmpty()) return text to 0
    val ranges = txtBodyRanges(text, chapters)
    if (ranges.isEmpty()) return text to 0
    val builder = StringBuilder(text.length)
    var cursor = 0
    var count = 0
    ranges.forEach { (start, end) ->
        if (start < cursor || end <= start) return@forEach
        builder.append(text, cursor, start)
        val result = applyTxtPurifyRulesToSegment(text.substring(start, end), rules)
        builder.append(result.first)
        count += result.second
        cursor = end
    }
    builder.append(text, cursor, text.length)
    return builder.toString() to count
}

internal fun applyTxtCatalogPurifyRules(
    text: String,
    chapters: List<TxtChapter>,
    rules: List<Pair<TxtPurifyRuleItem, Regex>>
): Pair<String, Int> {
    var next = text
    var count = 0
    chapters.sortedByDescending { it.lineIndex }.forEach { chapter ->
        val result = applyTxtPurifyRulesToSegment(chapter.title, rules)
        val nextTitle = result.first
        if (nextTitle.isNotBlank() && nextTitle != chapter.title) {
            next = ChapterDetector.updateTxtTitle(next, chapter.lineIndex, nextTitle)
            count += 1
        }
    }
    return next to count
}

internal fun applyTxtPurifyRulesToSegment(
    segment: String,
    rules: List<Pair<TxtPurifyRuleItem, Regex>>
): Pair<String, Int> {
    var next = segment
    var count = 0
    rules.forEach { (rule, regex) ->
        next = regex.replace(next) { match ->
            count += 1
            if (rule.regex) {
                expandRegexReplacement(match, rule.replacement)
            } else {
                rule.replacement
            }
        }
    }
    return next to count
}

internal fun txtBodyRanges(text: String, chapters: List<TxtChapter>): List<Pair<Int, Int>> {
    if (text.isEmpty()) return emptyList()
    if (chapters.isEmpty()) return listOf(0 to text.length)
    val ranges = mutableListOf<Pair<Int, Int>>()
    val ordered = chapters.sortedBy { it.startIndex }
    val firstStart = ordered.first().startIndex.coerceIn(0, text.length)
    if (firstStart > 0) ranges += 0 to firstStart
    ordered.forEach { chapter ->
        val start = chapter.bodyStartIndex.coerceIn(0, text.length)
        val end = chapter.endIndex.coerceIn(start, text.length)
        if (end > start) ranges += start to end
    }
    return ranges
}

internal fun applyTxtPurifyTargetsToDocument(
    document: TxtDocument,
    rulesText: String,
    applyBody: Boolean,
    applyCatalog: Boolean,
    detectChapters: (String) -> List<TxtChapter>,
    onInvalidRule: (TxtPurifyRuleItem) -> Unit = {}
): TxtPurifyApplyResult? {
    if (!applyBody && !applyCatalog) {
        return TxtPurifyApplyResult(hasRules = false, bodyCount = 0, catalogCount = 0)
    }
    val rules = enabledTxtPurifyRules(rulesText)
    val bodyRules = if (applyBody) {
        compileTxtPurifyRules(rules.filter { it.target == TXT_PURIFY_TARGET_BODY }) { rule, _ ->
            onInvalidRule(rule)
        } ?: return null
    } else {
        emptyList()
    }
    val catalogRules = if (applyCatalog) {
        compileTxtPurifyRules(rules.filter { it.target == TXT_PURIFY_TARGET_CATALOG }) { rule, _ ->
            onInvalidRule(rule)
        } ?: return null
    } else {
        emptyList()
    }
    var text = document.text
    var chapters = document.chapters
    var bodyCount = 0
    var catalogCount = 0
    if (applyBody) {
        val bodyResult = applyTxtBodyPurifyRules(text, chapters, bodyRules)
        text = bodyResult.first
        bodyCount = bodyResult.second
        if (bodyCount > 0) {
            chapters = detectChapters(text)
        }
    }
    if (applyCatalog) {
        chapters = detectChapters(text)
        val catalogResult = applyTxtCatalogPurifyRules(text, chapters, catalogRules)
        text = catalogResult.first
        catalogCount += catalogResult.second
        if (catalogResult.second > 0) {
            chapters = detectChapters(text)
        }
    }
    val result = TxtPurifyApplyResult(
        hasRules = bodyRules.isNotEmpty() || catalogRules.isNotEmpty(),
        bodyCount = bodyCount,
        catalogCount = catalogCount
    )
    if (result.changed) {
        document.text = text
        document.chapters = detectChapters(document.text)
    }
    return result
}
