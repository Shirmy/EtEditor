package com.eteditor

fun EditorController.textReplaceRuntimeFile(toolId: String): String {
    return textReplaceRuntimeFiles[toolId].orEmpty()
}

fun EditorController.updateTextReplaceRuntimeFile(toolId: String, value: String) {
    textReplaceRuntimeFiles = textReplaceRuntimeFiles
        .toMutableMap()
        .also { files ->
            if (value.isBlank()) {
                files.remove(toolId)
            } else {
                files[toolId] = value
            }
        }
}

fun EditorController.clearTextReplaceRuntimeFile(toolId: String) {
    updateTextReplaceRuntimeFile(toolId, "")
}

fun EditorController.clearReplacementFilePreview(toolId: String) {
    if (replacementFilePreview?.toolId == toolId) {
        replacementFilePreview = null
        selectedReplacementPreviewMatchId = null
        clearPreviewHighlight()
    }
}

fun EditorController.prepareReplacementFilePreviewForEditorTool(editorToolId: String): Boolean {
    val tool = selectedEditorTool?.takeIf { it.id == editorToolId }
        ?: editorTools.firstOrNull { it.id == editorToolId }
        ?: return false
    if (tool.toolId != "text_replace") return false
    return prepareReplacementFilePreview(tool)
}

suspend fun EditorController.prepareReplacementFilePreviewForEditorToolAsync(
    editorToolId: String,
    onProgress: (phase: String, completed: Int, total: Int) -> Unit = { _, _, _ -> }
): Boolean {
    val tool = selectedEditorTool?.takeIf { it.id == editorToolId }
        ?: editorTools.firstOrNull { it.id == editorToolId }
        ?: return false
    if (tool.toolId != "text_replace") return false
    return prepareReplacementFilePreviewAsync(tool, onProgress)
}

fun EditorController.prepareReplacementFilePreviewForBuiltIn(toolId: String): Boolean {
    if (toolId != "text_replace") return false
    return prepareReplacementFilePreview(builtInEditorTool(toolId))
}

suspend fun EditorController.prepareReplacementFilePreviewForBuiltInAsync(
    toolId: String,
    onProgress: (phase: String, completed: Int, total: Int) -> Unit = { _, _, _ -> }
): Boolean {
    if (toolId != "text_replace") return false
    return prepareReplacementFilePreviewAsync(builtInEditorTool(toolId), onProgress)
}
