package com.eteditor

import com.eteditor.core.ChapterInfo

internal fun toolScopeTargetChapterIndices(
    scope: String,
    size: Int,
    currentIndex: Int,
    chapters: List<ChapterInfo>,
    matchPattern: String = "",
    matchRegexEnabled: Boolean = true,
    onError: (String) -> Unit = {}
): List<Int> {
    if (size <= 0) return emptyList()
    return when (scope) {
        TOOL_SCOPE_CURRENT -> listOf(currentIndex.coerceIn(0, size - 1))
        TOOL_SCOPE_FILE_REGEX -> {
            val matcher = toolScopeFileNameMatcher(matchPattern, matchRegexEnabled, onError)
                ?: return emptyList()
            val matches = chapters.mapIndexedNotNull { index, chapter ->
                index.takeIf {
                    matcher(chapter.fileName) || matcher(chapter.source)
                }
            }
            if (matches.isEmpty()) onError("作用范围未匹配章节")
            matches
        }
        else -> (0 until size).toList()
    }
}

internal fun toolScopeFileNameMatcher(
    pattern: String,
    regexEnabled: Boolean,
    onError: (String) -> Unit = {}
): ((String) -> Boolean)? {
    val text = pattern.trim()
    if (text.isBlank()) {
        onError("请输入匹配规则")
        return null
    }
    if (!regexEnabled) {
        return { value -> value.contains(text) }
    }
    val regex = toolScopeRegex(text, onError) ?: return null
    return { value -> regex.containsMatchIn(value) }
}

private fun toolScopeRegex(
    pattern: String,
    onError: (String) -> Unit
): Regex? {
    val text = pattern.trim()
    if (text.isBlank()) {
        onError("请输入作用范围正则")
        return null
    }
    return runCatching { Regex(text) }
        .getOrElse {
            onError("作用范围正则错误")
            null
        }
}
