package com.eteditor

data class TextSearchResult(
    val id: String,
    val ruleIndex: Int,
    val chapterIndex: Int,
    val chapterTitle: String,
    val context: String,
    val matchText: String,
    val contextMatchStart: Int,
    val contextMatchEnd: Int,
    val sourceStart: Int,
    val sourceEnd: Int
)

data class TxtPreviewLineHighlight(
    val lineIndex: Int,
    val start: Int,
    val end: Int
)

data class TxtFullPreviewState(
    val text: String,
    val windowKey: String,
    val startLineIndex: Int,
    val highlightRange: Pair<Int, Int>?,
    val scrollTargetOffset: Int?,
    val scrollTargetLineIndex: Int?
)

data class TxtFullEditWindowSeed(
    val sourceText: String,
    val startOffset: Int,
    val endOffset: Int,
    val targetOffset: Int,
    val targetLineIndex: Int,
    val windowed: Boolean
)

data class TxtChapterRuleItem(
    val index: Int,
    val name: String,
    val pattern: String,
    val replacement: String = "",
    val enabled: Boolean,
    val matchCount: Int = 0
)

data class TxtPurifyRuleItem(
    val index: Int,
    val target: String,
    val name: String,
    val pattern: String,
    val replacement: String,
    val regex: Boolean,
    val enabled: Boolean,
    val matchCount: Int = 0
)

data class TxtBookTitleRuleItem(
    val index: Int,
    val name: String,
    val pattern: String,
    val replacement: String,
    val regex: Boolean,
    val enabled: Boolean,
    val matchCount: Int = 0
)
