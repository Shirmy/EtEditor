package com.eteditor.core

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal fun String.decodeUrl(): String {
    return runCatching { URLDecoder.decode(this, StandardCharsets.UTF_8.name()) }.getOrDefault(this)
}

internal fun normalizePath(path: String): String {
    val result = ArrayDeque<String>()
    path.replace('\\', '/').split('/').forEach { part ->
        when {
            part.isBlank() || part == "." -> Unit
            part == ".." -> {
                if (result.isNotEmpty()) result.removeLast()
            }
            else -> result.addLast(part)
        }
    }
    return result.joinToString("/")
}

internal fun relativeHref(fromDir: String, targetPath: String): String {
    val cleanFrom = normalizePath(fromDir).split('/').filter { it.isNotBlank() }
    val cleanTarget = normalizePath(targetPath).split('/').filter { it.isNotBlank() }
    var common = 0
    while (common < cleanFrom.size && common < cleanTarget.size && cleanFrom[common] == cleanTarget[common]) {
        common += 1
    }
    val ups = List(cleanFrom.size - common) { ".." }
    val rest = cleanTarget.drop(common)
    return (ups + rest).joinToString("/").ifBlank { targetPath.substringAfterLast('/') }
}
