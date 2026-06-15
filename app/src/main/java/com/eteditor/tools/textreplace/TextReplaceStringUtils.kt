package com.eteditor

import com.eteditor.core.ChapterDetector
import org.json.JSONArray

private const val REPLACEMENT_SEPARATOR = "#->#"

internal fun decodeLineBreakEscapes(pattern: String): String {
    return pattern
        .replace("\\r\\n", "\r\n")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
}

internal fun buildTextReplaceRules(
    parameters: TextReplaceParameters,
    singleMode: String,
    replacementMode: String,
    visibleTextTarget: String
): TextReplaceRuleBuildResult {
    if (parameters.mode == singleMode) {
        return TextReplaceRuleBuildResult(
            rules = listOf(
                TextReplaceRule(
                    find = decodeLineBreakEscapes(parameters.findText),
                    replacement = decodeLineBreakEscapes(parameters.replaceText),
                    regex = parameters.findRegexEnabled,
                    textOnly = parameters.target == visibleTextTarget
                )
            ).filter { it.find.isNotEmpty() }
        )
    }
    if (parameters.mode == replacementMode) {
        return TextReplaceRuleBuildResult(
            rules = null,
            message = "请先生成 .replacement 分组预览"
        )
    }
    val batchText = parameters.batchText
    if (batchText.trim().startsWith("[")) {
        val structuredRules = runCatching {
            val array = JSONArray(batchText)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val find = json.optString("search")
                if (find.isEmpty()) return@mapNotNull null
                TextReplaceRule(
                    find = decodeLineBreakEscapes(find),
                    replacement = decodeLineBreakEscapes(json.optString("replacement")),
                    regex = json.optBoolean("regex", parameters.findRegexEnabled),
                    textOnly = json.optBoolean("textOnly", false)
                )
            }
        }.getOrNull()
            ?: return TextReplaceRuleBuildResult(null, "批量规则格式错误")
        return TextReplaceRuleBuildResult(
            rules = structuredRules,
            message = if (structuredRules.isEmpty()) "请输入批量文本" else ""
        )
    }
    val rules = mutableListOf<TextReplaceRule>()
    for (line in batchText.lineSequence().map { it.removeSuffix("\r") }.filter { it.isNotBlank() }) {
        val separator = line.indexOf("=>")
        if (separator < 0) {
            return TextReplaceRuleBuildResult(null, "批量规则格式错误")
        }
        val find = line.substring(0, separator)
        if (find.isNotEmpty()) {
            rules += TextReplaceRule(
                find = decodeLineBreakEscapes(find),
                replacement = decodeLineBreakEscapes(line.substring(separator + 2)),
                regex = parameters.findRegexEnabled,
                textOnly = parameters.target == visibleTextTarget
            )
        }
    }
    return TextReplaceRuleBuildResult(
        rules = rules,
        message = if (rules.isEmpty()) "请输入批量文本" else ""
    )
}

internal fun textReplaceRulesFromParsedReplacementRules(
    parsedRules: List<ParsedReplacementRule>
): List<TextReplaceRule> {
    return parsedRules.map { rule ->
        TextReplaceRule(
            find = rule.pattern,
            replacement = rule.replacement,
            regex = rule.regex,
            textOnly = false
        )
    }
}

internal fun buildTextReplaceRulesFromReplacementFileText(ruleText: String): TextReplaceRuleBuildResult {
    val (parsedRules, skippedRules) = try {
        parseReplacementRules(ruleText)
    } catch (error: IllegalArgumentException) {
        return TextReplaceRuleBuildResult(
            rules = null,
            message = error.message ?: "规则文件解析失败"
        )
    }
    if (parsedRules.isEmpty()) {
        return TextReplaceRuleBuildResult(
            rules = null,
            message = if (skippedRules.isEmpty()) {
                "规则文件为空或没有可读取规则"
            } else {
                "没有有效规则：无效 ${skippedRules.size}/${skippedRules.size}"
            }
        )
    }
    return TextReplaceRuleBuildResult(
        rules = textReplaceRulesFromParsedReplacementRules(parsedRules)
    )
}

internal fun plainSearchRanges(source: String, find: String): List<Pair<Int, Int>> {
    return plainSearchRanges(source, find, caseSensitive = true)
}

internal fun plainSearchRanges(
    source: String,
    find: String,
    caseSensitive: Boolean
): List<Pair<Int, Int>> {
    if (find.isEmpty()) return emptyList()
    val ranges = mutableListOf<Pair<Int, Int>>()
    var index = 0
    while (index <= source.length - find.length) {
        val found = source.indexOf(find, startIndex = index, ignoreCase = !caseSensitive)
        if (found < 0) break
        ranges += found to (found + find.length)
        index = found + find.length
    }
    return ranges
}

internal fun regexSearchRanges(source: String, pattern: Regex): List<Pair<Int, Int>> {
    val ranges = mutableListOf<Pair<Int, Int>>()
    val matches = pattern.findAll(source).iterator()
    while (matches.hasNext()) {
        val match = matches.next()
        val start = match.range.first
        val end = match.range.last + 1
        if (end > start) ranges += start to end
    }
    return ranges
}

internal fun replaceInString(
    source: String,
    rule: TextReplaceRule,
    caseSensitive: Boolean
): Pair<String, Int> {
    if (rule.find.isEmpty()) return source to 0
    return if (rule.regex) {
        val options = if (caseSensitive) emptySet<RegexOption>() else setOf(RegexOption.IGNORE_CASE)
        val pattern = Regex(rule.find, options)
        replaceRegex(source, pattern, rule.replacement)
    } else {
        replacePlain(source, rule.find, rule.replacement, caseSensitive)
    }
}

internal fun replaceVisibleTextInMarkup(
    source: String,
    rule: TextReplaceRule,
    caseSensitive: Boolean
): Pair<String, Int> {
    val builder = StringBuilder()
    var cursor = 0
    var total = 0
    Regex("""<[^>]+>""").findAll(source).forEach { tag ->
        val segment = source.substring(cursor, tag.range.first)
        val replaced = replaceInString(segment, rule, caseSensitive)
        builder.append(replaced.first)
        builder.append(tag.value)
        total += replaced.second
        cursor = tag.range.last + 1
    }
    val tail = source.substring(cursor)
    val replacedTail = replaceInString(tail, rule, caseSensitive)
    builder.append(replacedTail.first)
    total += replacedTail.second
    return if (total == 0) source to 0 else builder.toString() to total
}

internal fun expandRegexReplacement(match: MatchResult, replacement: String): String {
    val builder = StringBuilder()
    var index = 0
    while (index < replacement.length) {
        val char = replacement[index]
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
        if (char == '{' && index + 2 < replacement.length && replacement[index + 1] == 'z') {
            val token = parseRegexReplacementNumberToken(match, replacement, index)
            if (token != null) {
                builder.append(token.first)
                index = token.second
                continue
            }
        }
        builder.append(char)
        index += 1
    }
    return builder.toString()
}

private fun replaceRegex(
    source: String,
    pattern: Regex,
    replacement: String
): Pair<String, Int> {
    val builder = StringBuilder()
    val matches = pattern.findAll(source).iterator()
    var cursor = 0
    var count = 0
    while (matches.hasNext()) {
        val match = matches.next()
        val start = match.range.first
        val end = match.range.last + 1
        if (start < cursor || end < start) continue
        builder.append(source, cursor, start)
        builder.append(expandRegexReplacement(match, replacement))
        cursor = end
        count += 1
    }
    if (count == 0) return source to 0
    builder.append(source, cursor, source.length)
    return builder.toString() to count
}

private fun replacePlain(
    source: String,
    find: String,
    replacement: String,
    caseSensitive: Boolean
): Pair<String, Int> {
    if (find.isEmpty()) return source to 0
    val ranges = plainSearchRanges(source, find, caseSensitive)
    if (ranges.isEmpty()) return source to 0
    val builder = StringBuilder()
    var cursor = 0
    ranges.forEach { (found, end) ->
        builder.append(source, cursor, found)
        builder.append(replacement)
        cursor = end
    }
    builder.append(source, cursor, source.length)
    return builder.toString() to ranges.size
}

internal fun parseReplacementRules(input: String): Pair<List<ParsedReplacementRule>, List<ReplacementSkippedRule>> {
    val lines = input.removePrefix("\uFEFF").split('\n').map { it.removeSuffix("\r") }
    val rules = mutableListOf<ParsedReplacementRule>()
    val skipped = mutableListOf<ReplacementSkippedRule>()
    var ruleCount = 0
    lines.forEachIndexed { index, rawLine ->
        val lineNo = index + 1
        if (rawLine.isBlank()) return@forEachIndexed
        ruleCount += 1
        if (ruleCount > REPLACEMENT_RULE_MAX_COUNT) {
            throw IllegalArgumentException("规则数量过多，最多支持 $REPLACEMENT_RULE_MAX_COUNT 条")
        }
        val separatorIndex = rawLine.indexOf(REPLACEMENT_SEPARATOR)
        val hasSecondSeparator = separatorIndex >= 0 &&
            rawLine.indexOf(REPLACEMENT_SEPARATOR, startIndex = separatorIndex + REPLACEMENT_SEPARATOR.length) >= 0
        if (separatorIndex < 0 || hasSecondSeparator) {
            skipped += ReplacementSkippedRule(
                lineNo = lineNo,
                reason = if (separatorIndex < 0) "缺少 #-># 分隔符" else "包含多个 #-># 分隔符",
                text = rawLine
            )
            return@forEachIndexed
        }

        var pattern = rawLine.substring(0, separatorIndex)
        val replacement = decodeLineBreakEscapes(rawLine.substring(separatorIndex + REPLACEMENT_SEPARATOR.length))
        var regex = true
        if (pattern.startsWith("*")) {
            regex = false
            pattern = pattern.drop(1)
        }
        if (pattern.isEmpty()) {
            skipped += ReplacementSkippedRule(lineNo, "查找内容为空", rawLine)
            return@forEachIndexed
        }
        if (regex) {
            val processed = decodeLineBreakEscapes(pattern)
            val error = runCatching { Regex(processed) }.exceptionOrNull()
            if (error != null) {
                skipped += ReplacementSkippedRule(
                    lineNo = lineNo,
                    reason = "正则错误：${error.message ?: "无法解析"}",
                    text = rawLine
                )
                return@forEachIndexed
            }
            pattern = processed
        } else {
            pattern = decodeLineBreakEscapes(pattern)
        }
        rules += ParsedReplacementRule(
            lineNo = lineNo,
            pattern = pattern,
            replacement = replacement,
            regex = regex
        )
    }
    return rules to skipped
}

private fun parseRegexReplacementNumberToken(
    match: MatchResult,
    replacement: String,
    startIndex: Int
): Pair<String, Int>? {
    var cursor = startIndex + 2
    val widthStart = cursor
    while (cursor < replacement.length && replacement[cursor].isDigit()) {
        cursor += 1
    }
    if (cursor == widthStart || cursor >= replacement.length || replacement[cursor] != ':') return null
    val width = replacement.substring(widthStart, cursor).toIntOrNull()?.coerceIn(0, 32) ?: return null
    cursor += 1
    val groupStart = cursor
    while (cursor < replacement.length && replacement[cursor].isDigit()) {
        cursor += 1
    }
    if (cursor == groupStart || cursor >= replacement.length || replacement[cursor] != '}') return null
    val groupIndex = replacement.substring(groupStart, cursor).toIntOrNull() ?: return null
    val groupValue = if (groupIndex < match.groups.size) {
        match.groups[groupIndex]?.value.orEmpty()
    } else {
        ""
    }
    val number = ChapterDetector.parseNumber(groupValue) ?: return "" to (cursor + 1)
    val rendered = number.toString().let { value ->
        if (width > 0) value.padStart(width, '0') else value
    }
    return rendered to (cursor + 1)
}
