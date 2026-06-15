package com.eteditor

fun EditorController.automationChainGroupOptions(): List<String> {
    return automationChainGroupOptions(automationChainGroups, automationChains)
}

fun EditorController.automationStepParameterValue(step: AutomationStep, key: String): String {
    val toolsById = availableTools.associateBy { it.id }
    val tool = automationStepToolForRunModel(step, editorTools, toolLabel(step.toolId)) ?: return ""
    return mergedEditorToolParameters(tool, toolsById)[key].orEmpty()
}

fun EditorController.automationStepLabel(step: AutomationStep): String {
    val toolsById = availableTools.associateBy { it.id }
    val tool = automationStepSourceToolModel(step, editorTools, toolLabel(step.toolId))
    if (tool != null) return tool.name.ifBlank { editorToolBaseLabel(tool) }
    if (step.presetId.isNotBlank()) return "\u9884\u8bbe\u5df2\u5220\u9664"
    val definition = toolsById[step.toolId] ?: return "\u7f3a\u5931\u529f\u80fd"
    return step.name.ifBlank { definition.title }
}

fun EditorController.automationRunStepStatus(step: AutomationStep): AutomationRunStepStatus {
    return automationRunStepStatuses[step.id]
        ?: AutomationRunStepStatus(stepId = step.id)
}

fun EditorController.automationConfirmationStep(request: AutomationConfirmationRequest): AutomationStep? {
    return automationConfirmationStepForRequest(automationChains, request)
}

fun EditorController.automationConfirmationState(request: AutomationConfirmationRequest): AutomationRunStepState? {
    val step = automationConfirmationStep(request) ?: return null
    return automationRunStepStatuses[step.id]?.state
}
