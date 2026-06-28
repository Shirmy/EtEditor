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
    // 运行中点了"停止"：置位后，执行循环在跑完当前这一步、开始下一步之前会停下。
    var automationRunStopRequested by mutableStateOf(false)
    // 本次运行是被"停止"结束的（而非自然跑完）：用于让运行视图判定为已结束、可离开。
    var automationRunStopped by mutableStateOf(false)
    // 同一次自动化运行内，按 source 缓存已认好的书的详情页地址（resolvedUrl）。
    // 后续同源 fetch_info 步骤直接复用，跳过搜索认书，避免同一本书被反复认。
    var fetchInfoRunResolvedUrls by mutableStateOf<Map<String, String>>(emptyMap())
    var draftAutomationChain by mutableStateOf<AutomationChain?>(null)
    var draftAutomationChainPreviousSelectionId: String = ""
    var nextAutomationChainNumber = 2
    var nextAutomationStepNumber = 1
}
