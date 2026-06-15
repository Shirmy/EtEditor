package com.eteditor

private fun EditorController.editorToolDefinition(toolId: String): ToolDefinition? {
    return availableTools.firstOrNull { it.id == toolId }
}

fun EditorController.createEditorTool(toolId: String = "file_rename") {
    val allowedToolIds = editorToolIdsForDocumentKind(kind)
    val fallbackToolId = allowedToolIds.firstOrNull() ?: return
    val definition = editorToolDefinition(toolId.takeIf { it in allowedToolIds } ?: fallbackToolId)
        ?: return
    draftEditorTool = EditorTool(
        id = DRAFT_EDITOR_TOOL_ID,
        name = "",
        toolId = definition.id
    )
    selectedEditorToolId = null
}

fun EditorController.saveBuiltInToolAsPreset(toolId: String, name: String): Boolean {
    val definition = editorToolDefinition(toolId)?.takeIf { it.id in editorToolIdsForDocumentKind(kind) } ?: return false
    val cleanName = name.trim()
    if (cleanName.isBlank()) {
        statusMessage = "请输入预设名字"
        return false
    }
    if (!validateToolParametersForSave(definition.id, builtInParameterOverrides[definition.id].orEmpty())) {
        return false
    }
    val addResult = appendNumberedEditorTool(
        tools = editorTools,
        idPrefix = "tool-",
        nextNumber = nextEditorToolNumber,
        name = cleanName,
        toolId = definition.id,
        parameterOverrides = exportParameterOverrides(
            definition.id,
            builtInParameterOverrides[definition.id].orEmpty(),
            includeSensitive = false
        )
    )
    nextEditorToolNumber = addResult.nextNumber
    editorTools = addResult.tools
    val saved = addResult.addedTool
    selectedEditorToolId = saved.id
    persistEditorTools()
    statusMessage = "已保存预设：${saved.name}"
    return true
}

fun EditorController.saveEditorToolDraft(): Boolean {
    val draft = draftEditorTool ?: return false
    if (draft.name.isBlank()) return false
    val definition = editorToolDefinition(draft.toolId)?.takeIf { it.id in editorToolIdsForDocumentKind(kind) } ?: return false
    if (!validateToolParametersForSave(definition.id, draft.parameterOverrides)) {
        return false
    }
    val creating = draft.id == DRAFT_EDITOR_TOOL_ID
    val savedId = if (creating) "tool-${nextEditorToolNumber++}" else draft.id
    val saved = draft.copy(
        id = savedId,
        name = draft.name,
        parameterOverrides = exportParameterOverrides(
            definition.id,
            draft.parameterOverrides,
            includeSensitive = false
        )
    )
    editorTools = upsertEditorTool(editorTools, saved, append = creating)
    selectedEditorToolId = saved.id
    draftEditorTool = null
    persistEditorTools()
    return true
}

fun EditorController.discardEditorToolDraft() {
    draftEditorTool = null
    selectedEditorToolId = null
}

fun EditorController.selectEditorTool(toolId: String) {
    val tool = editorTools.firstOrNull { it.id == toolId } ?: return
    draftEditorTool = tool.copy()
    selectedEditorToolId = toolId
}

fun EditorController.deleteEditorTool(toolId: String): Boolean {
    editorTools = deleteEditorToolById(editorTools, toolId) ?: return false
    if (selectedEditorToolId == toolId) selectedEditorToolId = null
    if (draftEditorTool?.id == toolId) draftEditorTool = null
    persistEditorTools()
    statusMessage = "预设已删除"
    return true
}

fun EditorController.moveEditorTool(fromToolId: String, toToolId: String): Boolean {
    editorTools = moveEditorToolById(editorTools, fromToolId, toToolId) ?: return false
    persistEditorTools()
    return true
}

fun EditorController.renameEditorTool(name: String) {
    val tool = selectedEditorTool ?: return
    updateEditorTool(tool.copy(name = name))
}

fun EditorController.renameSavedEditorTool(toolId: String, name: String) {
    val cleanName = name.trim()
    if (cleanName.isBlank()) return
    val updateResult = updateEditorToolById(editorTools, toolId) { tool ->
        tool.copy(name = cleanName)
    } ?: return
    editorTools = updateResult.tools
    if (draftEditorTool?.id == toolId) {
        draftEditorTool = draftEditorTool?.copy(name = cleanName)
    }
    persistEditorTools()
}

fun EditorController.updateEditorToolGroup(toolId: String, group: String) {
    val cleanGroup = group.trim()
    val updateResult = updateEditorToolById(editorTools, toolId) { tool ->
        tool.copy(group = cleanGroup)
    }
    if (updateResult != null) {
        editorTools = updateResult.tools
    }
    if (draftEditorTool?.id == toolId) {
        draftEditorTool = draftEditorTool?.copy(group = cleanGroup)
    }
    if (updateResult == null && draftEditorTool?.id != toolId) return
    persistEditorTools()
}

fun EditorController.changeEditorToolBase(toolId: String) {
    val definition = editorToolDefinition(toolId)?.takeIf { it.id in editorToolIdsForDocumentKind(kind) } ?: return
    val tool = selectedEditorTool ?: return
    updateEditorTool(
        tool.copy(
            toolId = definition.id,
            parameterOverrides = emptyMap()
        )
    )
}

fun EditorController.updateEditorToolParameter(key: String, value: String) {
    val tool = selectedEditorTool ?: return
    val definition = editorToolDefinition(tool.toolId) ?: return
    val parameter = definition.parameters.firstOrNull { it.key == key } ?: return
    val nextOverrides = tool.parameterOverrides.toMutableMap()
    if (tool.toolId == "text_replace" && key == TEXT_REPLACE_PARAM_MODE) {
        resetTextReplaceModeSelections(nextOverrides)
        clearTextSearchState()
        clearTextReplaceRuntimeFile(tool.id)
    }
    if (tool.toolId == "generate_cover" && key == COVER_PARAM_IMAGE_URI && value.isNotBlank()) {
        rememberReadableDocumentUri(appContext, value)
    }
    if (isSosadAuthParameter(tool.toolId, key) && value.isNotBlank()) {
        clearSosadLoginInvalid()
    }
    if (value == parameter.defaultValue) {
        nextOverrides.remove(key)
    } else {
        nextOverrides[key] = value
    }
    if (tool.toolId == "fetch_info") clearFetchInfoPreview()
    if (tool.toolId == "insert_chapter") {
        clearInsertChapterSourcePreview()
        clearFetchInfoSearchChoiceRequest(tool.id)
    }
    if (tool.toolId == "generate_cover") clearGeneratedCoverPreview()
    updateEditorTool(tool.copy(parameterOverrides = cleanParameterOverrides(tool.toolId, nextOverrides)))
}

private fun EditorController.updateEditorTool(updated: EditorTool) {
    clearFileRenamePlan()
    clearTitleRenamePlan()
    clearTitleFormatPlan()
    clearFetchInfoPreview()
    clearInsertChapterSourcePreview()
    clearGeneratedCoverPreview()
    if (draftEditorTool?.id == updated.id) {
        draftEditorTool = updated
        return
    }
    editorTools = upsertEditorTool(editorTools, updated, append = false)
    persistEditorTools()
}
