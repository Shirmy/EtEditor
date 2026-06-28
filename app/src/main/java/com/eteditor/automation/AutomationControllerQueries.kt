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

// 选中执行链是否已"跑完"：没有待确认请求，且所有步骤都到达终态（完成/跳过/失败）。
// 用于判断运行视图里任务是否还在进行中——跑完后才允许点目录离开自动化面板。
fun EditorController.isSelectedAutomationRunFinished(): Boolean {
    if (automationConfirmationRequest != null) return false
    if (automationRunStopped) return true
    val chain = selectedAutomationChain ?: return false
    if (chain.steps.isEmpty()) return false
    return chain.steps.all { step ->
        when (automationRunStepStatuses[step.id]?.state) {
            AutomationRunStepState.Completed,
            AutomationRunStepState.Skipped,
            AutomationRunStepState.Failed -> true
            else -> false
        }
    }
}
