package com.eteditor

internal fun EditorController.clearTextSearchState() {
    if (txtTextReplacementRefreshDeferred && !txtTextReplacementRefreshApplying) {
        applyDeferredTxtTextReplacementRefresh()
    }
    textSearchResults = emptyList()
    textSearchTotalMatchCount = 0
    textSearchRuleTotalCounts = emptyMap()
    textSearchRuleCursors = emptyMap()
    textSearchToolId = null
    replacementFilePreview = null
    selectedTextSearchResultId = null
    selectedReplacementPreviewMatchId = null
    clearPreviewHighlight()
}

internal data class TextSearchStateAfterBodyTextChange(
    val replacementFilePreview: ReplacementFilePreview?,
    val textSearchToolId: String?
)

internal fun EditorController.clearTextSearchStateAfterBodyTextChange() {
    val previousReplacementPreview = replacementFilePreview
    val previousTextSearchToolId = textSearchToolId
    clearTextSearchState()
    val restored = textSearchStateAfterBodyTextChange(
        previousReplacementPreview = previousReplacementPreview,
        previousTextSearchToolId = previousTextSearchToolId,
        rebuildReplacementPreview = ::rebuildReplacementFilePreviewAfterBodyTextChange,
        rebuildTextSearchPreview = { toolId ->
            textSearchToolId = toolId
            rebuildCurrentTextSearchPreviewAfterDocumentChange()
        }
    )
    replacementFilePreview = restored.replacementFilePreview
    textSearchToolId = restored.textSearchToolId
}

// 改正文后恢复预览：有替换预览优先重建；否则再重建静读。
internal fun textSearchStateAfterBodyTextChange(
    previousReplacementPreview: ReplacementFilePreview?,
    previousTextSearchToolId: String?,
    rebuildReplacementPreview: (ReplacementFilePreview) -> ReplacementFilePreview?,
    rebuildTextSearchPreview: (String) -> Boolean
): TextSearchStateAfterBodyTextChange {
    val nextReplacementPreview = replacementPreviewAfterBodyTextChange(
        previousReplacementPreview,
        rebuildReplacementPreview
    )
    if (nextReplacementPreview != null) {
        return TextSearchStateAfterBodyTextChange(
            replacementFilePreview = nextReplacementPreview,
            textSearchToolId = null
        )
    }
    val nextTextSearchToolId = textSearchPreviewToolIdAfterBodyTextChange(
        previousTextSearchToolId,
        rebuildTextSearchPreview
    )
    return TextSearchStateAfterBodyTextChange(
        replacementFilePreview = null,
        textSearchToolId = nextTextSearchToolId
    )
}

internal fun replacementPreviewAfterBodyTextChange(
    previousPreview: ReplacementFilePreview?,
    rebuildPreview: (ReplacementFilePreview) -> ReplacementFilePreview?
): ReplacementFilePreview? {
    return previousPreview?.let(rebuildPreview)
}

internal fun textSearchPreviewToolIdAfterBodyTextChange(
    previousToolId: String?,
    rebuildPreview: (String) -> Boolean
): String? {
    val toolId = previousToolId ?: return null
    return if (rebuildPreview(toolId)) toolId else null
}

internal fun EditorController.clearPreviewHighlight() {
    selectedTextSearchResultId = null
    selectedReplacementPreviewMatchId = null
    previewDisplayChapterIndexOverride = null
    previewHighlightChapterIndex = null
    previewHighlightSourceStart = -1
    previewHighlightSourceEnd = -1
    previewHighlightStart = -1
    previewHighlightEnd = -1
}
