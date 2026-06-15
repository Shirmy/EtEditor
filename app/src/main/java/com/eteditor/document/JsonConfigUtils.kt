package com.eteditor

import org.json.JSONObject

internal fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val result = mutableMapOf<String, String>()
    val iterator = keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        optString(key).takeIf { it.isNotBlank() }?.let { result[key] = it }
    }
    return result
}
