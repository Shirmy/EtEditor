package com.eteditor

private fun EditorController.runtimeToolsById(): Map<String, ToolDefinition> {
    return availableTools.associateBy { it.id }
}

internal fun resetTextReplaceModeSelections(overrides: MutableMap<String, String>) {
    listOf(
        TEXT_REPLACE_PARAM_TARGET,
        TEXT_REPLACE_PARAM_FIND,
        TEXT_REPLACE_PARAM_REPLACE,
        TEXT_REPLACE_PARAM_FIND_REGEX,
        TEXT_REPLACE_PARAM_BATCH_SOURCE,
        TEXT_REPLACE_PARAM_SCOPE,
        TEXT_REPLACE_PARAM_SELECTED_HTML,
        TEXT_REPLACE_PARAM_BATCH_TEXT,
        TEXT_REPLACE_PARAM_BATCH_FILE,
        TEXT_REPLACE_PARAM_PREVIEW
    ).forEach(overrides::remove)
}

internal fun EditorController.mergedToolParameters(tool: EditorTool): Map<String, String> {
    return mergedEditorToolParameters(tool, runtimeToolsById())
}

internal fun EditorController.defaultToolParameters(toolId: String): Map<String, String> {
    return defaultToolParameters(runtimeToolsById()[toolId])
}

internal fun EditorController.validateToolParametersForSave(
    toolId: String,
    overrides: Map<String, String>,
    savingBuiltInDefault: Boolean = false
): Boolean {
    val defaults = defaultToolParameters(toolId)
    val values = defaults + overrides.filterKeys { it in defaults }
    val error = toolParameterSaveError(toolId, values)
    if (error != null) {
        statusMessage = error
        return false
    }
    return true
}

internal fun EditorController.cleanParameterOverrides(toolId: String, overrides: Map<String, String>): Map<String, String> {
    return cleanToolParameterOverrides(
        toolId = toolId,
        definitions = runtimeToolsById()[toolId]?.parameters.orEmpty(),
        overrides = overrides,
        textReplaceScopeOptionKeys = textReplaceScopeOptionKeys()
    )
}

internal fun EditorController.cleanTxtTextReplacePresetOverrides(
    find: String,
    replace: String,
    regex: Boolean,
    scope: String
): Map<String, String> {
    return cleanTxtTextReplacePresetParameterOverrides(
        definitions = runtimeToolsById()["text_replace"]?.parameters.orEmpty(),
        find = find,
        replace = replace,
        regex = regex,
        scope = scope,
        txtScopeOptions = TXT_TEXT_REPLACE_SCOPE_OPTIONS,
        textReplaceScopeOptionKeys = textReplaceScopeOptionKeys()
    )
}

internal fun EditorController.cleanEpubTextReplacePresetOverrides(
    find: String,
    replace: String,
    regex: Boolean,
    scope: String,
    target: String
): Map<String, String> {
    return cleanEpubTextReplacePresetParameterOverrides(
        definitions = runtimeToolsById()["text_replace"]?.parameters.orEmpty(),
        find = find,
        replace = replace,
        regex = regex,
        scope = scope,
        target = target,
        textReplaceScopeOptionKeys = textReplaceScopeOptionKeys()
    )
}

internal fun EditorController.cleanBuiltInDefaultOverridesForSave(
    toolId: String,
    overrides: Map<String, String>
): Map<String, String> {
    return cleanBuiltInDefaultParameterOverridesForSave(
        toolId = toolId,
        definitions = runtimeToolsById()[toolId]?.parameters.orEmpty(),
        overrides = overrides,
        textReplaceScopeOptionKeys = textReplaceScopeOptionKeys()
    )
}

internal fun EditorController.cleanBuiltInParameterOverrides(toolId: String, overrides: Map<String, String>): Map<String, String> {
    return cleanBuiltInToolParameterOverrides(
        toolId = toolId,
        definitions = runtimeToolsById()[toolId]?.parameters.orEmpty(),
        overrides = overrides,
        textReplaceScopeOptionKeys = textReplaceScopeOptionKeys()
    )
}

internal fun EditorController.toolParameterOptionKeys(
    toolId: String,
    key: String,
    parameter: ToolParameterDefinition
): Set<String> {
    return allowedToolParameterOptionKeys(
        toolId = toolId,
        key = key,
        parameter = parameter,
        textReplaceScopeOptionKeys = textReplaceScopeOptionKeys()
    )
}

private fun textReplaceScopeOptionKeys(): Set<String> {
    return textReplaceScopeOptionKeys(
        textReplaceScopeOptions = TEXT_REPLACE_SCOPE_OPTIONS,
        txtTextReplaceScopeOptions = TXT_TEXT_REPLACE_SCOPE_OPTIONS,
        epubTextReplaceBatchScopeOptions = EPUB_TEXT_REPLACE_BATCH_SCOPE_OPTIONS
    )
}

internal fun EditorController.fileRenameParameters(tool: EditorTool? = null): FileRenameParameters {
    val values = if (tool == null) {
        defaultToolParameters("file_rename")
    } else {
        mergedToolParameters(tool)
    }
    return buildFileRenameParameters(values, BOOL_FALSE)
}

internal fun EditorController.textReplaceParameters(tool: EditorTool? = null): TextReplaceParameters {
    val values = if (tool == null) {
        defaultToolParameters("text_replace")
    } else {
        mergedToolParameters(tool)
    }
    val runtimeFile = tool?.id?.let { textReplaceRuntimeFiles[it] }.orEmpty()
    return buildTextReplaceParameters(
        values = values,
        kind = kind,
        runtimeFile = runtimeFile,
        batchSourceOptions = TEXT_REPLACE_BATCH_SOURCE_OPTIONS,
        textReplaceScopeOptions = TEXT_REPLACE_SCOPE_OPTIONS,
        epubBatchScopeOptions = EPUB_TEXT_REPLACE_BATCH_SCOPE_OPTIONS,
        txtScopeOptions = TXT_TEXT_REPLACE_SCOPE_OPTIONS,
        defaultBatchSource = TEXT_REPLACE_BATCH_INPUT,
        trueValue = BOOL_TRUE,
        falseValue = BOOL_FALSE
    )
}

internal fun EditorController.coverParameters(tool: EditorTool? = null): CoverParameters {
    val values = if (tool == null) {
        defaultToolParameters("generate_cover")
    } else {
        mergedToolParameters(tool)
    }
    return buildCoverParameters(
        values = values,
        modeOptions = COVER_MODE_OPTIONS,
        imageInsertOptions = COVER_IMAGE_INSERT_OPTIONS,
        trueValue = BOOL_TRUE
    )
}
