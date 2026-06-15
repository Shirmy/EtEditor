package com.eteditor

private fun EditorController.builtInToolDefaultDefinition(toolId: String): ToolDefinition? {
    return availableTools.firstOrNull { it.id == toolId }
}

fun EditorController.updateBuiltInToolParameter(toolId: String, key: String, value: String) {
    val definition = builtInToolDefaultDefinition(toolId) ?: return
    val parameter = definition.parameters.firstOrNull { it.key == key } ?: return
    val optionKeys = toolParameterOptionKeys(toolId, key, parameter)
    if (optionKeys.isNotEmpty() && value !in optionKeys) return
    val nextOverrides = builtInParameterOverrides[toolId].orEmpty().toMutableMap()
    if (toolId == "text_replace" && key == TEXT_REPLACE_PARAM_MODE) {
        resetTextReplaceModeSelections(nextOverrides)
        clearTextSearchState()
        clearTextReplaceRuntimeFile(builtInToolPlanId(toolId))
    }
    if (toolId == "generate_cover" && key == COVER_PARAM_IMAGE_URI && value.isNotBlank()) {
        rememberReadableDocumentUri(appContext, value)
    }
    if (isSosadAuthParameter(toolId, key) && value.isNotBlank()) {
        clearSosadLoginInvalid()
    }
    if (isEmptyToolParameterValue(toolId, key, value) || (optionKeys.isNotEmpty() && value == parameter.defaultValue)) {
        nextOverrides.remove(key)
    } else {
        nextOverrides[key] = value
    }
    builtInParameterOverrides = updateBuiltInToolOverrides(
        currentOverrides = builtInParameterOverrides,
        toolId = toolId,
        overrides = cleanBuiltInParameterOverrides(toolId, nextOverrides)
    )
    if (toolId == "file_rename") clearFileRenamePlan()
    if (toolId == "chapter_title_rename") clearTitleRenamePlan()
    if (toolId == "title_format") clearTitleFormatPlan()
    if (toolId == "fetch_info") clearFetchInfoPreview()
    if (toolId == "insert_chapter") {
        clearInsertChapterSourcePreview()
        clearFetchInfoSearchChoiceRequest(builtInToolPlanId(toolId))
    }
    if (toolId == "generate_cover") clearGeneratedCoverPreview()
}

fun EditorController.saveBuiltInToolDefaults(toolId: String): Boolean {
    val definition = builtInToolDefaultDefinition(toolId) ?: return false
    if (definition.parameters.isEmpty()) {
        statusMessage = "这个功能没有可设为默认的参数"
        return false
    }
    if (!validateToolParametersForSave(
            toolId = toolId,
            overrides = builtInParameterOverrides[toolId].orEmpty(),
            savingBuiltInDefault = true
        )
    ) {
        return false
    }
    savedBuiltInDefaultOverrides = saveBuiltInDefaultParameterOverrides(
        savedDefaults = savedBuiltInDefaultOverrides,
        toolId = toolId,
        overrides = builtInParameterOverrides[toolId].orEmpty().withoutSensitiveParameters(),
        cleanOverridesForSave = ::cleanBuiltInDefaultOverridesForSave
    )
    persistBuiltInToolDefaults()
    statusMessage = "已设为默认：${toolLabel(toolId)}"
    return true
}

fun EditorController.resetBuiltInToolDefaults(toolId: String): Boolean {
    if (builtInToolDefaultDefinition(toolId) == null) return false
    savedBuiltInDefaultOverrides = removeBuiltInDefaultParameterOverrides(savedBuiltInDefaultOverrides, toolId)
    persistBuiltInToolDefaults()
    resetBuiltInToolState(toolId)
    statusMessage = "已重置默认：${toolLabel(toolId)}"
    return true
}

fun EditorController.resetBuiltInToolState(toolId: String? = null) {
    builtInParameterOverrides = resetBuiltInParameterOverridesFromDefaults(
        currentOverrides = builtInParameterOverrides,
        savedDefaults = savedBuiltInDefaultOverrides,
        toolId = toolId
    )

    if (toolId == null || toolId == "file_rename") {
        clearFileRenamePlan()
    }
    if (toolId == null || toolId == "chapter_title_rename") {
        clearTitleRenamePlan()
    }
    if (toolId == null || toolId == "title_format") {
        clearTitleFormatPlan()
    }
    if (toolId == null || toolId == "text_replace") {
        clearTextSearchState()
        clearTextReplaceRuntimeFile(builtInToolPlanId("text_replace"))
    }
    if (toolId == null || toolId == "fetch_info") {
        clearFetchInfoPreview()
    }
    if (toolId == null || toolId == "generate_cover") {
        clearGeneratedCoverPreview()
    }
    if (toolId == null || toolId == "insert_chapter") {
        clearInsertChapterSourcePreview()
    }
}
