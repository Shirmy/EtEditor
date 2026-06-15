package com.eteditor

import com.eteditor.core.TxtChapter

internal data class TxtSupplementedCatalogLine(
    val lineIndex: Int,
    val originalLine: String,
    val supplementedLine: String
)

internal data class TxtHiddenCatalogMarker(
    val lineIndex: Int,
    val normalizedLine: String
)

internal data class TxtCatalogFormatResult(
    val text: String,
    val chapters: List<TxtChapter>,
    val removedBlankCount: Int,
    val contentLineCount: Int,
    val chapterLineCount: Int
)

internal data class TxtChapterRuleEditResult(
    val success: Boolean,
    val rulesText: String = "",
    val enabledKeys: Set<String> = emptySet()
)

internal data class TxtPurifyRuleEditResult(
    val success: Boolean,
    val rulesText: String = "",
    val changedRule: TxtPurifyRuleItem? = null
)

internal data class TxtBookTitleRuleEditResult(
    val success: Boolean,
    val rulesText: String = ""
)

internal data class TxtMoveChapterResult(
    val text: String,
    val chapters: List<TxtChapter>,
    val insertIndex: Int
)

internal data class TxtSaveChapterMappingResult(
    val text: String,
    val changedCount: Int
)

internal data class TxtSavePrepareResult(
    val mapping: TxtSaveChapterMappingResult,
    val bytes: ByteArray,
    val encodingLabel: String,
    val keepMappedCatalog: Boolean
)

internal data class TxtSaveChapterTitleParts(
    val number: Int,
    val unit: String,
    val suffix: String
)

internal data class TxtBookTitleFilterResult(
    val sourceTitle: String,
    val filteredTitle: String,
    val ruleIndex: Int? = null
)

internal data class TxtBookTitleUpdateResult(
    val success: Boolean,
    val title: String = "",
    val message: String = ""
)

internal data class TxtChapterDetectionConfig(
    val rulesText: String,
    val shortThreshold: Int,
    val longThreshold: Int,
    val hiddenLineIndices: Set<Int>,
    val hintMode: String = TXT_CHAPTER_HINT_MODE_MANUAL
)

internal const val TXT_CHAPTER_HINT_MODE_AUTO = "auto"
internal const val TXT_CHAPTER_HINT_MODE_MANUAL = "manual"
internal val TXT_CHAPTER_HINT_MODES = setOf(
    TXT_CHAPTER_HINT_MODE_AUTO,
    TXT_CHAPTER_HINT_MODE_MANUAL
)

internal data class TxtPurifyApplyResult(
    val hasRules: Boolean,
    val bodyCount: Int,
    val catalogCount: Int
) {
    val changed: Boolean get() = bodyCount > 0 || catalogCount > 0
}

internal data class TxtCatalogDetectionResult(
    val enabledKeys: Set<String>,
    val text: String,
    val chapters: List<TxtChapter>,
    val mappedTitleCount: Int
)

internal data class TxtFullPreviewAnchor(
    val offset: Int,
    val lineIndex: Int
)

internal data class TxtFullPreviewWindow(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val startLineIndex: Int
)

internal data class TxtChapterPreviewSource(
    val text: String,
    val highlightRange: Pair<Int, Int>? = null
)

internal data class TxtLinePosition(
    val startIndex: Int,
    val nextIndex: Int
)
