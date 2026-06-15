package com.eteditor

internal fun assignUniqueEditorToolIds(
    importedTools: List<EditorTool>,
    existingTools: List<EditorTool>,
    idPrefix: String,
    normalize: (EditorTool) -> EditorTool? = { it }
): List<EditorTool> {
    val usedIds = existingTools.mapTo(mutableSetOf()) { tool -> tool.id }
    var nextIdNumber = nextEditorToolNumberForPrefix(existingTools, idPrefix)

    fun nextId(): String {
        while (true) {
            val candidate = "$idPrefix${nextIdNumber++}"
            if (usedIds.add(candidate)) return candidate
        }
    }

    return importedTools.mapNotNull(normalize).map { tool ->
        val id = tool.id.takeIf { value -> value.isNotBlank() && usedIds.add(value) } ?: nextId()
        tool.copy(id = id)
    }
}

internal fun nextEditorToolNumberForPrefix(
    tools: List<EditorTool>,
    idPrefix: String
): Int {
    return ((tools.mapNotNull { tool ->
        tool.id.removePrefix(idPrefix).toIntOrNull()
    }.maxOrNull() ?: 0) + 1).coerceAtLeast(1)
}
