package com.eteditor

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

internal fun attr(tag: String, name: String): String {
    return Regex("""\b${Regex.escape(name)}\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        .find(tag)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
}

internal fun absoluteUrl(value: String, baseUrl: String): String {
    if (value.startsWith("//")) return "https:$value"
    if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) return value
    return runCatching { URI(baseUrl).resolve(value).toString() }.getOrDefault(value)
}

internal fun combineCatalogTitle(chapterTitle: String, summary: String): String {
    val title = chapterTitle.trim()
    val note = summary.trim()
    return when {
        title.isBlank() -> note
        note.isBlank() -> title
        title == note -> title
        else -> "$title $note"
    }
}

internal fun urlEncode(value: String): String {
    return urlEncode(value, StandardCharsets.UTF_8)
}

internal fun urlEncode(value: String, charset: Charset): String {
    return URLEncoder.encode(value, charset.name())
}

internal fun resolveFetchInfoSourceForRetryUrl(
    urlText: String,
    gongziSource: String,
    sosadSource: String,
    jjwxcSource: String
): String? {
    val value = urlText.trim().lowercase()
    return when {
        value.contains("gongzicp.com") ||
            value.contains("webapi.gongzicp.com") -> gongziSource
        value.contains("sosad.fun") ||
            value.contains("xn--pxtr7m.com") ||
            value.contains("/threads/") -> sosadSource
        value.contains("jjwxc.net") ||
            value.contains("onebook.php") ||
            value.contains("novelid=") -> jjwxcSource
        else -> null
    }
}
