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

internal fun textReplaceReplacementModeDocumentMessage(
    documentKind: DocumentKind,
    replacementMode: Boolean
): String? {
    if (!replacementMode || documentKind == DocumentKind.Epub) return null
    return if (documentKind == DocumentKind.None) {
        "请先打开 EPUB"
    } else {
        ".replacement 规则仅支持 EPUB"
    }
}

internal fun textReplaceReplacementPreviewSessionMessage(
    expectedSessionKey: Int,
    currentSessionKey: Int,
    documentKind: DocumentKind
): String? {
    textReplaceReplacementModeDocumentMessage(documentKind, replacementMode = true)?.let { message ->
        return message
    }
    return if (currentSessionKey != expectedSessionKey) {
        "文档已切换，请重新加载 .replacement 预览"
    } else {
        null
    }
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
