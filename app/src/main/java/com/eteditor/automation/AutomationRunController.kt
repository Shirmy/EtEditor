package com.eteditor

import kotlinx.coroutines.delay

private const val AUTO_CHAIN_MISSING = "\u6267\u884c\u94fe\u4e0d\u5b58\u5728"
private const val AUTO_STEP_INVALID = "\u6267\u884c\u6b65\u9aa4\u5df2\u5931\u6548"
private const val AUTO_PREVIEW_READY = "\u9884\u89c8\u5df2\u51c6\u5907"
private const val AUTO_UPLOAD_READY = "已上传文件"
private const val AUTO_RULE_FILE_UPLOAD_READY = "已上传规则文件"
private const val AUTO_WAIT_CONFIRM = "\u7b49\u5f85\u786e\u8ba4"
private const val AUTO_PREVIEW_READY_CONFIRM = "\u9884\u89c8\u5df2\u51c6\u5907\uff0c\u7b49\u5f85\u786e\u8ba4"
private const val AUTO_UPLOAD_FAILED = "\u4e0a\u4f20\u6587\u4ef6\u5931\u8d25"
private const val AUTO_NEEDS_UPLOAD_FILE = "\u9700\u8981\u4e0a\u4f20\u6587\u4ef6"
private const val AUTO_NEEDS_RULE_FILE = "\u9700\u8981\u4e0a\u4f20\u89c4\u5219\u6587\u4ef6"
private const val AUTO_UPLOAD_RULE_FILE_FIRST = "\u8bf7\u4e0a\u4f20\u89c4\u5219\u6587\u4ef6\u540e\u7ee7\u7eed"
private const val AUTO_UPLOAD_IMAGE_FIRST = "\u8bf7\u4e0a\u4f20\u56fe\u7247\u6587\u4ef6\u540e\u7ee7\u7eed"
private const val AUTO_UPLOAD_CONTENT_EMPTY = "\u6ca1\u6709\u53ef\u7528\u7684\u4e0a\u4f20\u5185\u5bb9"
private const val AUTO_UPLOAD_CONTENT_INVALID = "\u4e0a\u4f20\u5185\u5bb9\u65e0\u6548\uff0c\u8bf7\u91cd\u65b0\u4e0a\u4f20"
private const val AUTO_CONFIRMED = "\u5df2\u786e\u8ba4\uff0c\u7ee7\u7eed\u6267\u884c"
private const val AUTO_START = "\u5f00\u59cb\u6267\u884c"
private const val AUTO_CANCELLED = "\u5df2\u53d6\u6d88"
private const val AUTO_TOOL_MISSING = "\u529f\u80fd\u4e0d\u5b58\u5728"

private fun automationLogDivider(label: String): String {
    return "\u2501\u2501 $label \u2501\u2501"
}

private fun automationStepLogDivider(index: Int, label: String): String {
    return automationLogDivider("\u7b2c ${index + 1} \u6b65\uff1a$label")
}

private suspend fun flushAutomationUi() {
    delay(16)
}

private fun automationProgressLabel(phase: String, completed: Int, total: Int): String {
    return countProgressLabel(phase, completed, total)
}

private fun automationProgressValue(completed: Int, total: Int): Float {
    return countProgressFraction(completed, total)
}

private fun EditorController.updateAutomationStepCountProgress(
    step: AutomationStep,
    phase: String,
    completed: Int,
    total: Int
) {
    setAutomationRunStepProgress(
        step = step,
        progress = automationProgressValue(completed, total),
        progressText = automationProgressLabel(phase, completed, total)
    )
}

private suspend fun EditorController.startAutomationStepSingleProgress(step: AutomationStep, phase: String) {
    updateAutomationStepCountProgress(step, phase, 0, 1)
    flushAutomationUi()
    yieldToAppUiBeforeHeavyWork()
}

private suspend fun EditorController.finishAutomationStepSingleProgress(step: AutomationStep, phase: String) {
    updateAutomationStepCountProgress(step, phase, 1, 1)
    flushAutomationUi()
    delay(80)
}

private fun EditorController.coverToolNeedsImageUpload(tool: EditorTool): Boolean {
    val parameters = coverParameters(tool)
    return parameters.mode == COVER_MODE_INSERT ||
        (parameters.mode == COVER_MODE_IMAGE_INSERT && parameters.imageInsertType == COVER_IMAGE_INSERT_CUSTOM)
}

private fun coverToolWithUploadedImage(tool: EditorTool, imageUri: String): EditorTool {
    return tool.copy(
        parameterOverrides = tool.parameterOverrides + (COVER_PARAM_IMAGE_URI to imageUri)
    )
}

suspend fun EditorController.prepareCoverImageUploadForAutomationConfirmation(imageUri: String): Boolean {
    val request = automationConfirmationRequest ?: return false
    val chain = automationChains.firstOrNull { it.id == request.chainId } ?: run {
        automationConfirmationRequest = null
        statusMessage = AUTO_CHAIN_MISSING
        return false
    }
    val step = automationConfirmationStep(request) ?: return false
    if (step.toolId != "generate_cover" || imageUri.isBlank()) return false
    val tool = automationStepToolForRun(step) ?: run {
        automationConfirmationRequest = null
        statusMessage = AUTO_STEP_INVALID
        appendAutomationLog(AUTO_STEP_INVALID)
        return false
    }
    if (!coverToolNeedsImageUpload(tool)) return false
    rememberReadableDocumentUri(appContext, imageUri)
    val uploadedTool = coverToolWithUploadedImage(tool, imageUri)
    if (automationStepHasPreviewEnabled(uploadedTool)) {
        setAutomationRunStepState(step, AutomationRunStepState.Running)
        updateAutomationStepCountProgress(step, "\u52a0\u8f7d\u9884\u89c8", 0, 1)
        flushAutomationUi()
        yieldToAppUiBeforeHeavyWork()
        val prepared = runConfiguredToolAsync(uploadedTool, manual = false)
        updateAutomationStepCountProgress(step, "\u52a0\u8f7d\u9884\u89c8", 1, 1)
        if (!prepared && statusMessage == needsConfirmationMessage() && generatedCoverPreview != null) {
            setAutomationRunStepState(step, AutomationRunStepState.NeedsConfirmation, AUTO_PREVIEW_READY_CONFIRM)
            appendAutomationLog(AUTO_WAIT_CONFIRM)
            statusMessage = needsConfirmationMessage()
            return true
        }
        setAutomationRunStepState(step, AutomationRunStepState.NeedsUpload, statusMessage.ifBlank { AUTO_UPLOAD_FAILED })
        appendAutomationLog(AUTO_UPLOAD_FAILED)
        return false
    }
    setAutomationRunStepState(step, AutomationRunStepState.UploadedPendingExecution, AUTO_UPLOAD_READY)
    appendAutomationLog(AUTO_UPLOAD_READY)
    flushAutomationUi()
    yieldToAppUiBeforeHeavyWork()
    return runUploadedCoverAutomationStep(chain, request, step, imageUri)
}

suspend fun EditorController.prepareInsertChapterUploadForAutomationConfirmation(sourceUri: String): Boolean {
    val request = automationConfirmationRequest ?: return false
    val chain = automationChains.firstOrNull { it.id == request.chainId } ?: run {
        automationConfirmationRequest = null
        statusMessage = AUTO_CHAIN_MISSING
        return false
    }
    val step = automationConfirmationStep(request) ?: return false
    if (step.toolId != "insert_chapter" || sourceUri.isBlank()) return false
    setAutomationRunStepProgress(step, 0.08f, "\u8bfb\u53d6\u6765\u6e90")
    flushAutomationUi()
    yieldToAppUiBeforeHeavyWork()
    val ok = prepareInsertChapterSourcePreview(INSERT_CHAPTER_SOURCE_UPLOAD, sourceUri)
    if (!ok) {
        setAutomationRunStepState(step, AutomationRunStepState.NeedsUpload, statusMessage.ifBlank { AUTO_UPLOAD_FAILED })
        appendAutomationLog(AUTO_UPLOAD_FAILED)
        return false
    }
    setAutomationRunStepProgress(step, 1f, "\u8bfb\u53d6\u6765\u6e90 1/1")
    val previewEnabled = automationStepToolForRun(step)
        ?.let { insertChapterParameters(it).preview } == true
    if (previewEnabled) {
        appendAutomationLog(AUTO_PREVIEW_READY)
        setAutomationRunStepState(step, AutomationRunStepState.NeedsConfirmation, AUTO_PREVIEW_READY_CONFIRM)
        appendAutomationLog(AUTO_WAIT_CONFIRM)
        statusMessage = needsConfirmationMessage()
        return true
    }
    setAutomationRunStepState(step, AutomationRunStepState.UploadedPendingExecution, AUTO_UPLOAD_READY)
    appendAutomationLog(AUTO_UPLOAD_READY)
    flushAutomationUi()
    yieldToAppUiBeforeHeavyWork()
    return runUploadedInsertChapterAutomationStep(chain, request, step, sourceUri)
}

suspend fun EditorController.prepareTextReplaceRuleFileUploadForAutomationConfirmation(ruleFileUri: String): Boolean {
    val request = automationConfirmationRequest ?: return false
    val chain = automationChains.firstOrNull { it.id == request.chainId } ?: run {
        automationConfirmationRequest = null
        statusMessage = AUTO_CHAIN_MISSING
        return false
    }
    val step = automationConfirmationStep(request) ?: return false
    if (step.toolId != "text_replace" || ruleFileUri.isBlank()) return false
    updateTextReplaceRuntimeFile(step.id, ruleFileUri)
    val tool = automationStepToolForRun(step) ?: run {
        automationConfirmationRequest = null
        statusMessage = AUTO_STEP_INVALID
        appendAutomationLog(AUTO_STEP_INVALID)
        return false
    }
    appendAutomationLog(AUTO_RULE_FILE_UPLOAD_READY)
    val previewEnabled = textReplaceParameters(tool).preview
    if (previewEnabled) {
        setAutomationRunStepState(step, AutomationRunStepState.Running)
        updateAutomationStepCountProgress(step, "\u52a0\u8f7d\u9884\u89c8", 0, 1)
        flushAutomationUi()
        yieldToAppUiBeforeHeavyWork()
        val ready = runCatching {
            prepareReplacementFilePreviewAsync(tool) { phase, completed, total ->
                updateAutomationStepCountProgress(step, phase, completed, total)
            }
        }.getOrElse { error ->
            statusMessage = error.message?.takeIf { it.isNotBlank() } ?: AUTO_UPLOAD_FAILED
            false
        }
        if (!ready) {
            setAutomationRunStepState(step, AutomationRunStepState.NeedsUpload, statusMessage.ifBlank { AUTO_UPLOAD_FAILED })
            appendAutomationLog(AUTO_UPLOAD_FAILED)
            return false
        }
        setAutomationRunStepState(step, AutomationRunStepState.NeedsConfirmation, AUTO_PREVIEW_READY_CONFIRM)
        appendAutomationLog(AUTO_WAIT_CONFIRM)
        statusMessage = needsConfirmationMessage()
        return true
    }
    setAutomationRunStepState(step, AutomationRunStepState.UploadedPendingExecution, AUTO_RULE_FILE_UPLOAD_READY)
    flushAutomationUi()
    yieldToAppUiBeforeHeavyWork()
    return runUploadedTextReplaceAutomationStep(chain, request, step)
}

fun EditorController.clearAutomationLog() {
    automationLog = emptyList()
    resetAutomationRunRuntimeState()
}

suspend fun EditorController.runSelectedAutomationChain() {
    val chain = selectedAutomationChain ?: return
    if (chain.steps.isEmpty()) {
        resetAutomationPendingPreviewState()
        resetAutomationRunRuntimeState()
        appendAutomationLog("\u6267\u884c\u94fe\u4e3a\u7a7a")
        statusMessage = "\u6267\u884c\u94fe\u4e3a\u7a7a"
        return
    }

    resetAutomationPendingPreviewState()
    resetAutomationRunRuntimeState()
    chain.steps.forEach { step -> clearTextReplaceRuntimeFile(step.id) }
    resetAutomationRunStepStatuses(chain)
    appendAutomationLog(automationLogDivider(AUTO_START))
    runAutomationChainFrom(chain, startIndex = 0)
}

suspend fun EditorController.continueSelectedAutomationChainAfterConfirmation(): Boolean {
    val request = automationConfirmationRequest ?: return false
    val chain = automationChains.firstOrNull { it.id == request.chainId } ?: run {
        automationConfirmationRequest = null
        statusMessage = AUTO_CHAIN_MISSING
        return false
    }
    val step = automationConfirmationStepForRequest(chain, request)
    if (step == null) {
        automationConfirmationRequest = null
        statusMessage = AUTO_STEP_INVALID
        appendAutomationLog(AUTO_STEP_INVALID)
        return false
    }

    setAutomationRunStepState(step, AutomationRunStepState.Confirmed, AUTO_CONFIRMED)
    appendAutomationLog(AUTO_CONFIRMED)
    automationConfirmationRequest = null
    resetAutomationPendingPreviewState()
    val terminalState = automationRunTerminalStateForSuccess()
    countAutomationRunTerminalState(terminalState)
    setAutomationRunStepState(step, terminalState, statusMessage)
    appendAutomationLog(
        "${automationRunTerminalLogPrefix(terminalState)}${automationStepResultSuffix()}"
    )
    runAutomationChainFrom(chain, startIndex = request.stepIndex + 1)
    return true
}

private suspend fun EditorController.runUploadedInsertChapterAutomationStep(
    chain: AutomationChain,
    request: AutomationConfirmationRequest,
    step: AutomationStep,
    sourceUri: String
): Boolean {
    if (!isAutomationConfirmationStepCurrent(chain, request)) {
        automationConfirmationRequest = null
        statusMessage = AUTO_STEP_INVALID
        appendAutomationLog(AUTO_STEP_INVALID)
        return false
    }
    val sourcePreview = insertChapterSourcePreview
        ?.takeIf { preview ->
            preview.sourceUri == sourceUri && preview.sourceType == INSERT_CHAPTER_SOURCE_UPLOAD
        }
    val selectedSourceIndices = sourcePreview
        ?.items
        ?.map { it.sourceIndex }
        ?.toSet()
        .orEmpty()
    if (selectedSourceIndices.isEmpty()) {
        setAutomationRunStepState(step, AutomationRunStepState.NeedsUpload, AUTO_UPLOAD_CONTENT_EMPTY)
        statusMessage = AUTO_UPLOAD_CONTENT_EMPTY
        appendAutomationLog(AUTO_UPLOAD_CONTENT_INVALID)
        return false
    }

    setAutomationRunStepState(step, AutomationRunStepState.Running)
    flushAutomationUi()
    yieldToAppUiBeforeHeavyWork()
    val success = insertChaptersFromAutomationStep(
        stepId = request.stepId,
        sourceUri = sourceUri,
        positionMode = INSERT_CHAPTER_POSITION_END,
        targetChapterIndex = null,
        selectedSourceIndices = selectedSourceIndices,
        useSelectedSourceIndices = true,
        onProgress = { phase, completed, total ->
            updateAutomationStepCountProgress(step, phase, completed, total)
        }
    )
    automationConfirmationRequest = null
    if (success) {
        flushAutomationUi()
        delay(80)
        val terminalState = automationRunTerminalStateForSuccess()
        countAutomationRunTerminalState(terminalState)
        setAutomationRunStepState(step, terminalState, statusMessage)
        if (automationStepToolForRun(step)?.let { insertChapterParameters(it).preview } == true) {
            automationPendingPreviewToolId = "insert_chapter"
            automationPendingPreviewDataId = request.stepId
            automationPendingPreviewLabel = request.label
        } else {
            automationPendingPreviewToolId = null
            automationPendingPreviewDataId = null
            automationPendingPreviewLabel = null
        }
        appendAutomationLog(
            "${automationRunTerminalLogPrefix(terminalState)}${automationStepResultSuffix()}"
        )
        runAutomationChainFrom(chain, startIndex = request.stepIndex + 1)
        return true
    }

    val terminalState = automationRunTerminalStateForFailure()
    countAutomationRunTerminalState(terminalState)
    setAutomationRunStepState(step, terminalState, statusMessage)
    appendAutomationLog(
        "${automationRunTerminalLogPrefix(terminalState)}${automationFailureSuffix(statusMessage)}"
    )
    return false
}

private suspend fun EditorController.runUploadedTextReplaceAutomationStep(
    chain: AutomationChain,
    request: AutomationConfirmationRequest,
    step: AutomationStep
): Boolean {
    if (!isAutomationConfirmationStepCurrent(chain, request)) {
        automationConfirmationRequest = null
        statusMessage = AUTO_STEP_INVALID
        appendAutomationLog(AUTO_STEP_INVALID)
        return false
    }
    val tool = automationStepToolForRun(step)
    if (tool?.toolId != "text_replace") {
        automationConfirmationRequest = null
        statusMessage = AUTO_STEP_INVALID
        appendAutomationLog(AUTO_STEP_INVALID)
        return false
    }
    setAutomationRunStepState(step, AutomationRunStepState.Running)
    startAutomationStepSingleProgress(step, "执行")
    val success = runConfiguredToolAsync(tool, manual = false)
    automationConfirmationRequest = null
    if (success) {
        finishAutomationStepSingleProgress(step, "执行")
        val terminalState = automationRunTerminalStateForSuccess()
        countAutomationRunTerminalState(terminalState)
        setAutomationRunStepState(step, terminalState, statusMessage)
        appendAutomationLog(
            "${automationRunTerminalLogPrefix(terminalState)}${automationStepResultSuffix()}"
        )
        runAutomationChainFrom(chain, startIndex = request.stepIndex + 1)
        return true
    }

    val terminalState = automationRunTerminalStateForFailure()
    countAutomationRunTerminalState(terminalState)
    setAutomationRunStepState(step, terminalState, statusMessage)
    appendAutomationLog(
        "${automationRunTerminalLogPrefix(terminalState)}${automationFailureSuffix(statusMessage)}"
    )
    return false
}

private suspend fun EditorController.runUploadedCoverAutomationStep(
    chain: AutomationChain,
    request: AutomationConfirmationRequest,
    step: AutomationStep,
    imageUri: String
): Boolean {
    if (!isAutomationConfirmationStepCurrent(chain, request)) {
        automationConfirmationRequest = null
        statusMessage = AUTO_STEP_INVALID
        appendAutomationLog(AUTO_STEP_INVALID)
        return false
    }
    val tool = automationStepToolForRun(step)
    if (tool?.toolId != "generate_cover" || imageUri.isBlank()) {
        automationConfirmationRequest = null
        statusMessage = AUTO_STEP_INVALID
        appendAutomationLog(AUTO_STEP_INVALID)
        return false
    }
    setAutomationRunStepState(step, AutomationRunStepState.Running)
    startAutomationStepSingleProgress(step, "执行")
    val success = runConfiguredToolAsync(coverToolWithUploadedImage(tool, imageUri), manual = false)
    automationConfirmationRequest = null
    if (success) {
        finishAutomationStepSingleProgress(step, "执行")
        val terminalState = automationRunTerminalStateForSuccess()
        countAutomationRunTerminalState(terminalState)
        setAutomationRunStepState(step, terminalState, statusMessage)
        appendAutomationLog(
            "${automationRunTerminalLogPrefix(terminalState)}${automationStepResultSuffix()}"
        )
        runAutomationChainFrom(chain, startIndex = request.stepIndex + 1)
        return true
    }

    val terminalState = automationRunTerminalStateForFailure()
    countAutomationRunTerminalState(terminalState)
    setAutomationRunStepState(step, terminalState, statusMessage)
    appendAutomationLog(
        "${automationRunTerminalLogPrefix(terminalState)}${automationFailureSuffix(statusMessage)}"
    )
    return false
}

fun EditorController.cancelAutomationConfirmation() {
    val request = automationConfirmationRequest ?: return
    automationConfirmationStepForRequest(automationChains, request)
        ?.let { step ->
            setAutomationRunStepState(step, AutomationRunStepState.Failed, AUTO_CANCELLED)
            countAutomationRunTerminalState(AutomationRunStepState.Failed)
        }
    automationConfirmationRequest = null
    resetAutomationPendingPreviewState()
    statusMessage = AUTO_CANCELLED
    appendAutomationLog(AUTO_CANCELLED)
}

internal fun EditorController.failAutomationConfirmationStep(
    step: AutomationStep,
    message: String = statusMessage.ifBlank { "执行失败" }
) {
    statusMessage = message
    val terminalState = automationRunTerminalStateForFailure()
    countAutomationRunTerminalState(terminalState)
    setAutomationRunStepState(step, terminalState, message)
    automationConfirmationRequest = null
    resetAutomationPendingPreviewState()
    appendAutomationLog(
        "${automationRunTerminalLogPrefix(terminalState)}${automationFailureSuffix(message)}"
    )
}

private suspend fun EditorController.runAutomationChainFrom(chain: AutomationChain, startIndex: Int) {
    for (index in startIndex until chain.steps.size) {
        val step = chain.steps[index]
        val label = automationStepLabel(step)
        appendAutomationLog(automationStepLogDivider(index, label))
        flushAutomationUi()
        val runTool = automationStepToolForRun(step)
        if (runTool == null) {
            countAutomationRunTerminalState(AutomationRunStepState.Failed)
            setAutomationRunStepState(step, AutomationRunStepState.Failed, "\u6267\u884c\u6b65\u9aa4\u5f15\u7528\u7684\u9884\u8bbe\u5df2\u4e0d\u5b58\u5728")
            appendAutomationLog("\u5931\u8d25 \u9884\u8bbe\u5df2\u4e0d\u5b58\u5728")
            continue
        }
        if (availableTools.none { it.id == runTool.toolId }) {
            countAutomationRunTerminalState(AutomationRunStepState.Failed)
            setAutomationRunStepState(step, AutomationRunStepState.Failed, AUTO_TOOL_MISSING)
            appendAutomationLog(AUTO_TOOL_MISSING)
            continue
        }
        if (runTool.toolId == "text_replace" && editorToolNeedsTextReplaceRuleFile(runTool)) {
            setAutomationRunStepState(step, AutomationRunStepState.NeedsUpload, AUTO_NEEDS_RULE_FILE)
            automationPendingPreviewToolId = null
            automationPendingPreviewDataId = null
            automationPendingPreviewLabel = null
            automationConfirmationRequest = AutomationConfirmationRequest(
                chainId = chain.id,
                stepIndex = index,
                stepId = step.id,
                toolId = runTool.toolId,
                label = label
            )
            appendAutomationLog(AUTO_NEEDS_RULE_FILE)
            statusMessage = AUTO_UPLOAD_RULE_FILE_FIRST
            return
        }
        if (
            runTool.toolId == "insert_chapter" &&
            automationStepParameterValue(step, INSERT_CHAPTER_PARAM_SOURCE_TYPE)
                .ifBlank { INSERT_CHAPTER_SOURCE_UPLOAD } != INSERT_CHAPTER_SOURCE_SOSAD
        ) {
            clearInsertChapterSourcePreview()
            setAutomationRunStepState(step, AutomationRunStepState.NeedsUpload, AUTO_NEEDS_UPLOAD_FILE)
            automationPendingPreviewToolId = null
            automationPendingPreviewDataId = null
            automationPendingPreviewLabel = null
            automationConfirmationRequest = AutomationConfirmationRequest(
                chainId = chain.id,
                stepIndex = index,
                stepId = step.id,
                toolId = runTool.toolId,
                label = label
            )
            appendAutomationLog(AUTO_NEEDS_UPLOAD_FILE)
            statusMessage = AUTO_NEEDS_UPLOAD_FILE
            return
        }
        if (runTool.toolId == "generate_cover" && coverToolNeedsImageUpload(runTool)) {
            setAutomationRunStepState(step, AutomationRunStepState.NeedsUpload, AUTO_NEEDS_UPLOAD_FILE)
            automationPendingPreviewToolId = null
            automationPendingPreviewDataId = null
            automationPendingPreviewLabel = null
            automationConfirmationRequest = AutomationConfirmationRequest(
                chainId = chain.id,
                stepIndex = index,
                stepId = step.id,
                toolId = runTool.toolId,
                label = label
            )
            appendAutomationLog(AUTO_NEEDS_UPLOAD_FILE)
            statusMessage = AUTO_UPLOAD_IMAGE_FIRST
            return
        }
        val previewEnabled = automationStepHasPreviewEnabled(runTool)
        setAutomationRunStepState(step, AutomationRunStepState.Running)
        if (previewEnabled) {
            setAutomationRunStepProgress(step, 0f, "\u52a0\u8f7d\u9884\u89c8")
        } else {
            updateAutomationStepCountProgress(step, "\u6267\u884c", 0, 1)
        }
        flushAutomationUi()
        yieldToAppUiBeforeHeavyWork()
        val success = if (runTool.toolId == "text_replace" && previewEnabled) {
            runTextReplaceToolForAutomationPreview(runTool) { phase, completed, total ->
                updateAutomationStepCountProgress(step, phase, completed, total)
            }
        } else {
            runConfiguredToolAsync(runTool, manual = false)
        }
        if (success) {
            if (!previewEnabled) {
                finishAutomationStepSingleProgress(step, "\u6267\u884c")
            }
            val terminalState = automationRunTerminalStateForSuccess()
            countAutomationRunTerminalState(terminalState)
            setAutomationRunStepState(step, terminalState, statusMessage)
            appendAutomationLog(
                "${automationRunTerminalLogPrefix(terminalState)}${automationStepResultSuffix()}"
            )
        } else {
            if (statusMessage == needsConfirmationMessage()) {
                if (runTool.toolId == "fetch_info") clearFetchInfoPreview()
                if (
                    previewEnabled &&
                    runTool.toolId != "fetch_info" &&
                    runTool.toolId != "insert_chapter" &&
                    runTool.toolId != "text_replace"
                ) {
                    updateAutomationStepCountProgress(step, "\u52a0\u8f7d\u9884\u89c8", 1, 1)
                } else if (!previewEnabled) {
                    finishAutomationStepSingleProgress(step, "\u6267\u884c")
                }
                val insertChapterNeedsUpload = runTool.toolId == "insert_chapter" &&
                    automationStepParameterValue(step, INSERT_CHAPTER_PARAM_SOURCE_TYPE)
                        .ifBlank { INSERT_CHAPTER_SOURCE_UPLOAD } != INSERT_CHAPTER_SOURCE_SOSAD
                if (insertChapterNeedsUpload) {
                    clearInsertChapterSourcePreview()
                }
                val pendingState = if (insertChapterNeedsUpload) {
                    AutomationRunStepState.NeedsUpload
                } else {
                    AutomationRunStepState.NeedsConfirmation
                }
                setAutomationRunStepState(
                    step,
                    pendingState,
                    if (insertChapterNeedsUpload) AUTO_NEEDS_UPLOAD_FILE else AUTO_WAIT_CONFIRM
                )
                if (!insertChapterNeedsUpload && previewEnabled) {
                    automationPendingPreviewToolId = runTool.toolId
                    automationPendingPreviewDataId = step.id
                    automationPendingPreviewLabel = label
                } else if (insertChapterNeedsUpload) {
                    automationPendingPreviewToolId = null
                    automationPendingPreviewDataId = null
                    automationPendingPreviewLabel = null
                }
                automationConfirmationRequest = AutomationConfirmationRequest(
                    chainId = chain.id,
                    stepIndex = index,
                    stepId = step.id,
                    toolId = runTool.toolId,
                    label = label
                )
                appendAutomationLog(if (insertChapterNeedsUpload) AUTO_NEEDS_UPLOAD_FILE else AUTO_WAIT_CONFIRM)
                statusMessage = if (insertChapterNeedsUpload) AUTO_NEEDS_UPLOAD_FILE else needsConfirmationMessage()
                return
            }
            val terminalState = automationRunTerminalStateForFailure()
            countAutomationRunTerminalState(terminalState)
            setAutomationRunStepState(step, terminalState, statusMessage)
            appendAutomationLog(
                "${automationRunTerminalLogPrefix(terminalState)}${automationFailureSuffix(statusMessage)}"
            )
        }
    }
    statusMessage = buildString {
        append("\u81ea\u52a8\u5316\u5b8c\u6210\uff1a\u6267\u884c $automationRunExecuted \u6b65\uff0c\u8df3\u8fc7 $automationRunSkipped \u6b65")
        if (automationRunFailed > 0) append("\uff0c\u5931\u8d25 $automationRunFailed \u6b65")
    }
    appendAutomationLog(automationLogDivider(statusMessage))
}

private fun automationFailureSuffix(message: String): String {
    return message.takeIf { it.isNotBlank() }?.let { "\uff1a$it" }.orEmpty()
}
