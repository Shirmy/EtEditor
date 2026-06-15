package com.eteditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class EditorAutomationState {
    var automationChains by mutableStateOf<List<AutomationChain>>(emptyList())
    var automationChainGroups by mutableStateOf<List<String>>(emptyList())
    var selectedAutomationChainId by mutableStateOf("")
    var automationLog by mutableStateOf<List<String>>(emptyList())
    var automationRunStepStatuses by mutableStateOf<Map<String, AutomationRunStepStatus>>(emptyMap())
    var automationConfirmationRequest by mutableStateOf<AutomationConfirmationRequest?>(null)
    var automationPendingPreviewToolId by mutableStateOf<String?>(null)
    var automationPendingPreviewDataId by mutableStateOf<String?>(null)
    var automationPendingPreviewLabel by mutableStateOf<String?>(null)
    var automationRunExecuted = 0
    var automationRunSkipped = 0
    var automationRunFailed = 0
    var draftAutomationChain by mutableStateOf<AutomationChain?>(null)
    var draftAutomationChainPreviousSelectionId: String = ""
    var nextAutomationChainNumber = 2
    var nextAutomationStepNumber = 1
}
