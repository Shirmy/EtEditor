package com.eteditor

import org.json.JSONArray
import org.json.JSONObject

internal data class PersistedEditorToolLists(
    val tools: List<EditorTool>,
    val txtTextReplacePresets: List<EditorTool>,
    val epubTextReplacePresets: List<EditorTool>
)

internal fun normalizedTxtTextReplacePresetForImport(
    tool: EditorTool,
    defaultParameters: Map<String, String>,
    cleanPresetOverrides: (String, String, Boolean, String) -> Map<String, String>
): EditorTool? {
    if (tool.toolId != "text_replace") return null
    val values = defaultParameters + tool.parameterOverrides
    val find = values[TEXT_REPLACE_PARAM_FIND].orEmpty()
    if (find.isEmpty()) return null
    val replace = values[TEXT_REPLACE_PARAM_REPLACE].orEmpty()
    val regex = values[TEXT_REPLACE_PARAM_FIND_REGEX] == BOOL_TRUE
    val scope = values[TEXT_REPLACE_PARAM_SCOPE].orEmpty()
    return tool.copy(
        toolId = "text_replace",
        parameterOverrides = cleanPresetOverrides(find, replace, regex, scope)
    )
}

internal fun normalizedEpubTextReplacePresetForImport(
    tool: EditorTool,
    defaultParameters: Map<String, String>,
    cleanPresetOverrides: (String, String, Boolean, String, String) -> Map<String, String>
): EditorTool? {
    if (tool.toolId != "text_replace") return null
    val values = defaultParameters + tool.parameterOverrides
    val mode = values[TEXT_REPLACE_PARAM_MODE].orEmpty()
        .ifBlank { TEXT_REPLACE_MODE_SINGLE }
    val batchSource = values[TEXT_REPLACE_PARAM_BATCH_SOURCE].orEmpty()
    if (mode != TEXT_REPLACE_MODE_SINGLE ||
        batchSource == TEXT_REPLACE_BATCH_FILE
    ) {
        return null
    }
    val find = values[TEXT_REPLACE_PARAM_FIND].orEmpty()
    if (find.isEmpty()) return null
    val replace = values[TEXT_REPLACE_PARAM_REPLACE].orEmpty()
    val regex = values[TEXT_REPLACE_PARAM_FIND_REGEX] == BOOL_TRUE
    val scope = values[TEXT_REPLACE_PARAM_SCOPE].orEmpty()
    val target = values[TEXT_REPLACE_PARAM_TARGET]
        ?.takeIf {
            it == TEXT_REPLACE_TARGET_VISIBLE ||
                it == TEXT_REPLACE_TARGET_SOURCE
        }
        ?: TEXT_REPLACE_TARGET_SOURCE
    return tool.copy(
        toolId = "text_replace",
        parameterOverrides = cleanPresetOverrides(find, replace, regex, scope, target)
    )
}

internal fun exportToolParameterOverrides(
    toolId: String,
    overrides: Map<String, String>,
    includeSensitive: Boolean,
    sensitiveParameterKeys: Set<String>,
    cleanParameterOverrides: (String, Map<String, String>) -> Map<String, String>
): Map<String, String> {
    val cleaned = cleanParameterOverrides(toolId, overrides)
    return if (includeSensitive) {
        cleaned
    } else {
        cleaned.filterKeys { key -> key !in sensitiveParameterKeys }
    }
}

internal fun builtInToolDefaultsToJson(
    savedDefaults: Map<String, Map<String, String>>,
    includeSensitive: Boolean,
    sensitiveParameterKeys: Set<String>,
    cleanOverridesForSave: (String, Map<String, String>) -> Map<String, String>
): JSONObject {
    val root = JSONObject()
    savedDefaults.forEach { (toolId, overrides) ->
        val cleaned = cleanOverridesForSave(toolId, overrides)
        val exported = if (includeSensitive) {
            cleaned
        } else {
            cleaned.filterKeys { key -> key !in sensitiveParameterKeys }
        }
        if (exported.isNotEmpty()) {
            root.put(
                toolId,
                JSONObject().also { params ->
                    exported.forEach { (key, value) -> params.put(key, value) }
                }
            )
        }
    }
    return root
}

internal fun builtInToolDefaultsStoreJson(
    savedDefaults: Map<String, Map<String, String>>,
    cleanParameterOverrides: (String, Map<String, String>) -> Map<String, String>
): JSONObject {
    return builtInToolDefaultsToJson(
        savedDefaults = savedDefaults,
        includeSensitive = false,
        sensitiveParameterKeys = SENSITIVE_PARAMETER_KEYS,
        cleanOverridesForSave = cleanParameterOverrides
    )
}

internal fun parseBuiltInToolDefaultOverridesJson(
    root: JSONObject?,
    includeSensitive: Boolean,
    allowedToolIds: Set<String>,
    sensitiveParameterKeys: Set<String>,
    cleanOverridesForSave: (String, Map<String, String>) -> Map<String, String>
): Map<String, Map<String, String>> {
    if (root == null) return emptyMap()
    val result = mutableMapOf<String, Map<String, String>>()
    val iterator = root.keys()
    while (iterator.hasNext()) {
        val toolId = iterator.next()
        if (toolId !in allowedToolIds) continue
        val rawOverrides = root.optJSONObject(toolId).toStringMap()
        val importOverrides = if (includeSensitive) {
            rawOverrides
        } else {
            rawOverrides.filterKeys { key -> key !in sensitiveParameterKeys }
        }
        val cleaned = cleanOverridesForSave(toolId, importOverrides)
        if (cleaned.isNotEmpty()) result[toolId] = cleaned
    }
    return result
}

internal fun parseBuiltInToolDefaultsStoreJson(
    rawDefaults: String,
    allowedToolIds: Set<String>,
    sensitiveParameterKeys: Set<String>,
    cleanOverridesForSave: (String, Map<String, String>) -> Map<String, String>
): Map<String, Map<String, String>> {
    if (rawDefaults.isBlank()) return emptyMap()
    return runCatching {
        parseBuiltInToolDefaultOverridesJson(
            root = JSONObject(rawDefaults),
            includeSensitive = false,
            allowedToolIds = allowedToolIds,
            sensitiveParameterKeys = sensitiveParameterKeys,
            cleanOverridesForSave = cleanOverridesForSave
        )
    }.getOrElse { emptyMap() }
}

internal fun builtInToolDefaultsStoreJsonContainsSensitiveParameters(
    rawDefaults: String,
    sensitiveParameterKeys: Set<String>
): Boolean {
    if (rawDefaults.isBlank() || sensitiveParameterKeys.isEmpty()) return false
    return runCatching {
        val root = JSONObject(rawDefaults)
        val iterator = root.keys()
        while (iterator.hasNext()) {
            val params = root.optJSONObject(iterator.next())
            if (params != null && params.containsAnyKey(sensitiveParameterKeys)) return@runCatching true
        }
        false
    }.getOrElse { false }
}

internal fun parseEditorToolsJson(
    array: JSONArray?,
    allowedToolIds: Set<String>,
    includeSensitive: Boolean,
    exportParameterOverrides: (String, Map<String, String>, Boolean) -> Map<String, String>,
    normalize: (EditorTool) -> EditorTool? = { it }
): List<EditorTool>? {
    if (array == null) return null
    return buildList {
        for (index in 0 until array.length()) {
            parseEditorToolJson(
                json = array.optJSONObject(index),
                allowedToolIds = allowedToolIds,
                includeSensitive = includeSensitive,
                exportParameterOverrides = exportParameterOverrides
            )
                ?.let(normalize)
                ?.let(::add)
        }
    }
}

private fun parseEditorToolJson(
    json: JSONObject?,
    allowedToolIds: Set<String>,
    includeSensitive: Boolean,
    exportParameterOverrides: (String, Map<String, String>, Boolean) -> Map<String, String>
): EditorTool? {
    if (json == null) return null
    val id = json.optString("id").ifBlank { return null }
    val name = json.optString("name").ifBlank { return null }
    val toolId = json.optString("toolId").takeIf { it in allowedToolIds } ?: return null
    return EditorTool(
        id = id,
        name = name,
        group = json.optString("group").trim(),
        toolId = toolId,
        parameterOverrides = exportParameterOverrides(
            toolId,
            json.optJSONObject("parameterOverrides").toStringMap(),
            includeSensitive
        )
    )
}

internal fun parsePersistedEditorToolLists(
    rawTools: String,
    rawTxtPresets: String,
    rawEpubPresets: String,
    allowedToolIds: Set<String>,
    exportParameterOverrides: (String, Map<String, String>, Boolean) -> Map<String, String>,
    normalizeTxtPreset: (EditorTool) -> EditorTool?,
    normalizeEpubPreset: (EditorTool) -> EditorTool?
): PersistedEditorToolLists {
    return PersistedEditorToolLists(
        tools = parsePersistedEditorToolList(
            raw = rawTools,
            allowedToolIds = allowedToolIds,
            exportParameterOverrides = exportParameterOverrides
        ),
        txtTextReplacePresets = parsePersistedEditorToolList(
            raw = rawTxtPresets,
            allowedToolIds = allowedToolIds,
            exportParameterOverrides = exportParameterOverrides,
            normalize = normalizeTxtPreset
        ),
        epubTextReplacePresets = parsePersistedEditorToolList(
            raw = rawEpubPresets,
            allowedToolIds = allowedToolIds,
            exportParameterOverrides = exportParameterOverrides,
            normalize = normalizeEpubPreset
        )
    )
}

private fun parsePersistedEditorToolList(
    raw: String,
    allowedToolIds: Set<String>,
    exportParameterOverrides: (String, Map<String, String>, Boolean) -> Map<String, String>,
    normalize: (EditorTool) -> EditorTool? = { it }
): List<EditorTool> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        parseEditorToolsJson(
            array = JSONArray(raw),
            allowedToolIds = allowedToolIds,
            includeSensitive = false,
            exportParameterOverrides = exportParameterOverrides,
            normalize = normalize
        ).orEmpty()
    }.getOrElse { emptyList() }
}

internal fun editorToolListStoreJsonContainsSensitiveParameters(
    rawTools: String,
    sensitiveParameterKeys: Set<String>
): Boolean {
    if (rawTools.isBlank() || sensitiveParameterKeys.isEmpty()) return false
    return runCatching {
        val array = JSONArray(rawTools)
        for (index in 0 until array.length()) {
            val params = array.optJSONObject(index)?.optJSONObject("parameterOverrides")
            if (params != null && params.containsAnyKey(sensitiveParameterKeys)) return@runCatching true
        }
        false
    }.getOrElse { false }
}

internal fun editorToolsToJsonArray(
    tools: List<EditorTool>,
    includeSensitive: Boolean,
    exportParameterOverrides: (String, Map<String, String>, Boolean) -> Map<String, String>
): JSONArray {
    val array = JSONArray()
    tools.forEach { tool ->
        array.put(editorToolToJson(tool, includeSensitive, exportParameterOverrides))
    }
    return array
}

private fun editorToolToJson(
    tool: EditorTool,
    includeSensitive: Boolean,
    exportParameterOverrides: (String, Map<String, String>, Boolean) -> Map<String, String>
): JSONObject {
    return JSONObject()
        .put("id", tool.id)
        .put("name", tool.name)
        .put("group", tool.group)
        .put("toolId", tool.toolId)
        .put("parameterOverrides", JSONObject().also { params ->
            exportParameterOverrides(tool.toolId, tool.parameterOverrides, includeSensitive)
                .forEach { (key, value) -> params.put(key, value) }
        })
}

internal fun editorToolListStoreJson(
    tools: List<EditorTool>,
    includeSensitive: Boolean,
    exportParameterOverrides: (String, Map<String, String>, Boolean) -> Map<String, String>
): JSONArray {
    return editorToolsToJsonArray(
        tools = tools,
        includeSensitive = includeSensitive,
        exportParameterOverrides = exportParameterOverrides
    )
}

private fun JSONObject.containsAnyKey(targetKeys: Set<String>): Boolean {
    val iterator = keys()
    while (iterator.hasNext()) {
        if (iterator.next() in targetKeys) return true
    }
    return false
}

internal fun Map<String, String>.withoutSensitiveParameters(): Map<String, String> {
    return filterKeys { key -> key !in SENSITIVE_PARAMETER_KEYS }
}
