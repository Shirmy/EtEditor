package com.eteditor

import com.eteditor.core.TxtDocument

internal data class TxtPurifyRuleEditState(
    val rulesText: String,
    val changedRule: TxtPurifyRuleItem?
)

internal fun applyTxtPurifyRuleEditState(result: TxtPurifyRuleEditResult): TxtPurifyRuleEditState? {
    if (!result.success) return null
    return TxtPurifyRuleEditState(
        rulesText = result.rulesText,
        changedRule = result.changedRule
    )
}

internal const val TXT_PURIFY_TARGET_BODY = "body"
internal const val TXT_PURIFY_TARGET_CATALOG = "catalog"

internal fun parseTxtPurifyRuleItems(text: String): List<TxtPurifyRuleItem> {
    return buildList {
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
            val left = body.substring(0, separator)
            val parts = left.split('\t')
            val target = when {
                parts.size >= 2 -> normalizeTxtPurifyTarget(parts[0])
                else -> TXT_PURIFY_TARGET_BODY
            }
            val name = decodeTxtRuleArrowSeparator(
                when {
                    parts.size >= 4 -> parts[1].trim()
                    parts.size == 3 -> parts[1].trim()
                    else -> ""
                }
            )
            val regex = when {
                parts.size >= 4 -> parts[2].trim().toBooleanStrictOrNull() ?: true
                else -> true
            }
            val pattern = decodeTxtRuleArrowSeparator(
                when {
                    parts.size >= 4 -> parts.drop(3).joinToString("\t")
                    parts.size == 3 -> parts[2]
                    parts.size == 2 -> parts[1]
                    else -> left
                }.trim()
            )
            add(
                TxtPurifyRuleItem(
                    index = size,
                    target = target,
                    name = name,
                    pattern = pattern,
                    replacement = body.substring(separator + 2),
                    regex = regex,
                    enabled = !disabled
                )
            )
        }
    }
}

internal fun serializeTxtPurifyRuleItems(items: List<TxtPurifyRuleItem>): String {
    return items.joinToString("\n") { item ->
        val target = normalizeTxtPurifyTarget(item.target)
        val name = encodeTxtRuleArrowSeparator(
            item.name
                .replace('\t', ' ')
                .replace('\n', ' ')
                .trim()
        )
        val pattern = encodeTxtRuleArrowSeparator(
            item.pattern
                .replace('\t', ' ')
                .replace('\n', ' ')
                .trim()
        )
        val replacement = item.replacement
            .replace('\t', ' ')
            .replace('\n', ' ')
        val line = "$target\t$name\t${item.regex}\t$pattern=>$replacement"
        if (item.enabled) line else "# $line"
    }
}

internal fun addTxtPurifyRuleModel(
    rulesText: String,
    target: String,
    name: String,
    regex: Boolean,
    pattern: String,
    replacement: String
): TxtPurifyRuleEditResult {
    val items = parseTxtPurifyRuleItems(rulesText).toMutableList()
    val added = TxtPurifyRuleItem(
        index = items.size,
        target = normalizeTxtPurifyTarget(target),
        name = name,
        pattern = pattern.trim(),
        replacement = replacement,
        regex = regex,
        enabled = true
    )
    items += added
    return TxtPurifyRuleEditResult(
        success = true,
        rulesText = serializeTxtPurifyRuleItems(items),
        changedRule = added
    )
}

internal fun updateTxtPurifyRuleTargetModel(
    rulesText: String,
    index: Int,
    target: String
): TxtPurifyRuleEditResult {
    return updateTxtPurifyRuleModelAt(rulesText, index) { current ->
        current.copy(target = normalizeTxtPurifyTarget(target))
    }
}

internal fun updateTxtPurifyRuleNameModel(
    rulesText: String,
    index: Int,
    name: String
): TxtPurifyRuleEditResult {
    return updateTxtPurifyRuleModelAt(rulesText, index) { current ->
        current.copy(name = name)
    }
}

internal fun updateTxtPurifyRulePatternModel(
    rulesText: String,
    index: Int,
    pattern: String
): TxtPurifyRuleEditResult {
    return updateTxtPurifyRuleModelAt(rulesText, index) { current ->
        current.copy(pattern = pattern.trim())
    }
}

internal fun updateTxtPurifyRuleReplacementModel(
    rulesText: String,
    index: Int,
    replacement: String
): TxtPurifyRuleEditResult {
    return updateTxtPurifyRuleModelAt(rulesText, index) { current ->
        current.copy(replacement = replacement)
    }
}

internal fun updateTxtPurifyRuleRegexModel(
    rulesText: String,
    index: Int,
    regex: Boolean
): TxtPurifyRuleEditResult {
    return updateTxtPurifyRuleModelAt(rulesText, index) { current ->
        current.copy(regex = regex)
    }
}

internal fun updateTxtPurifyRuleEnabledModel(
    rulesText: String,
    index: Int,
    enabled: Boolean
): TxtPurifyRuleEditResult {
    return updateTxtPurifyRuleModelAt(rulesText, index) { current ->
        current.copy(enabled = enabled)
    }
}

internal fun updateTxtPurifyRuleModel(
    rulesText: String,
    index: Int,
    target: String,
    name: String,
    regex: Boolean,
    pattern: String,
    replacement: String
): TxtPurifyRuleEditResult {
    return updateTxtPurifyRuleModelAt(rulesText, index) { current ->
        current.copy(
            target = normalizeTxtPurifyTarget(target),
            name = name,
            regex = regex,
            pattern = pattern.trim(),
            replacement = replacement
        )
    }
}

internal fun deleteTxtPurifyRuleModel(
    rulesText: String,
    index: Int
): TxtPurifyRuleEditResult {
    val items = parseTxtPurifyRuleItems(rulesText).toMutableList()
    if (index !in items.indices) return TxtPurifyRuleEditResult(success = false)
    items.removeAt(index)
    return TxtPurifyRuleEditResult(success = true, rulesText = serializeTxtPurifyRuleItems(items))
}

internal fun moveTxtPurifyRuleModel(
    rulesText: String,
    fromIndex: Int,
    toIndex: Int
): TxtPurifyRuleEditResult {
    val items = parseTxtPurifyRuleItems(rulesText).toMutableList()
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) {
        return TxtPurifyRuleEditResult(success = false)
    }
    val item = items.removeAt(fromIndex)
    items.add(toIndex, item)
    return TxtPurifyRuleEditResult(success = true, rulesText = serializeTxtPurifyRuleItems(items))
}

private fun updateTxtPurifyRuleModelAt(
    rulesText: String,
    index: Int,
    transform: (TxtPurifyRuleItem) -> TxtPurifyRuleItem
): TxtPurifyRuleEditResult {
    val items = parseTxtPurifyRuleItems(rulesText).toMutableList()
    val current = items.getOrNull(index) ?: return TxtPurifyRuleEditResult(success = false)
    val next = transform(current)
    items[index] = next
    return TxtPurifyRuleEditResult(
        success = true,
        rulesText = serializeTxtPurifyRuleItems(items),
        changedRule = next
    )
}

internal fun normalizeTxtPurifyTarget(target: String): String {
    return when (target.trim().lowercase()) {
        TXT_PURIFY_TARGET_CATALOG, "title", "titles", "toc", "目录", "标题" -> TXT_PURIFY_TARGET_CATALOG
        else -> TXT_PURIFY_TARGET_BODY
    }
}

internal fun countTxtPurifyRuleMatches(document: TxtDocument?, item: TxtPurifyRuleItem): Int {
    val source = document ?: return 0
    if (item.pattern.isBlank()) return 0
    val target = normalizeTxtPurifyTarget(item.target)
    if (txtPurifyRegexCostWarningForDocument(
            document = source,
            rules = listOf(item.copy(enabled = true, target = target)),
            applyBody = target == TXT_PURIFY_TARGET_BODY,
            applyCatalog = target == TXT_PURIFY_TARGET_CATALOG,
            requireEnabled = false
        ) != null
    ) {
        return 0
    }
    val regex = runCatching {
        val pattern = if (item.regex) item.pattern else Regex.escape(item.pattern)
        Regex(pattern, setOf(RegexOption.MULTILINE))
    }.getOrNull() ?: return 0
    return when (target) {
        TXT_PURIFY_TARGET_CATALOG -> source.chapters.sumOf { chapter -> regex.findAll(chapter.title).count() }
        else -> txtBodyRanges(source.text, source.chapters).sumOf { (start, end) ->
            regex.findAll(source.text.substring(start, end)).count()
        }
    }
}

internal fun enabledTxtPurifyRules(text: String): List<TxtPurifyRuleItem> {
    return parseTxtPurifyRuleItems(text)
        .filter { it.enabled && it.pattern.isNotBlank() }
}

internal fun autoSelectedTxtPurifyRuleItemsAfterOpen(
    document: TxtDocument,
    rulesText: String
): List<TxtPurifyRuleItem>? {
    val items = parseTxtPurifyRuleItems(rulesText)
    if (items.isEmpty()) return null
    var changed = false
    val nextItems = items.map { item ->
        val nextEnabled = item.pattern.isNotBlank() && countTxtPurifyRuleMatches(document, item) > 0
        if (item.enabled != nextEnabled) changed = true
        item.copy(enabled = nextEnabled)
    }
    return nextItems.takeIf { changed }
}

internal fun autoSelectTxtPurifyRulesAfterOpenModel(
    document: TxtDocument,
    rulesText: String
): TxtPurifyRuleEditResult {
    val nextItems = autoSelectedTxtPurifyRuleItemsAfterOpen(document, rulesText)
        ?: return TxtPurifyRuleEditResult(success = false)
    return TxtPurifyRuleEditResult(
        success = true,
        rulesText = serializeTxtPurifyRuleItems(nextItems)
    )
}
