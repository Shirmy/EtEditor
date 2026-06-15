package com.eteditor

import com.eteditor.core.TxtDocument

private const val VISIBLE_TEXT_REGEX_LONG_SEGMENT_CHARS = 1200
private const val VISIBLE_TEXT_REGEX_COMPLEX_PATTERN_CHARS = 240
private const val VISIBLE_TEXT_REGEX_ALTERNATION_LIMIT = 8
private const val VISIBLE_TEXT_REGEX_WIDE_ALTERNATION_LIMIT = 16
private const val VISIBLE_TEXT_REGEX_COST_WARNING =
    "仅文本正则过于复杂，可能在长正文段落上卡住；请切换为“正文源码 + 正则”，或把这条正则拆成多条更简单的规则"
private const val TEXT_REGEX_COST_WARNING =
    "正则过于复杂，可能在长正文上卡住；请拆成更简单的规则，或改用普通文本匹配"
private const val TXT_PURIFY_REGEX_COST_WARNING =
    "净化规则正则过于复杂，可能在长 TXT 正文上卡住；请拆成更简单的规则，或改用普通文本匹配"

internal fun visibleTextRegexCostWarning(
    rules: List<TextReplaceRule>,
    longestSegmentLength: Int
): String? {
    val riskyRule = rules.firstOrNull { rule ->
        rule.enabled &&
            rule.textOnly &&
            rule.regex &&
            rule.find.isNotEmpty() &&
            isHighCostVisibleTextRegex(rule.find)
    } ?: return null
    return if (isVisibleTextRegexRiskyForVisibleSegments(riskyRule.find, longestSegmentLength)) {
        VISIBLE_TEXT_REGEX_COST_WARNING
    } else {
        null
    }
}

internal fun visibleTextRegexCostWarningForSources(
    rules: List<TextReplaceRule>,
    sources: Iterable<SearchSource>
): String? {
    return visibleTextRegexCostWarning(
        rules = rules,
        longestSegmentLength = longestVisibleTextSegmentLength(sources)
    )
}

internal fun textRegexCostWarningForSources(
    rules: List<TextReplaceRule>,
    sources: Iterable<SearchSource>
): String? {
    val riskyRule = rules.firstOrNull { rule ->
        rule.enabled &&
            rule.regex &&
            rule.find.isNotEmpty() &&
            isHighCostTextRegex(rule.find)
    } ?: return null
    return if (isTextRegexRiskyForLength(riskyRule.find, longestSearchSourceTextLength(sources))) {
        TEXT_REGEX_COST_WARNING
    } else {
        null
    }
}

internal fun txtPurifyRegexCostWarningForDocument(
    document: TxtDocument,
    rules: List<TxtPurifyRuleItem>,
    applyBody: Boolean,
    applyCatalog: Boolean,
    requireEnabled: Boolean = true
): String? {
    val activeRules = rules.filter { rule ->
        (!requireEnabled || rule.enabled) &&
            rule.regex &&
            rule.pattern.isNotEmpty() &&
            isHighCostTextRegex(rule.pattern)
    }
    if (activeRules.isEmpty()) return null
    val longest = activeRules.maxOf { rule ->
        when (normalizeTxtPurifyTarget(rule.target)) {
            TXT_PURIFY_TARGET_CATALOG -> if (applyCatalog) {
                document.chapters.maxOfOrNull { it.title.length } ?: 0
            } else {
                0
            }
            else -> if (applyBody) {
                txtBodyRanges(document.text, document.chapters).maxOfOrNull { (start, end) ->
                    end - start
                } ?: 0
            } else {
                0
            }
        }
    }
    val risky = activeRules.any { rule -> isTextRegexRiskyForLength(rule.pattern, longest) }
    return if (risky) TXT_PURIFY_REGEX_COST_WARNING else null
}

internal fun isVisibleTextRegexRiskyForVisibleSegments(
    pattern: String,
    longestSegmentLength: Int
): Boolean {
    if (!isHighCostVisibleTextRegex(pattern)) return false
    if (longestSegmentLength <= 0) return false
    if (longestSegmentLength < VISIBLE_TEXT_REGEX_LONG_SEGMENT_CHARS &&
        pattern.length < VISIBLE_TEXT_REGEX_COMPLEX_PATTERN_CHARS
    ) {
        return false
    }
    return true
}

internal fun isTextRegexRiskyForLength(
    pattern: String,
    sourceLength: Int
): Boolean {
    if (!isHighCostTextRegex(pattern)) return false
    if (sourceLength <= 0) return false
    if (sourceLength < VISIBLE_TEXT_REGEX_LONG_SEGMENT_CHARS &&
        pattern.length < VISIBLE_TEXT_REGEX_COMPLEX_PATTERN_CHARS
    ) {
        return false
    }
    return true
}

internal fun isHighCostVisibleTextRegex(pattern: String): Boolean {
    return isHighCostTextRegex(pattern)
}

internal fun isHighCostTextRegex(pattern: String): Boolean {
    val alternationCount = pattern.count { it == '|' }
    val hasBroadWildcard = pattern.contains(".*") || pattern.contains(".+")
    val hasLookAroundWildcard = Regex("""\(\?(?:[=!]|<[=!])[^)]*\.\*""").containsMatchIn(pattern)
    val hasRepeatedGroup = Regex("""\([^)]*[+*][^)]*\)[+*{]""").containsMatchIn(pattern)
    return hasLookAroundWildcard ||
        hasRepeatedGroup ||
        (alternationCount >= VISIBLE_TEXT_REGEX_ALTERNATION_LIMIT && hasBroadWildcard) ||
        (alternationCount >= VISIBLE_TEXT_REGEX_WIDE_ALTERNATION_LIMIT &&
            pattern.length >= VISIBLE_TEXT_REGEX_COMPLEX_PATTERN_CHARS)
}

internal fun visibleSegmentLengthIfText(text: String, start: Int, end: Int): Int {
    if (end <= start) return 0
    for (index in start until end) {
        if (!text[index].isWhitespace()) return end - start
    }
    return 0
}

internal fun longestVisibleTextSegmentLength(sources: Iterable<SearchSource>): Int {
    var longest = 0
    sources.forEach { source ->
        var cursor = 0
        Regex("""<[^>]+>""").findAll(source.text).forEach { tag ->
            longest = visibleSegmentLengthIfText(source.text, cursor, tag.range.first)
                .coerceAtLeast(longest)
            cursor = tag.range.last + 1
        }
        longest = visibleSegmentLengthIfText(source.text, cursor, source.text.length)
            .coerceAtLeast(longest)
    }
    return longest
}

internal fun longestSearchSourceTextLength(sources: Iterable<SearchSource>): Int {
    var longest = 0
    sources.forEach { source ->
        longest = source.text.length.coerceAtLeast(longest)
    }
    return longest
}
