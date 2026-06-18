package com.eteditor

import org.json.JSONArray

private data class FetchInfoReplacementRule(
    val lineNo: Int,
    val name: String,
    val search: String,
    val replacement: String,
    val regex: Boolean,
    val action: String = FETCH_CATALOG_RULE_ACTION_REPLACE,
    val category: String = FETCH_CATALOG_RULE_CATEGORY_PURIFY,
    val enabled: Boolean = true
)

object FetchInfoFilter {
    fun apply(raw: FetchedInfo, parameters: FetchInfoParameters): Pair<FetchedInfo, List<FetchInfoFilterIssue>> {
        val issues = mutableListOf<FetchInfoFilterIssue>()
        val catalog = applyCatalogFilters(raw.catalog, parameters.catalogFilter, issues)
        val intro = applyIntroFilters(raw.intro, parameters.introFilter, issues)
        return raw.copy(
            catalog = catalog.mapIndexed { index, item -> item.copy(index = index + 1) },
            intro = intro
        ) to issues
    }

    private fun applyCatalogFilters(
        input: List<FetchedCatalogItem>,
        filterText: String,
        issues: MutableList<FetchInfoFilterIssue>
    ): List<FetchedCatalogItem> {
        var items = input
        parseStructuredReplacementFilters(filterText, issues)?.let { rules ->
            val ordered = rules
                .filter { it.enabled }
                .sortedBy { if (it.category == FETCH_CATALOG_RULE_CATEGORY_CHAPTER) 0 else 1 } // 稳定排序：章节在前、净化在后
            ordered.forEach { rule ->
                items = when {
                    rule.action == FETCH_CATALOG_RULE_ACTION_DROP -> {
                        if (rule.regex) {
                            val regex = parseFilterRegex(rule.search, rule.lineNo, rule.name, issues) ?: return@forEach
                            items.filterNot { regex.containsMatchIn(it.title) }
                        } else {
                            items.filterNot { it.title.contains(rule.search) }
                        }
                    }
                    rule.regex -> {
                        val regex = parseFilterRegex(rule.search, rule.lineNo, rule.name, issues) ?: return@forEach
                        items.map { item -> item.copy(title = regexReplace(item.title, regex, rule.replacement)) }
                    }
                    else -> items.map { item -> item.copy(title = item.title.replace(rule.search, rule.replacement)) }
                }
            }
            return items
        }
        filterText.lines().forEachIndexed { index, rawLine ->
            val lineNo = index + 1
            val line = rawLine.trim()
            if (line.isBlank()) return@forEachIndexed
            when {
                line.startsWith("dropContains:", ignoreCase = true) -> {
                    val token = line.substringAfter(':')
                    if (token.isBlank()) {
                        issues += FetchInfoFilterIssue(lineNo, "dropContains 内容为空", rawLine)
                    } else {
                        items = items.filterNot { it.title.contains(token) }
                    }
                }
                line.startsWith("dropRegex:", ignoreCase = true) -> {
                    val regex = parseFilterRegex(line.substringAfter(':'), lineNo, rawLine, issues) ?: return@forEachIndexed
                    items = items.filterNot { regex.containsMatchIn(it.title) }
                }
                line.startsWith("replaceRegex:", ignoreCase = true) -> {
                    val rule = parseReplacementFilter(line.substringAfter(':'), lineNo, rawLine, issues) ?: return@forEachIndexed
                    val regex = parseFilterRegex(rule.first, lineNo, rawLine, issues) ?: return@forEachIndexed
                    items = items.map { item -> item.copy(title = regexReplace(item.title, regex, rule.second)) }
                }
                line.startsWith("replace:", ignoreCase = true) -> {
                    val rule = parseReplacementFilter(line.substringAfter(':'), lineNo, rawLine, issues) ?: return@forEachIndexed
                    items = items.map { item -> item.copy(title = item.title.replace(rule.first, rule.second)) }
                }
                else -> issues += FetchInfoFilterIssue(lineNo, "目录过滤规则不支持", rawLine)
            }
        }
        return items
    }

    private fun applyIntroFilters(
        input: String,
        filterText: String,
        issues: MutableList<FetchInfoFilterIssue>
    ): String {
        var text = input
        parseStructuredReplacementFilters(filterText, issues)?.let { rules ->
            rules.filter { it.enabled }.forEach { rule ->
                text = if (rule.regex) {
                    val regex = parseFilterRegex(rule.search, rule.lineNo, rule.name, issues) ?: return@forEach
                    regexReplace(text, regex, rule.replacement)
                } else {
                    text.replace(rule.search, rule.replacement)
                }
            }
            return text
        }
        filterText.lines().forEachIndexed { index, rawLine ->
            val lineNo = index + 1
            val line = rawLine.trim()
            if (line.isBlank()) return@forEachIndexed
            when {
                line.equals("trim", ignoreCase = true) -> text = text.trim()
                line.equals("compressBlankLines", ignoreCase = true) -> {
                    text = text.replace(Regex("""[ \t]*\r?\n[ \t]*\r?\n+"""), "\n\n")
                }
                line.startsWith("truncateBefore:", ignoreCase = true) -> {
                    val token = line.substringAfter(':')
                    if (token.isBlank()) {
                        issues += FetchInfoFilterIssue(lineNo, "truncateBefore 内容为空", rawLine)
                    } else {
                        val found = text.indexOf(token)
                        if (found >= 0) text = text.substring(found + token.length)
                    }
                }
                line.startsWith("replaceRegex:", ignoreCase = true) -> {
                    val rule = parseReplacementFilter(line.substringAfter(':'), lineNo, rawLine, issues) ?: return@forEachIndexed
                    val regex = parseFilterRegex(rule.first, lineNo, rawLine, issues) ?: return@forEachIndexed
                    text = regexReplace(text, regex, rule.second)
                }
                line.startsWith("replace:", ignoreCase = true) -> {
                    val rule = parseReplacementFilter(line.substringAfter(':'), lineNo, rawLine, issues) ?: return@forEachIndexed
                    text = text.replace(rule.first, rule.second)
                }
                else -> issues += FetchInfoFilterIssue(lineNo, "简介过滤规则不支持", rawLine)
            }
        }
        return text
    }

    private fun parseReplacementFilter(
        input: String,
        lineNo: Int,
        rawLine: String,
        issues: MutableList<FetchInfoFilterIssue>
    ): Pair<String, String>? {
        val separator = input.indexOf("=>")
        if (separator < 0) {
            issues += FetchInfoFilterIssue(lineNo, "替换规则缺少 =>", rawLine)
            return null
        }
        val find = input.substring(0, separator)
        if (find.isBlank()) {
            issues += FetchInfoFilterIssue(lineNo, "查找内容为空", rawLine)
            return null
        }
        return decodeLineBreakEscapes(find) to decodeLineBreakEscapes(input.substring(separator + 2))
    }

    private fun parseStructuredReplacementFilters(
        filterText: String,
        issues: MutableList<FetchInfoFilterIssue>
    ): List<FetchInfoReplacementRule>? {
        val text = filterText.trim()
        if (!text.startsWith("[")) return null
        return try {
            val array = JSONArray(text)
            val rules = mutableListOf<FetchInfoReplacementRule>()
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                if (json == null) {
                    issues += FetchInfoFilterIssue(index + 1, "过滤规则格式错误", "")
                    continue
                }
                val name = json.optString("name").ifBlank { "规则${index + 1}" }
                val search = json.optString("search")
                if (search.isBlank()) {
                    issues += FetchInfoFilterIssue(index + 1, "查找内容为空", name)
                    continue
                }
                rules += FetchInfoReplacementRule(
                    lineNo = index + 1,
                    name = name,
                    search = decodeLineBreakEscapes(search),
                    replacement = decodeLineBreakEscapes(json.optString("replacement")),
                    regex = json.optBoolean("regex", false),
                    action = normalizeFetchCatalogRuleAction(json.optString("action")),
                    category = normalizeFetchCatalogRuleCategory(json.optString("category")),
                    enabled = json.optBoolean("enabled", true)
                )
            }
            rules
        } catch (error: Throwable) {
            issues += FetchInfoFilterIssue(1, "过滤规则格式错误：${error.message ?: "无法解析"}", filterText)
            emptyList()
        }
    }

    private fun parseFilterRegex(
        input: String,
        lineNo: Int,
        rawLine: String,
        issues: MutableList<FetchInfoFilterIssue>
    ): Regex? {
        val pattern = decodeLineBreakEscapes(input)
        if (pattern.isBlank()) {
            issues += FetchInfoFilterIssue(lineNo, "正则为空", rawLine)
            return null
        }
        return try {
            Regex(pattern)
        } catch (error: Throwable) {
            issues += FetchInfoFilterIssue(lineNo, "正则错误：${error.message ?: "无法解析"}", rawLine)
            null
        }
    }

    private fun regexReplace(source: String, regex: Regex, replacement: String): String {
        return regex.replace(source) { match -> expandRegexReplacement(match, replacement) }
    }
}
