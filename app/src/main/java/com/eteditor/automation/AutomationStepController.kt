package com.eteditor

private fun EditorController.automationToolDefinition(toolId: String): ToolDefinition? {
    return availableTools.firstOrNull { it.id == toolId }
}

private fun EditorController.addAutomationStep(
    toolId: String,
    name: String = "",
    parameterOverrides: Map<String, String> = emptyMap(),
    presetId: String = ""
): String? {
    val tool = automationToolDefinition(toolId) ?: return null
    val chain = selectedAutomationChain ?: return null
    val addResult = appendNumberedAutomationStep(
        chain = chain,
        nextNumber = nextAutomationStepNumber,
        name = name.ifBlank { tool.title },
        toolId = tool.id,
        parameterOverrides = cleanParameterOverrides(tool.id, parameterOverrides),
        presetId = presetId
    )
    nextAutomationStepNumber = addResult.nextNumber
    updateAutomationChain(addResult.chain)
    return addResult.step.id
}

fun EditorController.addConfiguredBuiltInToolToSelectedChain(toolId: String, name: String = ""): String? {
    val definition = automationToolDefinition(toolId)?.takeIf { it.id in EPUB_EDITOR_TOOL_IDS } ?: return null
    if (selectedAutomationChain == null) {
        statusMessage = "\u6267\u884c\u94fe\u4e0d\u5b58\u5728"
        return null
    }
    val parameterOverrides = builtInParameterOverrides[definition.id].orEmpty()
    if (!validateToolParametersForSave(definition.id, parameterOverrides)) {
        return null
    }
    val stepId = addAutomationStep(
        toolId = definition.id,
        name = name.trim().ifBlank { definition.title },
        parameterOverrides = parameterOverrides
    )
    if (stepId != null) {
        statusMessage = "\u5df2\u6dfb\u52a0\u6b65\u9aa4\uff1a${name.trim().ifBlank { definition.title }}"
    }
    return stepId
}

fun EditorController.saveBuiltInToolAsPresetAndAddToSelectedChain(toolId: String, name: String): Boolean {
    val definition = automationToolDefinition(toolId)?.takeIf { it.id in EPUB_EDITOR_TOOL_IDS } ?: return false
    if (selectedAutomationChain == null) {
        statusMessage = "\u6267\u884c\u94fe\u4e0d\u5b58\u5728"
        return false
    }
    val cleanName = name.trim()
    if (cleanName.isBlank()) {
        statusMessage = "\u8bf7\u8f93\u5165\u9884\u8bbe\u540d\u5b57"
        return false
    }
    val parameterOverrides = builtInParameterOverrides[definition.id].orEmpty()
    if (!validateToolParametersForSave(definition.id, parameterOverrides)) {
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
            parameterOverrides,
            includeSensitive = false
        )
    )
    nextEditorToolNumber = addResult.nextNumber
    editorTools = addResult.tools
    val saved = addResult.addedTool
    selectedEditorToolId = saved.id
    persistEditorTools()
    if (addEditorToolToSelectedChain(saved.id) == null) {
        statusMessage = "\u6267\u884c\u94fe\u4e0d\u5b58\u5728"
        return false
    }
    statusMessage = "\u5df2\u4fdd\u5b58\u9884\u8bbe\u5e76\u6dfb\u52a0\uff1a${saved.name}"
    return true
}

fun EditorController.addEditorToolToSelectedChain(editorToolId: String): String? {
    val tool = editorTools.firstOrNull { it.id == editorToolId && it.toolId in EPUB_EDITOR_TOOL_IDS } ?: return null
    return addAutomationStep(
        toolId = tool.toolId,
        name = tool.name.ifBlank { editorToolBaseLabel(tool) },
        parameterOverrides = tool.parameterOverrides,
        presetId = tool.id
    )
}

fun EditorController.removeAutomationStepFromSelected(index: Int) {
    val chain = selectedAutomationChain ?: return
    updateAutomationChain(removeAutomationStepAt(chain, index) ?: return)
}

fun EditorController.moveAutomationStepFromSelected(fromIndex: Int, toIndex: Int) {
    val chain = selectedAutomationChain ?: return
    updateAutomationChain(moveAutomationStepAt(chain, fromIndex, toIndex) ?: return)
}

fun EditorController.updateAutomationStepParameter(stepId: String, key: String, value: String) {
    val chain = selectedAutomationChain ?: return
    val step = chain.steps.firstOrNull { it.id == stepId } ?: return
    val definition = automationToolDefinition(step.toolId) ?: return
    val parameter = definition.parameters.firstOrNull { it.key == key } ?: return
    val optionKeys = toolParameterOptionKeys(step.toolId, key, parameter)
    if (optionKeys.isNotEmpty() && value !in optionKeys) return
    val nextOverrides = step.parameterOverrides.toMutableMap()
    if (step.toolId == "text_replace" && key == TEXT_REPLACE_PARAM_MODE) {
        resetTextReplaceModeSelections(nextOverrides)
        clearTextSearchState()
        clearTextReplaceRuntimeFile(step.id)
    }
    if (step.toolId == "generate_cover" && key == COVER_PARAM_IMAGE_URI && value.isNotBlank()) {
        rememberReadableDocumentUri(appContext, value)
    }
    if (isSosadAuthParameter(step.toolId, key) && value.isNotBlank()) {
        clearSosadLoginInvalid()
    }
    if (isEmptyToolParameterValue(step.toolId, key, value) || value == parameter.defaultValue) {
        nextOverrides.remove(key)
    } else {
        nextOverrides[key] = value
    }
    if (step.toolId == "generate_cover") clearGeneratedCoverPreview()
    if (step.toolId == "insert_chapter") {
        clearInsertChapterSourcePreview()
        clearFetchInfoSearchChoiceRequest(step.id)
    }
    updateAutomationStep(
        step.copy(parameterOverrides = cleanParameterOverrides(step.toolId, nextOverrides))
    )
}

fun EditorController.renameAutomationStep(stepId: String, name: String) {
    val chain = selectedAutomationChain ?: return
    val step = chain.steps.firstOrNull { it.id == stepId } ?: return
    if (step.presetId.isNotBlank()) {
        statusMessage = "\u9884\u8bbe\u540d\u53ea\u80fd\u5728\u9884\u8bbe\u91cc\u4fee\u6539"
        return
    }
    updateAutomationStep(step.copy(name = name))
}

private fun EditorController.updateAutomationStep(updated: AutomationStep) {
    val chain = selectedAutomationChain ?: return
    updateAutomationChain(updateAutomationStepById(chain, updated))
}

fun EditorController.saveAutomationStepAsPreset(stepId: String, name: String): Boolean {
    val chain = selectedAutomationChain ?: return false
    val step = chain.steps.firstOrNull { it.id == stepId } ?: return false
    val definition = automationToolDefinition(step.toolId)?.takeIf { it.id in EPUB_EDITOR_TOOL_IDS } ?: return false
    val cleanName = name.trim()
    if (cleanName.isBlank()) {
        statusMessage = "请输入预设名字"
        return false
    }
    if (!validateToolParametersForSave(definition.id, step.parameterOverrides)) {
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
            step.parameterOverrides,
            includeSensitive = false
        )
    )
    nextEditorToolNumber = addResult.nextNumber
    editorTools = addResult.tools
    val saved = addResult.addedTool
    persistEditorTools()
    statusMessage = "已保存预设：${saved.name}"
    return true
}
