package com.eteditor

internal fun applyTxtBookTitleRuleEditText(result: TxtBookTitleRuleEditResult): String? {
    if (!result.success) return null
    return result.rulesText
}

internal fun resolveTxtBookTitleFilterWithRules(
    sourceTitle: String,
    rules: List<TxtBookTitleRuleItem>,
    onRegexError: (Throwable, String) -> Unit = { _, _ -> }
): TxtBookTitleFilterResult {
    val rawTitle = sourceTitle.trim()
    if (rawTitle.isBlank()) return TxtBookTitleFilterResult(sourceTitle, sourceTitle)
    rules.forEach { item ->
        val filteredTitle = txtBookTitleRuleReplacement(rawTitle, item, onRegexError)
        if (filteredTitle != null) {
            return TxtBookTitleFilterResult(
                sourceTitle = rawTitle,
                filteredTitle = cleanTxtBookTitleForDisplay(filteredTitle),
                ruleIndex = item.index
            )
        }
    }
    return TxtBookTitleFilterResult(sourceTitle, cleanTxtBookTitleForDisplay(rawTitle))
}

internal fun resolveTxtBookTitleFilterWithRules(
    sourceTitles: List<String>,
    rules: List<TxtBookTitleRuleItem>,
    onRegexError: (Throwable, String) -> Unit = { _, _ -> }
): TxtBookTitleFilterResult {
    val candidates = sourceTitles.ifEmpty { listOf("") }
    candidates.forEach { sourceTitle ->
        val rawTitle = sourceTitle.trim()
        if (rawTitle.isBlank()) return@forEach
        rules.forEach { item ->
            val filteredTitle = txtBookTitleRuleReplacement(rawTitle, item, onRegexError)
            if (filteredTitle != null) {
                return TxtBookTitleFilterResult(
                    sourceTitle = rawTitle,
                    filteredTitle = cleanTxtBookTitleForDisplay(filteredTitle),
                    ruleIndex = item.index
                )
            }
        }
    }
    return TxtBookTitleFilterResult(candidates.first(), cleanTxtBookTitleForDisplay(candidates.first()))
}

internal fun buildTxtBookTitleFilterSources(
    originalName: String?,
    sourceDisplayName: String?,
    fallbackTitle: String
): List<String> {
    val sources = mutableListOf<String>()
    fun addCandidate(value: String?) {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return
        sources += trimmed
        if (trimmed.endsWith(".txt", ignoreCase = true)) {
            sources += trimmed.dropLast(4).trim()
        } else {
            sources += "$trimmed.txt"
        }
    }
    addCandidate(originalName)
    addCandidate(sourceDisplayName)
    if (sources.isEmpty()) {
        addCandidate(fallbackTitle)
    }
    return sources
        .map { candidate -> candidate.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .ifEmpty { listOf(originalName.txtRuleBaseName("TXT")).filter { it.isNotBlank() } }
}

internal fun enabledTxtBookTitleRules(text: String): List<TxtBookTitleRuleItem> {
    return parseTxtBookTitleRuleItems(text)
        .filter { it.pattern.isNotBlank() && it.replacement.isNotBlank() }
}

internal fun cleanTxtBookTitleForDisplay(value: String): String {
    val trimmed = value.trim()
    return if (trimmed.endsWith(".txt", ignoreCase = true)) trimmed.dropLast(4).trim() else trimmed
}

internal fun updateTxtBookTitleModel(currentTitle: String, value: String): TxtBookTitleUpdateResult {
    val next = cleanTxtBookTitleForDisplay(value)
    if (next.isBlank()) {
        return TxtBookTitleUpdateResult(success = false, message = "书名不能为空")
    }
    if (next == currentTitle) {
        return TxtBookTitleUpdateResult(success = false, message = "书名无需修改")
    }
    return TxtBookTitleUpdateResult(
        success = true,
        title = next,
        message = "书名已修改：$next"
    )
}

private fun txtBookTitleRuleReplacement(
    sourceTitle: String,
    item: TxtBookTitleRuleItem,
    onRegexError: (Throwable, String) -> Unit
): String? {
    val rawTitle = sourceTitle.trim()
    val pattern = item.pattern.trim()
    val replacement = item.replacement.trim()
    if (rawTitle.isBlank() || pattern.isBlank() || replacement.isBlank()) return null
    if (item.regex) {
        val regex = try {
            Regex(pattern)
        } catch (error: IllegalArgumentException) {
            onRegexError(error, pattern)
            return null
        }
        val match = regex.find(rawTitle)
            ?: txtBookTitleLenientRegexMatch(rawTitle, pattern, replacement)
            ?: return replacement.takeIf { txtBookTitlePlainMatches(rawTitle, pattern) }
        return expandRegexReplacement(match, replacement).trim().ifBlank { rawTitle }
    }
    return if (txtBookTitlePlainMatches(rawTitle, pattern)) replacement else null
}

private fun txtBookTitleLenientRegexMatch(
    sourceTitle: String,
    pattern: String,
    replacement: String
): MatchResult? {
    val normalizedPattern = if (Regex("""\${'$'}\d+""").containsMatchIn(replacement)) {
        pattern.replace('（', '(').replace('）', ')')
    } else {
        pattern
    }.replace(Regex("""\s+"""), "")
    val compactSource = sourceTitle.replace(Regex("""\s+"""), "")
    return runCatching { Regex(normalizedPattern) }.getOrNull()?.find(compactSource)
}

private fun txtBookTitlePlainMatches(sourceTitle: String, pattern: String): Boolean {
    val source = sourceTitle.trim()
    val rule = pattern.trim()
    if (source == rule) return true
    return cleanTxtBookTitleForDisplay(source) == cleanTxtBookTitleForDisplay(rule)
}

private fun String?.txtRuleBaseName(fallback: String): String {
    return orEmpty().substringAfterLast('/').substringBeforeLast('.').ifBlank { fallback }
}

internal fun parseTxtBookTitleRuleItems(text: String): List<TxtBookTitleRuleItem> {
    val parsed = buildList {
        text.lineSequence().forEach { rawLine ->
            if (rawLine.isBlank()) return@forEach
            val withoutIndent = rawLine.trimStart()
            val disabled = isLegacyDisabledRuleLine(withoutIndent)
            val body = if (disabled) {
                stripLegacyDisabledRulePrefix(withoutIndent)
            } else {
                rawLine
            }
            if (body.isBlank()) return@forEach
            val separator = body.indexOf("=>")
            if (separator < 0) return@forEach
            val parts = body.substring(0, separator).split('\t')
            val firstPart = parts.getOrNull(0)?.trim().orEmpty()
            val oldRegexFlag = firstPart.toBooleanStrictOrNull()
            val pattern = when {
                parts.size >= 3 -> parts.drop(2).joinToString("\t").trim()
                parts.size == 2 -> parts[1].trim()
                else -> firstPart
            }
            val name = when {
                parts.size >= 3 -> firstPart
                parts.size == 2 && oldRegexFlag != null -> ""
                parts.size >= 2 -> firstPart
                else -> ""
            }
            val regex = when {
                parts.size >= 3 -> parts.getOrNull(1)?.trim()?.toBooleanStrictOrNull() ?: true
                parts.size == 2 && oldRegexFlag != null -> oldRegexFlag
                else -> true
            }
            add(
                TxtBookTitleRuleItem(
                    index = size,
                    name = name,
                    pattern = pattern,
                    replacement = body.substring(separator + 2).trim(),
                    regex = regex,
                    enabled = !disabled
                )
            )
        }
    }
    return parsed.mapIndexed { index, item ->
        item.copy(index = index, regex = true, enabled = true)
    }
}

internal fun serializeTxtBookTitleRuleItems(items: List<TxtBookTitleRuleItem>): String {
    return items.joinToString("\n") { item ->
        val pattern = item.pattern
            .replace('\t', ' ')
            .replace('\n', ' ')
            .trim()
        val name = item.name
            .replace('\t', ' ')
            .replace('\n', ' ')
            .trim()
        val replacement = item.replacement
            .replace('\t', ' ')
            .replace('\n', ' ')
            .trim()
        "$name\t${item.regex}\t$pattern=>$replacement"
    }
}

internal fun addTxtBookTitleRuleModel(
    rulesText: String,
    name: String,
    pattern: String,
    replacement: String
): TxtBookTitleRuleEditResult {
    val items = parseTxtBookTitleRuleItems(rulesText).toMutableList()
    items += TxtBookTitleRuleItem(
        index = items.size,
        name = name.trim(),
        pattern = pattern.trim(),
        replacement = replacement,
        regex = true,
        enabled = true
    )
    return TxtBookTitleRuleEditResult(
        success = true,
        rulesText = serializeTxtBookTitleRuleItems(items)
    )
}

internal fun updateTxtBookTitleRuleNameModel(
    rulesText: String,
    index: Int,
    name: String
): TxtBookTitleRuleEditResult {
    return updateTxtBookTitleRuleModelAt(rulesText, index) { current ->
        current.copy(name = name)
    }
}

internal fun updateTxtBookTitleRulePatternModel(
    rulesText: String,
    index: Int,
    pattern: String
): TxtBookTitleRuleEditResult {
    return updateTxtBookTitleRuleModelAt(rulesText, index) { current ->
        current.copy(pattern = pattern.trim(), regex = true)
    }
}

internal fun updateTxtBookTitleRuleReplacementModel(
    rulesText: String,
    index: Int,
    replacement: String
): TxtBookTitleRuleEditResult {
    return updateTxtBookTitleRuleModelAt(rulesText, index) { current ->
        current.copy(replacement = replacement)
    }
}

internal fun updateTxtBookTitleRuleRegexModel(
    rulesText: String,
    index: Int
): TxtBookTitleRuleEditResult {
    return updateTxtBookTitleRuleModelAt(rulesText, index) { current ->
        current.copy(regex = true)
    }
}

internal fun updateTxtBookTitleRuleModel(
    rulesText: String,
    index: Int,
    name: String,
    pattern: String,
    replacement: String
): TxtBookTitleRuleEditResult {
    return updateTxtBookTitleRuleModelAt(rulesText, index) { current ->
        current.copy(
            name = name,
            regex = true,
            pattern = pattern.trim(),
            replacement = replacement
        )
    }
}

internal fun deleteTxtBookTitleRuleModel(
    rulesText: String,
    index: Int
): TxtBookTitleRuleEditResult {
    val items = parseTxtBookTitleRuleItems(rulesText).toMutableList()
    if (index !in items.indices) return TxtBookTitleRuleEditResult(success = false)
    items.removeAt(index)
    return TxtBookTitleRuleEditResult(
        success = true,
        rulesText = serializeTxtBookTitleRuleItems(items)
    )
}

internal fun moveTxtBookTitleRuleModel(
    rulesText: String,
    fromIndex: Int,
    toIndex: Int
): TxtBookTitleRuleEditResult {
    val items = parseTxtBookTitleRuleItems(rulesText).toMutableList()
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) {
        return TxtBookTitleRuleEditResult(success = false)
    }
    val item = items.removeAt(fromIndex)
    items.add(toIndex, item)
    return TxtBookTitleRuleEditResult(
        success = true,
        rulesText = serializeTxtBookTitleRuleItems(items)
    )
}

private fun updateTxtBookTitleRuleModelAt(
    rulesText: String,
    index: Int,
    transform: (TxtBookTitleRuleItem) -> TxtBookTitleRuleItem
): TxtBookTitleRuleEditResult {
    val items = parseTxtBookTitleRuleItems(rulesText).toMutableList()
    val current = items.getOrNull(index) ?: return TxtBookTitleRuleEditResult(success = false)
    items[index] = transform(current)
    return TxtBookTitleRuleEditResult(
        success = true,
        rulesText = serializeTxtBookTitleRuleItems(items)
    )
}
