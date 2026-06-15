package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.TxtChapterPatternRule

internal data class TxtChapterRuleEditState(
    val rulesText: String,
    val enabledKeys: Set<String>
)

internal fun applyTxtChapterRuleEditState(
    currentEnabledKeys: Set<String>,
    result: TxtChapterRuleEditResult,
    updateEnabledKeys: Boolean = true
): TxtChapterRuleEditState? {
    if (!result.success) return null
    return TxtChapterRuleEditState(
        rulesText = result.rulesText,
        enabledKeys = if (updateEnabledKeys) result.enabledKeys else currentEnabledKeys
    )
}

internal fun txtChapterRuleKey(item: TxtChapterRuleItem): String {
    return "${item.name.trim()}\u0000${item.pattern.trim()}\u0000${item.replacement.trim()}"
}

internal fun activeTxtChapterPatternRules(
    config: TxtChapterDetectionConfig,
    enabledKeys: Set<String>
): List<TxtChapterPatternRule> {
    return parseTxtChapterRuleItems(config.rulesText)
        .filter { item -> txtChapterRuleKey(item) in enabledKeys && item.pattern.isNotBlank() }
        .map { item ->
            TxtChapterPatternRule(
                pattern = item.pattern.trim(),
                replacement = item.replacement.trim()
            )
        }
}

internal fun effectiveTxtChapterRuleItems(
    rulesText: String,
    enabledKeys: Set<String>
): List<TxtChapterRuleItem> {
    return parseTxtChapterRuleItems(rulesText).map { item ->
        item.copy(enabled = txtChapterRuleKey(item) in enabledKeys)
    }
}

internal fun addTxtChapterRuleModel(
    rulesText: String,
    enabledKeys: Set<String>,
    name: String,
    pattern: String,
    replacement: String
): TxtChapterRuleEditResult {
    val items = parseTxtChapterRuleItems(rulesText).toMutableList()
    val added = TxtChapterRuleItem(
        index = items.size,
        name = name.trim(),
        pattern = pattern.trim(),
        replacement = replacement.trim(),
        enabled = true
    )
    items += added
    return TxtChapterRuleEditResult(
        success = true,
        rulesText = serializeTxtChapterRuleItems(items),
        enabledKeys = enabledKeys + txtChapterRuleKey(added)
    )
}

internal fun renameTxtChapterRuleModel(
    rulesText: String,
    enabledKeys: Set<String>,
    index: Int,
    name: String
): TxtChapterRuleEditResult {
    val items = parseTxtChapterRuleItems(rulesText).toMutableList()
    val current = items.getOrNull(index) ?: return TxtChapterRuleEditResult(success = false)
    val next = current.copy(name = name)
    items[index] = next
    return TxtChapterRuleEditResult(
        success = true,
        rulesText = serializeTxtChapterRuleItems(items),
        enabledKeys = replaceTxtChapterRuleEnabledKey(enabledKeys, current, next)
    )
}

internal fun updateTxtChapterRulePatternModel(
    rulesText: String,
    enabledKeys: Set<String>,
    index: Int,
    pattern: String
): TxtChapterRuleEditResult {
    val items = parseTxtChapterRuleItems(rulesText).toMutableList()
    val current = items.getOrNull(index) ?: return TxtChapterRuleEditResult(success = false)
    val next = current.copy(pattern = pattern.trim())
    items[index] = next
    return TxtChapterRuleEditResult(
        success = true,
        rulesText = serializeTxtChapterRuleItems(items),
        enabledKeys = replaceTxtChapterRuleEnabledKey(enabledKeys, current, next)
    )
}

internal fun updateTxtChapterRuleItemModel(
    rulesText: String,
    enabledKeys: Set<String>,
    index: Int,
    name: String,
    pattern: String,
    replacement: String
): TxtChapterRuleEditResult {
    val items = parseTxtChapterRuleItems(rulesText).toMutableList()
    val current = items.getOrNull(index) ?: return TxtChapterRuleEditResult(success = false)
    val next = current.copy(name = name, pattern = pattern.trim(), replacement = replacement.trim())
    items[index] = next
    return TxtChapterRuleEditResult(
        success = true,
        rulesText = serializeTxtChapterRuleItems(items),
        enabledKeys = replaceTxtChapterRuleEnabledKey(enabledKeys, current, next)
    )
}

internal fun updateTxtChapterRuleEnabledKeys(
    rulesText: String,
    enabledKeys: Set<String>,
    index: Int,
    enabled: Boolean
): TxtChapterRuleEditResult {
    val item = parseTxtChapterRuleItems(rulesText).getOrNull(index)
        ?: return TxtChapterRuleEditResult(success = false)
    val key = txtChapterRuleKey(item)
    return TxtChapterRuleEditResult(
        success = true,
        rulesText = rulesText,
        enabledKeys = if (enabled) enabledKeys + key else enabledKeys - key
    )
}

internal fun deleteTxtChapterRuleModel(
    rulesText: String,
    enabledKeys: Set<String>,
    index: Int
): TxtChapterRuleEditResult {
    val items = parseTxtChapterRuleItems(rulesText).toMutableList()
    val current = items.getOrNull(index) ?: return TxtChapterRuleEditResult(success = false)
    items.removeAt(index)
    return TxtChapterRuleEditResult(
        success = true,
        rulesText = serializeTxtChapterRuleItems(items),
        enabledKeys = enabledKeys - txtChapterRuleKey(current)
    )
}

internal fun moveTxtChapterRuleModel(
    rulesText: String,
    fromIndex: Int,
    toIndex: Int
): TxtChapterRuleEditResult {
    val items = parseTxtChapterRuleItems(rulesText).toMutableList()
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) {
        return TxtChapterRuleEditResult(success = false)
    }
    val item = items.removeAt(fromIndex)
    items.add(toIndex, item)
    return TxtChapterRuleEditResult(
        success = true,
        rulesText = serializeTxtChapterRuleItems(items)
    )
}

private fun replaceTxtChapterRuleEnabledKey(
    enabledKeys: Set<String>,
    current: TxtChapterRuleItem,
    next: TxtChapterRuleItem
): Set<String> {
    val currentKey = txtChapterRuleKey(current)
    val nextKey = txtChapterRuleKey(next)
    return if (currentKey in enabledKeys) {
        (enabledKeys - currentKey) + nextKey
    } else {
        enabledKeys
    }
}

internal fun parseTxtChapterRuleItems(text: String): List<TxtChapterRuleItem> {
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
            val parts = body.split('\t', limit = 3)
            val fallbackName = "规则 ${size + 1}"
            val name = if (parts.size >= 2) {
                parts[0].trim()
            } else {
                fallbackName
            }
            val pattern = if (parts.size >= 2) parts[1].trim() else body.trim()
            val replacement = if (parts.size >= 3) parts[2].trim() else ""
            add(
                TxtChapterRuleItem(
                    index = size,
                    name = name,
                    pattern = pattern,
                    replacement = replacement,
                    enabled = !disabled
                )
            )
        }
    }
}

internal fun serializeTxtChapterRuleItems(items: List<TxtChapterRuleItem>): String {
    return items.joinToString("\n") { item ->
        val name = item.name
            .replace('\t', ' ')
            .replace('\n', ' ')
            .trim()
        val pattern = item.pattern
            .replace('\t', ' ')
            .replace('\n', ' ')
            .trim()
        val replacement = item.replacement
            .replace('\t', ' ')
            .replace('\n', ' ')
            .trim()
        "$name\t$pattern\t$replacement"
    }
}

internal fun countTxtChapterRuleMatches(text: String, pattern: String): Int {
    if (pattern.isBlank()) return 0
    return runCatching {
        val regex = Regex(pattern)
        text.lineSequence().count { line ->
            val value = ChapterDetector.normalizeTxtCatalogMatchText(line)
            value.length in 2..90 && regex.find(value)?.range?.first == 0
        }
    }.getOrDefault(0)
}

internal fun txtChapterRuleHasMatch(text: String, pattern: String): Boolean {
    if (pattern.isBlank()) return false
    return runCatching {
        val regex = Regex(pattern)
        text.lineSequence().any { line ->
            val value = ChapterDetector.normalizeTxtCatalogMatchText(line)
            value.length in 2..90 && regex.find(value)?.range?.first == 0
        }
    }.getOrDefault(false)
}
