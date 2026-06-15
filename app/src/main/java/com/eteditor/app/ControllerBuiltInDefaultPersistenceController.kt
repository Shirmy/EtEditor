package com.eteditor

import org.json.JSONObject

internal fun EditorController.loadPersistedBuiltInToolDefaults() {
    val rawDefaults = jsonPreferences.readBuiltInToolDefaults()
    if (rawDefaults.isBlank()) return
    val containsSensitiveDefaults = builtInToolDefaultsStoreJsonContainsSensitiveParameters(
        rawDefaults = rawDefaults,
        sensitiveParameterKeys = SENSITIVE_PARAMETER_KEYS
    )

    savedBuiltInDefaultOverrides = parseBuiltInToolDefaultsStoreJson(
        rawDefaults = rawDefaults,
        allowedToolIds = availableToolIds(),
        sensitiveParameterKeys = SENSITIVE_PARAMETER_KEYS,
        cleanOverridesForSave = { toolId, overrides ->
            cleanBuiltInDefaultOverridesForSave(toolId, overrides)
        }
    )
    if (containsSensitiveDefaults) persistBuiltInToolDefaults()
}

internal fun EditorController.persistBuiltInToolDefaults() {
    jsonPreferences.writeBuiltInToolDefaults(builtInDefaultsToJson(includeSensitive = false).toString())
}

internal fun EditorController.builtInDefaultsToJson(includeSensitive: Boolean): JSONObject {
    return builtInToolDefaultsToJson(
        savedDefaults = savedBuiltInDefaultOverrides,
        includeSensitive = includeSensitive,
        sensitiveParameterKeys = SENSITIVE_PARAMETER_KEYS,
        cleanOverridesForSave = { toolId, overrides ->
            cleanBuiltInDefaultOverridesForSave(toolId, overrides)
        }
    )
}

internal fun EditorController.parseBuiltInDefaultOverrides(
    root: JSONObject?,
    includeSensitive: Boolean = false
): Map<String, Map<String, String>> {
    return parseBuiltInToolDefaultOverridesJson(
        root = root,
        includeSensitive = includeSensitive,
        allowedToolIds = availableToolIds(),
        sensitiveParameterKeys = SENSITIVE_PARAMETER_KEYS,
        cleanOverridesForSave = { toolId, overrides ->
            cleanBuiltInDefaultOverridesForSave(toolId, overrides)
        }
    )
}

internal fun EditorController.replaceBuiltInDefaultsForImport(importedDefaults: Map<String, Map<String, String>>) {
    savedBuiltInDefaultOverrides = importedDefaults
    persistBuiltInToolDefaults()
    resetBuiltInToolState()
}

private fun EditorController.availableToolIds(): Set<String> {
    return availableTools.mapTo(mutableSetOf()) { it.id }
}
