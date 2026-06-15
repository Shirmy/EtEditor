package com.eteditor

data class AutomationStep(
    val id: String,
    val name: String = "",
    val toolId: String,
    val parameterOverrides: Map<String, String> = emptyMap(),
    val presetId: String = ""
)

data class AutomationChain(
    val id: String,
    val name: String,
    val group: String = "",
    val steps: List<AutomationStep>
)

data class AutomationConfirmationRequest(
    val chainId: String,
    val stepIndex: Int,
    val stepId: String,
    val toolId: String,
    val label: String
)

enum class AutomationRunStepState {
    Waiting,
    Running,
    NeedsUpload,
    UploadedPendingExecution,
    NeedsConfirmation,
    Confirmed,
    Completed,
    Skipped,
    Failed
}

data class AutomationRunStepStatus(
    val stepId: String,
    val state: AutomationRunStepState = AutomationRunStepState.Waiting,
    val message: String = "",
    val progress: Float? = null,
    val progressText: String = ""
)
