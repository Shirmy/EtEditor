package com.eteditor

internal fun mergedEditorToolParameters(
    tool: EditorTool,
    toolsById: Map<String, ToolDefinition>
): Map<String, String> {
    val defaults = defaultToolParameters(toolsById[tool.toolId])
    val editableKeys = toolsById[tool.toolId]
        ?.parameters
        .orEmpty()
        .map { it.key }
        .toSet()
    return defaults + tool.parameterOverrides.filterKeys { it in editableKeys }
}

internal fun builtInEditorToolModel(
    toolId: String,
    planId: String,
    label: String,
    parameterOverrides: Map<String, String>
): EditorTool {
    return EditorTool(
        id = planId,
        name = label,
        toolId = toolId,
        parameterOverrides = parameterOverrides
    )
}

internal fun automationStepAsEditorToolModel(
    step: AutomationStep,
    toolLabel: String
): EditorTool {
    return EditorTool(
        id = step.id,
        name = step.name.ifBlank { toolLabel },
        toolId = step.toolId,
        parameterOverrides = step.parameterOverrides
    )
}

internal fun automationStepSourceToolModel(
    step: AutomationStep,
    editorTools: List<EditorTool>,
    toolLabel: String
): EditorTool? {
    return step.presetId
        .takeIf { it.isNotBlank() }
        ?.let { presetId -> editorTools.firstOrNull { it.id == presetId } }
        ?: step.takeIf { it.presetId.isBlank() }?.let {
            automationStepAsEditorToolModel(step, toolLabel)
        }
}

internal fun automationStepToolForRunModel(
    step: AutomationStep,
    editorTools: List<EditorTool>,
    toolLabel: String
): EditorTool? {
    return automationStepSourceToolModel(step, editorTools, toolLabel)?.copy(id = step.id)
}
