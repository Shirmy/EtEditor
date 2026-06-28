package com.eteditor

internal fun EditorController.resetAutomationRunStepStatuses(chain: AutomationChain) {
    automationRunStepStatuses = initialAutomationRunStepStatuses(chain)
}

internal fun EditorController.setAutomationRunStepState(
    step: AutomationStep,
    state: AutomationRunStepState,
    message: String = ""
) {
    automationRunStepStatuses = updatedAutomationRunStepStatuses(
        statuses = automationRunStepStatuses,
        step = step,
        state = state,
        message = message
    )
    if (!state.keepsBodyAutomationProgress()) {
        clearBodyOperationProgress()
    }
}

internal fun EditorController.setAutomationRunStepProgress(
    step: AutomationStep,
    progress: Float?,
    progressText: String
) {
    automationRunStepStatuses = updatedAutomationRunStepProgress(
        statuses = automationRunStepStatuses,
        step = step,
        progress = progress,
        progressText = progressText
    )
}

internal fun EditorController.automationRunTerminalStateForSuccess(): AutomationRunStepState {
    return automationTerminalStateForSuccessMessage(statusMessage)
}

internal fun EditorController.automationRunTerminalStateForFailure(): AutomationRunStepState {
    return automationTerminalStateForFailureMessage(statusMessage)
}

internal fun EditorController.countAutomationRunTerminalState(state: AutomationRunStepState) {
    val counts = updatedAutomationRunTerminalCounts(
        counts = AutomationRunTerminalCounts(
            executed = automationRunExecuted,
            skipped = automationRunSkipped,
            failed = automationRunFailed
        ),
        state = state
    )
    automationRunExecuted = counts.executed
    automationRunSkipped = counts.skipped
    automationRunFailed = counts.failed
}

internal fun EditorController.automationStepHasPreviewEnabled(tool: EditorTool): Boolean {
    return when (tool.toolId) {
        "file_rename" -> fileRenameParameters(tool).preview
        "text_replace" -> textReplaceParameters(tool).preview
        "chapter_title_rename" -> titleRenameParameters(tool).preview
        "title_format" -> titleFormatParameters(tool).preview
        "insert_chapter" -> insertChapterParameters(tool).preview
        "generate_cover" -> coverParameters(tool).preview
        else -> false
    }
}

internal fun EditorController.resetAutomationRunRuntimeState() {
    automationConfirmationRequest = null
    automationRunStopRequested = false
    automationRunStopped = false
    automationRunStepStatuses = emptyMap()
    automationRunExecuted = 0
    automationRunSkipped = 0
    automationRunFailed = 0
    fetchInfoRunResolvedUrls = emptyMap()
    clearBodyOperationProgress()
}

internal fun EditorController.resetAutomationPendingPreviewState() {
    automationPendingPreviewToolId = null
    automationPendingPreviewDataId = null
    automationPendingPreviewLabel = null
}

private fun AutomationRunStepState.keepsBodyAutomationProgress(): Boolean {
    return this == AutomationRunStepState.Running ||
        this == AutomationRunStepState.NeedsConfirmation ||
        this == AutomationRunStepState.Confirmed
}
