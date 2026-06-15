package com.eteditor

import org.json.JSONArray

internal fun EditorController.loadPersistedEditorTools() {
    val rawStores = jsonPreferences.readEditorToolStores()
    val toolsContainSensitiveParameters = editorToolListStoreJsonContainsSensitiveParameters(
        rawTools = rawStores.tools,
        sensitiveParameterKeys = SENSITIVE_PARAMETER_KEYS
    )
    val txtPresetsContainSensitiveParameters = editorToolListStoreJsonContainsSensitiveParameters(
        rawTools = rawStores.txtTextReplacePresets,
        sensitiveParameterKeys = SENSITIVE_PARAMETER_KEYS
    )
    val epubPresetsContainSensitiveParameters = editorToolListStoreJsonContainsSensitiveParameters(
        rawTools = rawStores.epubTextReplacePresets,
        sensitiveParameterKeys = SENSITIVE_PARAMETER_KEYS
    )
    val loaded = parsePersistedEditorToolLists(
        rawTools = rawStores.tools,
        rawTxtPresets = rawStores.txtTextReplacePresets,
        rawEpubPresets = rawStores.epubTextReplacePresets,
        allowedToolIds = EDITOR_TOOL_IDS,
        exportParameterOverrides = { toolId, overrides, includeSensitive ->
            exportParameterOverrides(toolId, overrides, includeSensitive)
        },
        normalizeTxtPreset = { tool -> normalizedTxtTextReplacePreset(tool) },
        normalizeEpubPreset = { tool -> normalizedEpubTextReplacePreset(tool) }
    )
    editorTools = loaded.tools
    txtTextReplacePresets = assignUniqueEditorToolIds(
        importedTools = loaded.txtTextReplacePresets,
        existingTools = emptyList(),
        idPrefix = "txt-search-",
        normalize = { tool -> normalizedTxtTextReplacePreset(tool) }
    )
    epubTextReplacePresets = assignUniqueEditorToolIds(
        importedTools = loaded.epubTextReplacePresets,
        existingTools = emptyList(),
        idPrefix = "epub-search-",
        normalize = { tool -> normalizedEpubTextReplacePreset(tool) }
    )
    syncEditorToolCounter()
    syncTxtTextReplacePresetCounter()
    syncEpubTextReplacePresetCounter()
    if (toolsContainSensitiveParameters) persistEditorTools()
    if (txtPresetsContainSensitiveParameters) persistTxtTextReplacePresets()
    if (epubPresetsContainSensitiveParameters) persistEpubTextReplacePresets()
}

internal fun EditorController.persistTxtTextReplacePresets(): Boolean {
    return jsonPreferences.writeTxtTextReplacePresets(editorToolsStoreJson(txtTextReplacePresets))
}

internal fun EditorController.persistEpubTextReplacePresets(): Boolean {
    return jsonPreferences.writeEpubTextReplacePresets(editorToolsStoreJson(epubTextReplacePresets))
}

internal fun EditorController.persistEditorTools(): Boolean {
    return jsonPreferences.writeEditorTools(editorToolsStoreJson(editorTools))
}

private fun EditorController.editorToolsStoreJson(tools: List<EditorTool>): String {
    return editorToolListStoreJson(
        tools = tools,
        includeSensitive = false,
        exportParameterOverrides = { toolId, overrides, includeSensitive ->
            exportParameterOverrides(toolId, overrides, includeSensitive)
        }
    ).toString()
}

internal fun EditorController.parseEditorTools(
    array: JSONArray?,
    includeSensitive: Boolean = false
): List<EditorTool>? {
    return parseEditorToolsJson(
        array = array,
        allowedToolIds = EDITOR_TOOL_IDS,
        includeSensitive = includeSensitive,
        exportParameterOverrides = { toolId, overrides, includeSensitive ->
            exportParameterOverrides(toolId, overrides, includeSensitive)
        }
    )
}

internal fun EditorController.parseTxtTextReplacePresets(
    array: JSONArray?,
    includeSensitive: Boolean = false
): List<EditorTool>? {
    return parseEditorToolsJson(
        array = array,
        allowedToolIds = EDITOR_TOOL_IDS,
        includeSensitive = includeSensitive,
        exportParameterOverrides = { toolId, overrides, includeSensitive ->
            exportParameterOverrides(toolId, overrides, includeSensitive)
        },
        normalize = { tool -> normalizedTxtTextReplacePreset(tool) }
    )
}

internal fun EditorController.parseEpubTextReplacePresets(
    array: JSONArray?,
    includeSensitive: Boolean = false
): List<EditorTool>? {
    return parseEditorToolsJson(
        array = array,
        allowedToolIds = EDITOR_TOOL_IDS,
        includeSensitive = includeSensitive,
        exportParameterOverrides = { toolId, overrides, includeSensitive ->
            exportParameterOverrides(toolId, overrides, includeSensitive)
        },
        normalize = { tool -> normalizedEpubTextReplacePreset(tool) }
    )
}

internal fun EditorController.editorToolsToJson(
    tools: List<EditorTool>,
    includeSensitive: Boolean = false
): JSONArray {
    return editorToolsToJsonArray(
        tools = tools,
        includeSensitive = includeSensitive,
        exportParameterOverrides = { toolId, overrides, includeSensitive ->
            exportParameterOverrides(toolId, overrides, includeSensitive)
        }
    )
}

internal fun EditorController.replaceEditorToolsForImport(
    replaceToolIds: Set<String>,
    importedTools: List<EditorTool>
) {
    val result = replaceEditorToolPresetsForImport(
        currentTools = editorTools,
        selectedToolId = selectedEditorToolId,
        draftTool = draftEditorTool,
        replaceToolIds = replaceToolIds,
        importedTools = importedTools
    )
    editorTools = result.tools
    selectedEditorToolId = result.selectedToolId
    draftEditorTool = result.draftTool
    syncEditorToolCounter()
    persistEditorTools()
}

internal fun EditorController.replaceTxtTextReplacePresetsForImport(importedPresets: List<EditorTool>) {
    txtTextReplacePresets = replaceTextReplacePresetsForImport(
        importedPresets = importedPresets,
        idPrefix = "txt-search-",
        normalize = { tool -> normalizedTxtTextReplacePreset(tool) }
    )
    syncTxtTextReplacePresetCounter()
    persistTxtTextReplacePresets()
}

internal fun EditorController.replaceEpubTextReplacePresetsForImport(importedPresets: List<EditorTool>) {
    epubTextReplacePresets = replaceTextReplacePresetsForImport(
        importedPresets = importedPresets,
        idPrefix = "epub-search-",
        normalize = { tool -> normalizedEpubTextReplacePreset(tool) }
    )
    syncEpubTextReplacePresetCounter()
    persistEpubTextReplacePresets()
}

private fun EditorController.normalizedTxtTextReplacePreset(tool: EditorTool): EditorTool? {
    return normalizedTxtTextReplacePresetForImport(
        tool = tool,
        defaultParameters = defaultToolParameters("text_replace"),
        cleanPresetOverrides = { find, replace, regex, scope ->
            cleanTxtTextReplacePresetOverrides(find, replace, regex, scope)
        }
    )
}

private fun EditorController.normalizedEpubTextReplacePreset(tool: EditorTool): EditorTool? {
    return normalizedEpubTextReplacePresetForImport(
        tool = tool,
        defaultParameters = defaultToolParameters("text_replace"),
        cleanPresetOverrides = { find, replace, regex, scope, target ->
            cleanEpubTextReplacePresetOverrides(find, replace, regex, scope, target)
        }
    )
}

internal fun EditorController.syncEditorToolCounter() {
    nextEditorToolNumber = nextEditorToolNumberForPrefix(editorTools, "tool-")
}

private fun EditorController.syncTxtTextReplacePresetCounter() {
    nextTxtTextReplacePresetNumber = nextEditorToolNumberForPrefix(txtTextReplacePresets, "txt-search-")
}

private fun EditorController.syncEpubTextReplacePresetCounter() {
    nextEpubTextReplacePresetNumber = nextEditorToolNumberForPrefix(epubTextReplacePresets, "epub-search-")
}

internal fun EditorController.exportParameterOverrides(
    toolId: String,
    overrides: Map<String, String>,
    includeSensitive: Boolean
): Map<String, String> {
    return exportToolParameterOverrides(
        toolId = toolId,
        overrides = overrides,
        includeSensitive = includeSensitive,
        sensitiveParameterKeys = SENSITIVE_PARAMETER_KEYS,
        cleanParameterOverrides = { cleanToolId, cleanOverrides ->
            cleanParameterOverrides(cleanToolId, cleanOverrides)
        }
    )
}
