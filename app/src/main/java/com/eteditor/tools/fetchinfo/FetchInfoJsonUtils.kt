package com.eteditor

import org.json.JSONArray
import org.json.JSONObject

internal fun findJsonStringValue(text: String, vararg keys: String): String {
    keys.forEach { key ->
        val pattern = Regex(
            "\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        pattern.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.decodeJsonString()
            ?.cleanHtmlText()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
    }
    return extractJsonObjects(text)
        .asSequence()
        .mapNotNull { json -> findJsonValueRecursive(json, keys.toSet()) }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}

internal fun findJsonRawStringValue(text: String, vararg keys: String): String {
    keys.forEach { key ->
        val pattern = Regex(
            "\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        pattern.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.decodeJsonString()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
    }
    return findJsonStringValue(text, *keys)
}

internal fun parseMeta(html: String, name: String): String {
    val direct = Regex(
        """<meta[^>]+(?:name|property)=["']${Regex.escape(name)}["'][^>]+content=["']([^"']*)["'][^>]*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    direct.find(html)?.groupValues?.getOrNull(1)?.cleanHtmlText()?.takeIf { it.isNotBlank() }?.let { return it }
    return Regex(
        """<meta\b[^>]*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).findAll(html)
        .firstOrNull { tag ->
            attr(tag.value, "name").equals(name, ignoreCase = true) ||
                attr(tag.value, "property").equals(name, ignoreCase = true)
        }
        ?.value
        ?.let { attr(it, "content") }
        ?.cleanHtmlText()
        .orEmpty()
}

private fun extractJsonObjects(text: String): List<JSONObject> {
    val candidates = buildList {
        Regex(
            """<script[^>]+id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(text).forEach { add(it.groupValues[1]) }
        Regex(
            """<script[^>]+type=["']application/ld\+json["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(text).forEach { add(it.groupValues[1]) }
        if (text.trimStart().startsWith("{")) add(text)
    }
    return candidates.mapNotNull { candidate ->
        runCatching { JSONObject(candidate.decodeHtmlEntities()) }.getOrNull()
    }
}

private fun findJsonValueRecursive(value: Any?, keys: Set<String>): String? {
    return when (value) {
        is JSONObject -> {
            val iterator = value.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                val child = value.opt(key)
                if (keys.any { it.equals(key, ignoreCase = true) } && child is String && child.isNotBlank()) {
                    return child.cleanHtmlText()
                }
                findJsonValueRecursive(child, keys)?.let { return it }
            }
            null
        }
        is JSONArray -> {
            for (index in 0 until value.length()) {
                findJsonValueRecursive(value.opt(index), keys)?.let { return it }
            }
            null
        }
        else -> null
    }
}

private fun String.decodeJsonString(): String {
    return runCatching {
        JSONObject("""{"value":"$this"}""").optString("value")
    }.getOrDefault(this)
}
