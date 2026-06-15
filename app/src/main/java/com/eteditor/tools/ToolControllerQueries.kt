package com.eteditor

import com.eteditor.core.DocumentKind

private fun EditorController.toolsByIdSnapshot(): Map<String, ToolDefinition> {
    return availableTools.associateBy { it.id }
}

fun EditorController.toolSummary(toolId: String): String {
    return toolsByIdSnapshot()[toolId]?.description ?: ""
}

fun EditorController.titleFormatStyleOptions(): List<Pair<String, String>> {
    return TITLE_FORMAT_STYLE_OPTIONS
}

fun EditorController.editorToolFunctionOptions(): List<Pair<String, String>> {
    return editorToolFunctionOptionsForKind(kind)
}

fun EditorController.editorToolFunctionOptionsForKind(documentKind: DocumentKind): List<Pair<String, String>> {
    return editorToolFunctionOptionsForDocumentKind(documentKind, toolsByIdSnapshot())
}

fun EditorController.builtInToolsForCurrentDocument(): List<ToolDefinition> {
    return builtInToolsForDocumentKind(kind, toolsByIdSnapshot())
}

fun EditorController.canRunToolInCurrentDocument(toolId: String): Boolean {
    return toolId in builtInToolIdsForDocumentKind(kind)
}

fun EditorController.toolParameterDefinitions(toolId: String): List<ToolParameterDefinition> {
    val parameters = toolsByIdSnapshot()[toolId]?.parameters.orEmpty()
    return toolParameterDefinitionsForDocument(
        toolId = toolId,
        kind = kind,
        parameters = parameters,
        txtTextReplaceScopeOptions = TXT_TEXT_REPLACE_SCOPE_OPTIONS
    )
}

fun EditorController.builtInToolParameterValue(toolId: String, key: String): String {
    return builtInParameterOverrides[toolId].orEmpty()[key].orEmpty()
}

fun EditorController.builtInToolCanSavePreset(toolId: String): Boolean {
    return toolId in editorToolIdsForDocumentKind(kind)
}

fun EditorController.builtInToolCanSaveDefault(toolId: String): Boolean {
    return toolsByIdSnapshot()[toolId]?.parameters.orEmpty().isNotEmpty()
}

fun EditorController.coverModeOptions(): List<Pair<String, String>> {
    return COVER_MODE_OPTIONS
}

fun EditorController.coverImageInsertOptions(): List<Pair<String, String>> {
    return COVER_IMAGE_INSERT_OPTIONS
}

fun EditorController.fetchInfoContentOptions(source: String): List<Pair<String, String>> {
    return FETCH_INFO_CONTENT_OPTIONS
}

fun EditorController.toolLabel(toolId: String): String {
    return toolsByIdSnapshot()[toolId]?.title ?: toolId
}

fun EditorController.editorToolBaseLabel(tool: EditorTool): String {
    return toolsByIdSnapshot()[tool.toolId]?.title ?: "缺失基础功能"
}

fun EditorController.editorToolsForCurrentDocument(): List<EditorTool> {
    return editorToolsForDocumentKind(kind, editorTools)
}

fun EditorController.editorToolGroupOptions(): List<String> {
    return editorToolGroupOptions(editorTools)
}

fun EditorController.editorToolsForEpubAutomation(): List<EditorTool> {
    return editorToolsForEpubAutomationModel(editorTools)
}

fun EditorController.automationFunctionTools(): List<ToolDefinition> {
    return automationFunctionToolDefinitions(toolsByIdSnapshot())
}

fun EditorController.editorToolParameterValue(tool: EditorTool, key: String): String {
    return mergedEditorToolParameters(tool, toolsByIdSnapshot())[key].orEmpty()
}

fun EditorController.builtInToolPlanId(toolId: String): String = "builtin-$toolId"

internal fun EditorController.builtInEditorTool(toolId: String): EditorTool {
    return builtInEditorToolModel(
        toolId = toolId,
        planId = builtInToolPlanId(toolId),
        label = toolLabel(toolId),
        parameterOverrides = builtInParameterOverrides[toolId].orEmpty()
    )
}

internal fun EditorController.automationStepSourceTool(step: AutomationStep): EditorTool? {
    return automationStepSourceToolModel(step, editorTools, toolLabel(step.toolId))
}

internal fun EditorController.automationStepToolForRun(step: AutomationStep): EditorTool? {
    return automationStepToolForRunModel(step, editorTools, toolLabel(step.toolId))
}

internal fun EditorController.automationStepToolForPreview(toolId: String): EditorTool? {
    val request = automationConfirmationRequest?.takeIf { it.stepId == toolId } ?: return null
    val step = automationConfirmationStepForRequest(automationChains, request) ?: return null
    return automationStepToolForRun(step)
}

fun EditorController.editorToolNeedsTextReplaceRuleFile(tool: EditorTool): Boolean {
    if (tool.toolId != "text_replace") return false
    val parameters = textReplaceParametersForQuery(
        values = mergedEditorToolParameters(tool, toolsByIdSnapshot()),
        runtimeFile = textReplaceRuntimeFile(tool.id)
    )
    return parameters.isReplacementMode() && parameters.batchFile.isBlank()
}

fun EditorController.builtInTextReplaceNeedsRuleFile(toolId: String): Boolean {
    if (toolId != "text_replace") return false
    val values = defaultToolParameters(toolsByIdSnapshot()[toolId]) + builtInParameterOverrides[toolId].orEmpty()
    val parameters = textReplaceParametersForQuery(
        values = values,
        runtimeFile = textReplaceRuntimeFile(builtInToolPlanId(toolId))
    )
    return parameters.isReplacementMode() && parameters.batchFile.isBlank()
}

private fun EditorController.textReplaceParametersForQuery(
    values: Map<String, String>,
    runtimeFile: String
): TextReplaceParameters {
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
