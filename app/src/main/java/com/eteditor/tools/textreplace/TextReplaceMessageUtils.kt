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

    // 其余各种"确实没匹配到"（含搜网页标签没找到）统一显示"无匹配内容"；
    // 上面那种"范围/规则本身有问题"的提示不是"没匹配"，已在前面原样保留。
    return "无匹配内容"
}
