package com.eteditor

internal fun updateBuiltInToolOverrides(
    currentOverrides: Map<String, Map<String, String>>,
    toolId: String,
    overrides: Map<String, String>
): Map<String, Map<String, String>> {
    return currentOverrides.toMutableMap().also { next ->
        if (overrides.isEmpty()) {
            next.remove(toolId)
        } else {
            next[toolId] = overrides
        }
    }
}

internal fun saveBuiltInDefaultParameterOverrides(
    savedDefaults: Map<String, Map<String, String>>,
    toolId: String,
    overrides: Map<String, String>,
    cleanOverridesForSave: (String, Map<String, String>) -> Map<String, String>
): Map<String, Map<String, String>> {
    return updateBuiltInToolOverrides(
        currentOverrides = savedDefaults,
        toolId = toolId,
        overrides = cleanOverridesForSave(toolId, overrides)
    )
}

internal fun removeBuiltInDefaultParameterOverrides(
    savedDefaults: Map<String, Map<String, String>>,
    toolId: String
): Map<String, Map<String, String>> {
    return savedDefaults.toMutableMap().also { it.remove(toolId) }
}

internal fun resetBuiltInParameterOverridesFromDefaults(
    currentOverrides: Map<String, Map<String, String>>,
    savedDefaults: Map<String, Map<String, String>>,
    toolId: String?
): Map<String, Map<String, String>> {
    if (toolId == null) return savedDefaults
    return updateBuiltInToolOverrides(
        currentOverrides = currentOverrides,
        toolId = toolId,
        overrides = savedDefaults[toolId].orEmpty()
    )
}
