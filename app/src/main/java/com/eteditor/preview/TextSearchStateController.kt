package com.eteditor

internal fun EditorController.clearTextSearchState() {
    if (txtTextReplacementRefreshDeferred && !txtTextReplacementRefreshApplying) {
        applyDeferredTxtTextReplacementRefresh()
    }
    textSearchResults = emptyList()
    textSearchToolId = null
    replacementFilePreview = null
    selectedTextSearchResultId = null
    selectedReplacementPreviewMatchId = null
    clearPreviewHighlight()
}

internal fun EditorController.clearTextSearchStateAfterBodyTextChange() {
    val previousReplacementPreview = replacementFilePreview
    val previousTextSearchToolId = textSearchToolId
    clearTextSearchState()
    replacementFilePreview = replacementPreviewAfterBodyTextChange(
        previousReplacementPreview,
        ::rebuildReplacementFilePreviewAfterBodyTextChange
    )
    if (replacementFilePreview == null) {
        textSearchToolId = textSearchPreviewToolIdAfterBodyTextChange(previousTextSearchToolId) { toolId ->
            textSearchToolId = toolId
            rebuildCurrentTextSearchPreviewAfterDocumentChange()
        }
    }
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
