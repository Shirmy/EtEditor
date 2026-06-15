package com.eteditor

internal fun fetchInfoSourceProgress(sourceIndex: Int, sourceTotal: Int, phase: Float): Float {
    val total = sourceTotal.coerceAtLeast(1)
    val sourceStart = sourceIndex.toFloat() / total
    return (sourceStart + phase.coerceIn(0f, 1f) / total).coerceIn(0.02f, 0.96f)
}

internal fun fetchInfoProgressPhase(message: String): Float {
    val cleanMessage = message.trim()
    return when {
        cleanMessage.contains("正在读取正文") -> 0.9f
        cleanMessage.contains("正在抓取正文") -> 0.9f
        cleanMessage.contains("正在抓取目录") -> 0.78f
        cleanMessage.contains("\u6b63\u5728\u6293\u53d6\u7b80\u4ecb") -> 0.74f
        cleanMessage.contains("正在抓取封面") -> 0.74f
        cleanMessage.contains("\u6b63\u5728\u8bfb\u53d6\u8be6\u60c5\u9875") -> 0.55f
        cleanMessage.contains("\u6b63\u5728\u786e\u8ba4\u4f5c\u8005") -> 0.42f
        cleanMessage.contains("搜索镜像") -> 0.28f
        cleanMessage.startsWith("\u6b63\u5728\u5c1d\u8bd5\u641c\u7d22\u6e90") -> 0.28f
        cleanMessage.contains("\u641c\u7d22\u4e2d") -> 0.16f
        else -> 0.42f
    }
}

internal fun fetchInfoAutoSources(content: String): List<String> {
    return when (content) {
        FETCH_INFO_CONTENT_COVER,
        FETCH_INFO_CONTENT_INTRO -> listOf(
            FETCH_INFO_SOURCE_JJWXC,
            FETCH_INFO_SOURCE_GONGZICP,
            FETCH_INFO_SOURCE_SOSAD
        )
        FETCH_INFO_CONTENT_CATALOG -> listOf(
            FETCH_INFO_SOURCE_JJWXC,
            FETCH_INFO_SOURCE_SOSAD
        )
        else -> listOf(FETCH_INFO_SOURCE_JJWXC)
    }
}

internal fun fetchInfoContentLabel(content: String): String {
    return when (content) {
        FETCH_INFO_CONTENT_COVER -> "封面"
        FETCH_INFO_CONTENT_INTRO -> "简介"
        FETCH_INFO_CONTENT_CATALOG -> "目录"
        else -> "内容"
    }
}
