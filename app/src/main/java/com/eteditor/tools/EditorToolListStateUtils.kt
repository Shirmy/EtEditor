package com.eteditor

internal data class NumberedEditorToolAddResult(
    val tools: List<EditorTool>,
    val addedTool: EditorTool,
    val nextNumber: Int
)

internal data class EditorToolListUpdateResult(
    val tools: List<EditorTool>,
    val updatedTool: EditorTool
)

internal data class ImportedEditorToolListState(
    val tools: List<EditorTool>,
    val selectedToolId: String?,
    val draftTool: EditorTool?
)

internal fun appendNumberedEditorTool(
    tools: List<EditorTool>,
    idPrefix: String,
    nextNumber: Int,
    name: String,
    toolId: String,
    parameterOverrides: Map<String, String>,
    group: String = ""
): NumberedEditorToolAddResult {
    val addedTool = EditorTool(
        id = "$idPrefix$nextNumber",
        name = name,
        group = group,
        toolId = toolId,
        parameterOverrides = parameterOverrides
    )
    return NumberedEditorToolAddResult(
        tools = tools + addedTool,
        addedTool = addedTool,
        nextNumber = nextNumber + 1
    )
}

internal fun upsertEditorTool(
    tools: List<EditorTool>,
    tool: EditorTool,
    append: Boolean
): List<EditorTool> {
    return if (append) {
        tools + tool
    } else {
        tools.map { current -> if (current.id == tool.id) tool else current }
    }
}

internal fun updateEditorToolById(
    tools: List<EditorTool>,
    toolId: String,
    update: (EditorTool) -> EditorTool
): EditorToolListUpdateResult? {
    var updatedTool: EditorTool? = null
    val nextTools = tools.map { tool ->
        if (tool.id == toolId) {
            update(tool).also { updatedTool = it }
        } else {
            tool
        }
    }
    return updatedTool?.let { EditorToolListUpdateResult(nextTools, it) }
}

internal fun deleteEditorToolById(
    tools: List<EditorTool>,
    toolId: String
): List<EditorTool>? {
    if (tools.none { it.id == toolId }) return null
    return tools.filterNot { it.id == toolId }
}

internal fun moveEditorToolById(
    tools: List<EditorTool>,
    fromToolId: String,
    toToolId: String
): List<EditorTool>? {
    if (fromToolId == toToolId) return null
    val items = tools.toMutableList()
    val fromIndex = items.indexOfFirst { it.id == fromToolId }
    val toIndex = items.indexOfFirst { it.id == toToolId }
    if (fromIndex < 0 || toIndex < 0) return null
    val moved = items.removeAt(fromIndex)
    items.add(toIndex.coerceIn(0, items.size), moved)
    return items
}

internal fun editorToolGroupOptions(tools: List<EditorTool>): List<String> {
    return tools.map { it.group.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
}

internal fun replaceEditorToolPresetsForImport(
    currentTools: List<EditorTool>,
    selectedToolId: String?,
    draftTool: EditorTool?,
    replaceToolIds: Set<String>,
    importedTools: List<EditorTool>
): ImportedEditorToolListState {
    val keptTools = currentTools.filterNot { tool -> tool.toolId in replaceToolIds }
    val normalizedTools = assignUniqueEditorToolIds(importedTools, keptTools, "tool-")
    val nextTools = keptTools + normalizedTools
    return ImportedEditorToolListState(
        tools = nextTools,
        selectedToolId = selectedToolId?.takeIf { id -> nextTools.any { tool -> tool.id == id } },
        draftTool = draftTool.takeUnless { tool -> tool?.toolId?.let { it in replaceToolIds } == true }
    )
}

internal fun replaceTextReplacePresetsForImport(
    importedPresets: List<EditorTool>,
    idPrefix: String,
    normalize: (EditorTool) -> EditorTool?
): List<EditorTool> {
    return assignUniqueEditorToolIds(
        importedTools = importedPresets,
        existingTools = emptyList(),
        idPrefix = idPrefix,
        normalize = normalize
    )
}
