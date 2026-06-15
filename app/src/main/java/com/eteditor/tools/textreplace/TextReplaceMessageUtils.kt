package com.eteditor

import com.eteditor.core.DocumentKind

internal fun textReplaceRegexErrorMessage(error: Throwable): String {
    val detail = error.message
        ?.lineSequence()
        ?.firstOrNull()
        ?.takeIf { it.isNotBlank() }
    return if (detail == null) "查找正则错误" else "查找正则错误：$detail"
}

internal fun textSearchFoundMessage(count: Int): String {
    return "找到 $count 处"
}

internal fun textReplaceNoMatchMessage(
    documentKind: DocumentKind,
    currentStatusMessage: String,
    parameters: TextReplaceParameters? = null,
    rules: List<TextReplaceRule> = emptyList()
): String {
    val scopedMessage = currentStatusMessage.takeIf { message ->
        message.contains("作用范围") || message.contains("匹配规则")
    }
    if (scopedMessage != null) return scopedMessage

    val firstRule = rules.firstOrNull { it.find.isNotEmpty() }
    val findText = firstRule?.find
        ?: parameters?.findText.orEmpty()
    val looksLikeHtmlTag = Regex("""</?\w+[^>]*>""").containsMatchIn(findText)
    if (documentKind == DocumentKind.Epub &&
        parameters?.target == TEXT_REPLACE_TARGET_SOURCE &&
        looksLikeHtmlTag
    ) {
        return "没有对应的标签"
    }
    if (documentKind == DocumentKind.Epub && parameters?.scope == TOOL_SCOPE_ALL) {
        return "无匹配内容"
    }
    return "无匹配内容"
}
