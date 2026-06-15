package com.eteditor

import com.eteditor.core.TxtChapter

internal fun EditorController.currentTxtChapterDetectionConfig(): TxtChapterDetectionConfig {
    return TxtChapterDetectionConfig(
        rulesText = txtChapterRulesText,
        shortThreshold = if (txtShortChapterHintEnabled) {
            txtShortChapterThreshold
        } else {
            0
        },
        longThreshold = if (txtLongChapterHintEnabled) {
            txtLongChapterThreshold
        } else {
            0
        },
        hiddenLineIndices = txtHiddenCatalogLineIndices,
        hintMode = txtChapterHintMode.takeIf { it in TXT_CHAPTER_HINT_MODES } ?: TXT_CHAPTER_HINT_MODE_AUTO
    )
}

internal fun EditorController.effectiveTxtChapterRuleItems(): List<TxtChapterRuleItem> {
    return effectiveTxtChapterRuleItems(txtChapterRulesText, txtEnabledChapterRuleKeys)
}

internal fun EditorController.detectCurrentTxtChapters(
    text: String,
    applyRuleReplacementToTitle: Boolean = true
): List<TxtChapter> {
    return detectTxtChaptersWithConfig(
        text = text,
        config = currentTxtChapterDetectionConfig(),
        autoKeys = txtEnabledChapterRuleKeys,
        applyRuleReplacementToTitle = applyRuleReplacementToTitle
    )
}

internal fun EditorController.detectTxtChaptersWithConfig(
    text: String,
    config: TxtChapterDetectionConfig,
    autoKeys: Set<String>,
    applyRuleReplacementToTitle: Boolean = true,
    forcedLineIndicesOverride: Set<Int>? = null
): List<TxtChapter> {
    return detectTxtChaptersWithCatalogConfig(
        text = text,
        config = config,
        autoKeys = autoKeys,
        supplementedCatalogLines = txtSupplementedCatalogLines,
        applyRuleReplacementToTitle = applyRuleReplacementToTitle,
        forcedLineIndicesOverride = forcedLineIndicesOverride
    )
}

internal fun EditorController.detectTxtChaptersWithMappingsApplied(
    text: String,
    config: TxtChapterDetectionConfig,
    autoKeys: Set<String>
): TxtCatalogDetectionResult {
    return detectTxtChaptersWithMappingsAppliedToText(
        text = text,
        config = config,
        autoKeys = autoKeys,
        supplementedCatalogLines = txtSupplementedCatalogLines
    )
}

internal fun EditorController.refreshTxtDocumentChapters() {
    val document = txt ?: return
    val config = currentTxtChapterDetectionConfig()
    val result = detectTxtChaptersWithMappingsApplied(document.text, config, txtEnabledChapterRuleKeys)
    if (result.text != document.text) {
        document.text = result.text
        markDocumentChanged()
        clearTextSearchState()
    }
    document.chapters = result.chapters
    previewChapterIndex = previewChapterIndex.coerceAtMost(document.chapters.lastIndex.coerceAtLeast(0))
    checkReport = null
}
