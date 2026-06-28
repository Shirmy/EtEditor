package com.eteditor

import com.eteditor.core.TxtDocument

private const val VISIBLE_TEXT_REGEX_LONG_SEGMENT_CHARS = 1200
private const val VISIBLE_TEXT_REGEX_COMPLEX_PATTERN_CHARS = 240
private const val VISIBLE_TEXT_REGEX_ALTERNATION_LIMIT = 8
private const val VISIBLE_TEXT_REGEX_WIDE_ALTERNATION_LIMIT = 16
private const val TXT_PURIFY_REGEX_COST_WARNING =
    "净化规则正则过于复杂，可能在长 TXT 正文上卡住；请拆成更简单的规则，或改用普通文本匹配"

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
