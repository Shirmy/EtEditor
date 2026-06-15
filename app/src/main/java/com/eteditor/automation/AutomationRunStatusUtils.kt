package com.eteditor

internal data class AutomationRunTerminalCounts(
    val executed: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0
)

internal fun initialAutomationRunStepStatuses(chain: AutomationChain): Map<String, AutomationRunStepStatus> {
    return chain.steps.associate { step ->
        step.id to AutomationRunStepStatus(stepId = step.id)
    }
}

internal fun updatedAutomationRunStepStatuses(
    statuses: Map<String, AutomationRunStepStatus>,
    step: AutomationStep,
    state: AutomationRunStepState,
    message: String = ""
): Map<String, AutomationRunStepStatus> {
    val previous = statuses[step.id]
    val keepProgress = state == AutomationRunStepState.Running ||
        state == AutomationRunStepState.NeedsConfirmation
    return statuses + (
        step.id to AutomationRunStepStatus(
            stepId = step.id,
            state = state,
            message = message,
            progress = previous?.progress?.takeIf { keepProgress },
            progressText = if (keepProgress) previous?.progressText.orEmpty() else ""
        )
    )
}

internal fun updatedAutomationRunStepProgress(
    statuses: Map<String, AutomationRunStepStatus>,
    step: AutomationStep,
    progress: Float?,
    progressText: String
): Map<String, AutomationRunStepStatus> {
    val previous = statuses[step.id] ?: AutomationRunStepStatus(stepId = step.id)
    return statuses + (
        step.id to previous.copy(
            progress = progress?.coerceIn(0f, 1f),
            progressText = progressText
        )
    )
}

internal fun updatedAutomationRunTerminalCounts(
    counts: AutomationRunTerminalCounts,
    state: AutomationRunStepState
): AutomationRunTerminalCounts {
    return when (state) {
        AutomationRunStepState.Completed -> counts.copy(executed = counts.executed + 1)
        AutomationRunStepState.Skipped -> counts.copy(skipped = counts.skipped + 1)
        AutomationRunStepState.Failed -> counts.copy(failed = counts.failed + 1)
        else -> counts
    }
}

internal fun automationTerminalStateForSuccessMessage(message: String): AutomationRunStepState {
    return if (automationStatusMeansSkipped(message)) {
        AutomationRunStepState.Skipped
    } else {
        AutomationRunStepState.Completed
    }
}

internal fun automationTerminalStateForFailureMessage(message: String): AutomationRunStepState {
    return if (automationStatusMeansSkipped(message)) {
        AutomationRunStepState.Skipped
    } else {
        AutomationRunStepState.Failed
    }
}

internal fun automationStatusMeansSkipped(message: String): Boolean {
    val text = message.trim()
    if (text.isBlank()) return false
    return listOf(
        "无需修改",
        "没有匹配内容",
        "没有对应的标签",
        "没有启用",
        "没有可读取",
        "没有可处理",
        "无匹配",
        "修改 0",
        "已跳过"
    ).any { marker -> text.contains(marker) }
}

internal fun automationRunTerminalLogPrefix(state: AutomationRunStepState): String {
    return when (state) {
        AutomationRunStepState.Completed -> "完成"
        AutomationRunStepState.Skipped -> "跳过"
        AutomationRunStepState.Failed -> "失败"
        else -> "完成"
    }
}
