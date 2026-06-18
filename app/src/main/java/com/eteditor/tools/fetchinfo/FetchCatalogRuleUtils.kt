package com.eteditor

import org.json.JSONArray
import org.json.JSONObject

const val FETCH_CATALOG_RULE_CATEGORY_CHAPTER = "章节"
const val FETCH_CATALOG_RULE_CATEGORY_PURIFY = "净化"

data class FetchCatalogRuleItem(
    val index: Int,
    val category: String,
    val name: String,
    val search: String,
    val replacement: String = "",
    val regex: Boolean = false,
    val enabled: Boolean = true
)

internal fun normalizeFetchCatalogRuleCategory(value: String): String {
    return if (value.trim() == FETCH_CATALOG_RULE_CATEGORY_CHAPTER) {
        FETCH_CATALOG_RULE_CATEGORY_CHAPTER
    } else {
        FETCH_CATALOG_RULE_CATEGORY_PURIFY
    }
}

/** 仅解析结构化 JSON 形式的目录过滤规则；非 JSON（旧行命令）返回空列表，由引擎按旧逻辑处理。 */
fun parseFetchCatalogRuleItems(text: String): List<FetchCatalogRuleItem> {
    val trimmed = text.trim()
    if (!trimmed.startsWith("[")) return emptyList()
    return runCatching {
        val array = JSONArray(trimmed)
        buildList {
            for (i in 0 until array.length()) {
                val json = array.optJSONObject(i) ?: continue
                val search = json.optString("search")
                add(
                    FetchCatalogRuleItem(
                        index = size,
                        category = normalizeFetchCatalogRuleCategory(json.optString("category")),
                        name = json.optString("name").ifBlank { "规则${size + 1}" },
                        search = search,
                        replacement = json.optString("replacement"),
                        regex = json.optBoolean("regex", false),
                        enabled = json.optBoolean("enabled", true)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

fun serializeFetchCatalogRuleItems(items: List<FetchCatalogRuleItem>): String {
    val array = JSONArray()
    items.forEach { item ->
        val json = JSONObject()
        json.put("name", item.name.trim())
        json.put("search", item.search)
        json.put("replacement", item.replacement)
        json.put("regex", item.regex)
        json.put("category", normalizeFetchCatalogRuleCategory(item.category))
        json.put("enabled", item.enabled)
        array.put(json)
    }
    return array.toString()
}

fun addFetchCatalogRule(
    text: String,
    category: String,
    name: String,
    search: String,
    replacement: String,
    regex: Boolean
): String {
    val items = parseFetchCatalogRuleItems(text).toMutableList()
    items += FetchCatalogRuleItem(
        index = items.size,
        category = normalizeFetchCatalogRuleCategory(category),
        name = name.trim(),
        search = search,
        replacement = replacement,
        regex = regex,
        enabled = true
    )
    return serializeFetchCatalogRuleItems(items)
}

fun updateFetchCatalogRule(
    text: String,
    index: Int,
    category: String,
    name: String,
    search: String,
    replacement: String,
    regex: Boolean
): String {
    val items = parseFetchCatalogRuleItems(text).toMutableList()
    val current = items.getOrNull(index) ?: return text
    items[index] = current.copy(
        category = normalizeFetchCatalogRuleCategory(category),
        name = name.trim(),
        search = search,
        replacement = replacement,
        regex = regex
    )
    return serializeFetchCatalogRuleItems(items)
}

fun setFetchCatalogRuleEnabled(text: String, index: Int, enabled: Boolean): String {
    val items = parseFetchCatalogRuleItems(text).toMutableList()
    val current = items.getOrNull(index) ?: return text
    items[index] = current.copy(enabled = enabled)
    return serializeFetchCatalogRuleItems(items)
}

fun deleteFetchCatalogRule(text: String, index: Int): String {
    val items = parseFetchCatalogRuleItems(text).toMutableList()
    if (index !in items.indices) return text
    items.removeAt(index)
    return serializeFetchCatalogRuleItems(items)
}

fun moveFetchCatalogRule(text: String, fromIndex: Int, toIndex: Int): String {
    val items = parseFetchCatalogRuleItems(text).toMutableList()
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return text
    val item = items.removeAt(fromIndex)
    items.add(toIndex, item)
    return serializeFetchCatalogRuleItems(items)
}
