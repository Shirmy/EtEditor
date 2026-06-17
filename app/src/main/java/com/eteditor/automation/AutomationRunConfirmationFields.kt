package com.eteditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun AutomationPendingPreviewPane(
    controller: EditorController,
    toolId: String,
    previewId: String,
    label: String,
    modifier: Modifier = Modifier
) {
    when (toolId) {
        "file_rename" -> {
            if (controller.fileRenamePlanToolId == previewId && controller.fileRenamePlan.isNotEmpty()) {
                FileRenamePlanPane(
                    controller = controller,
                    toolId = previewId,
                    onDismiss = {},
                    onApplied = {},
                    modifier = modifier
                )
            } else {
                AutomationPendingPreviewFallback(label, "没有可用的重命名预览", modifier)
            }
        }
        "chapter_title_rename" -> {
            if (controller.titleRenamePlanToolId == previewId && controller.titleRenamePlan.isNotEmpty()) {
                TitleRenamePlanPane(
                    controller = controller,
                    toolId = previewId,
                    onDismiss = {},
                    onApplied = {},
                    modifier = modifier
                )
            } else {
                AutomationPendingPreviewFallback(label, "没有可用的标题预览", modifier)
            }
        }
        "title_format" -> {
            if (controller.titleFormatPlanToolId == previewId && controller.titleFormatPlan.isNotEmpty()) {
                TitleFormatPlanPane(
                    controller = controller,
                    toolId = previewId,
                    onDismiss = {},
                    onApplied = {},
                    modifier = modifier
                )
            } else {
                AutomationPendingPreviewFallback(label, "没有可用的格式预览", modifier)
            }
        }
        "text_replace" -> {
            if (
                (controller.textSearchToolId == previewId && controller.textSearchResults.isNotEmpty()) ||
                controller.replacementFilePreview?.toolId == previewId
            ) {
                TextSearchResultsPane(
                    controller = controller,
                    toolId = previewId,
                    onDismiss = {},
                    onApplied = {},
                    modifier = modifier
                )
            } else {
                AutomationPendingPreviewFallback(label, "没有可用的替换预览", modifier)
            }
        }
        "fetch_info" -> {
            if (controller.fetchInfoPreview?.toolId == previewId) {
                FetchInfoPreviewPane(
                    controller = controller,
                    toolId = previewId,
                    onDismiss = {},
                    onApplied = {},
                    modifier = modifier
                )
            } else {
                AutomationPendingPreviewFallback(label, "没有可用的抓取预览", modifier)
            }
        }
        "insert_chapter" -> {
            val preview = controller.insertChapterSourcePreview
            if (preview != null) {
                InsertChapterSourcePreviewPane(
                    preview = preview,
                    manual = false,
                    selectedSourceIndices = emptySet(),
                    onToggle = { _, _ -> },
                    modifier = modifier,
                    expanded = true
                )
            } else {
                AutomationPendingPreviewFallback(label, "没有可用的章节预览", modifier)
            }
        }
        "generate_cover" -> {
            if (controller.generatedCoverPreview != null) {
                GeneratedCoverPreviewPane(
                    preview = controller.generatedCoverPreview,
                    modifier = modifier
                )
            } else {
                AutomationPendingPreviewFallback(label, "没有可用的图片预览", modifier)
            }
        }
        else -> AutomationPendingPreviewFallback(label, "没有可用的预览", modifier)
    }
}

@Composable
private fun AutomationPendingPreviewFallback(
    label: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = PreviewShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "$label：$message",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
internal fun AutomationConfirmationPreviewPane(
    controller: EditorController,
    request: AutomationConfirmationRequest,
    modifier: Modifier = Modifier
) {
    AutomationConfirmationPreview(
        controller = controller,
        request = request,
        modifier = modifier
    )
}

@Composable
private fun AutomationConfirmationPreview(
    controller: EditorController,
    request: AutomationConfirmationRequest,
    modifier: Modifier = Modifier
) {
    val continueRun: () -> Unit = {
        controller.controllerScope.launch {
            controller.continueSelectedAutomationChainAfterConfirmation()
        }
    }
    val cancelRun: () -> Unit = { controller.cancelAutomationConfirmation() }

    when (request.toolId) {
        "file_rename" -> {
            if (controller.fileRenamePlanToolId == request.stepId && controller.fileRenamePlan.isNotEmpty()) {
                FileRenamePlanPane(
                    controller = controller,
                    toolId = request.stepId,
                    onDismiss = cancelRun,
                    onApplied = continueRun,
                    modifier = modifier
                )
            } else {
                AutomationConfirmationFallback(request, "没有可用的重命名预览", cancelRun, modifier)
            }
        }
        "chapter_title_rename" -> {
            if (controller.titleRenamePlanToolId == request.stepId && controller.titleRenamePlan.isNotEmpty()) {
                TitleRenamePlanPane(
                    controller = controller,
                    toolId = request.stepId,
                    onDismiss = cancelRun,
                    onApplied = continueRun,
                    modifier = modifier
                )
            } else {
                AutomationConfirmationFallback(request, "没有可用的标题预览", cancelRun, modifier)
            }
        }
        "title_format" -> {
            if (controller.titleFormatPlanToolId == request.stepId && controller.titleFormatPlan.isNotEmpty()) {
                TitleFormatPlanPane(
                    controller = controller,
                    toolId = request.stepId,
                    onDismiss = cancelRun,
                    onApplied = continueRun,
                    modifier = modifier
                )
            } else {
                AutomationConfirmationFallback(request, "没有可用的格式预览", cancelRun, modifier)
            }
        }
        "text_replace" -> {
            if (
                (controller.textSearchToolId == request.stepId && controller.textSearchResults.isNotEmpty()) ||
                controller.replacementFilePreview?.toolId == request.stepId
            ) {
                TextSearchResultsPane(
                    controller = controller,
                    toolId = request.stepId,
                    onDismiss = cancelRun,
                    onApplied = continueRun,
                    modifier = modifier
                )
            } else {
                AutomationConfirmationFallback(request, "没有可用的替换预览", cancelRun, modifier)
            }
        }
        "fetch_info" -> {
            AutomationFetchInfoConfirmation(
                controller = controller,
                request = request,
                onDismiss = cancelRun,
                onApplied = continueRun,
                modifier = modifier
            )
        }
        "insert_chapter" -> {
            AutomationInsertChapterConfirmation(
                controller = controller,
                request = request,
                onDismiss = cancelRun,
                onApplied = continueRun,
                modifier = modifier
            )
        }
        "generate_cover" -> {
            AutomationCoverConfirmation(
                controller = controller,
                request = request,
                onDismiss = cancelRun,
                onApplied = continueRun,
                modifier = modifier
            )
        }
        else -> {
            AutomationConfirmationFallback(
                request = request,
                message = "这个步骤需要先查看预览后再继续",
                onDismiss = cancelRun,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun AutomationCoverConfirmation(
    controller: EditorController,
    request: AutomationConfirmationRequest,
    onDismiss: () -> Unit,
    onApplied: () -> Unit,
    modifier: Modifier = Modifier
) {
    val step = controller.automationConfirmationStep(request)
    if (step == null) {
        AutomationConfirmationFallback(request, "执行链步骤已变化", onDismiss, modifier)
        return
    }
    var applying by remember(request.stepId) { mutableStateOf(false) }
    val preview = controller.generatedCoverPreview
    ToolDetailTemplate(
        title = "确认 / ${request.label}",
        modifier = modifier,
        onBack = onDismiss
    ) {
        GeneratedCoverPreviewPane(
            preview = preview,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 520.dp)
        )
        if (applying) {
            ToolRunProgress(
                toolName = "写入图片",
                progress = 0.72f
            )
        }
        ButtonRow {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !applying,
                modifier = Modifier.weight(1f),
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Text("取消")
            }
            Button(
                enabled = preview != null && !controller.busy && !applying,
                onClick = {
                    applying = true
                    controller.setAutomationRunStepState(step, AutomationRunStepState.Running)
                    controller.setAutomationRunStepProgress(step, 0f, "写入图片 0/1")
                    controller.controllerScope.launch {
                        delay(16)
                        yieldToAppUiBeforeHeavyWork()
                        val ok = controller.applyGeneratedCoverPreview()
                        if (ok) {
                            controller.setAutomationRunStepProgress(step, 1f, "写入图片 1/1")
                            applying = false
                            onApplied()
                        } else {
                            applying = false
                            controller.failAutomationConfirmationStep(
                                step,
                                controller.statusMessage.ifBlank { "图片写入失败" }
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("确认写入")
            }
        }
    }
}

@Composable
private fun AutomationFetchInfoConfirmation(
    controller: EditorController,
    request: AutomationConfirmationRequest,
    onDismiss: () -> Unit,
    onApplied: () -> Unit,
    modifier: Modifier = Modifier
) {
    val step = controller.automationConfirmationStep(request)
    var preparing by remember(request.stepId) { mutableStateOf(false) }
    var attempted by remember(request.stepId) { mutableStateOf(false) }
    val previewReady = controller.fetchInfoPreview?.toolId == request.stepId
    val choiceRequest = controller.fetchInfoSearchChoiceRequest?.takeIf { it.toolId == request.stepId }

    LaunchedEffect(preparing, previewReady, controller.fetchInfoProgress, controller.statusMessage) {
        val currentStep = step ?: return@LaunchedEffect
        if (preparing || (!previewReady && controller.fetchInfoProgress > 0f)) {
            controller.setAutomationRunStepProgress(
                step = currentStep,
                progress = controller.fetchInfoProgress.takeIf { it > 0f } ?: 0.08f,
                progressText = fetchProgressDisplayText(controller.statusMessage)
                    .ifBlank { "抓取预览" }
            )
        } else if (previewReady) {
            controller.setAutomationRunStepProgress(
                step = currentStep,
                progress = 1f,
                progressText = "抓取预览 1/1"
            )
        }
    }

    LaunchedEffect(request.stepId, previewReady, choiceRequest) {
        if (!previewReady && choiceRequest == null && !attempted) {
            attempted = true
            preparing = true
            yieldToAppUiBeforeHeavyWork()
            controller.prepareFetchInfoPreviewForAutomationStep(request.stepId)
            preparing = false
        }
    }

    if (previewReady) {
        FetchInfoPreviewPane(
            controller = controller,
            toolId = request.stepId,
            onDismiss = onDismiss,
            onApplied = onApplied,
            modifier = modifier
        )
    } else if (preparing) {
        AutomationFetchInfoLoadingPane(
            progress = controller.fetchInfoProgress.takeIf { it > 0f } ?: 0.08f,
            modifier = modifier
        )
    } else {
        AutomationConfirmationFallback(
            request = request,
            message = controller.statusMessage.ifBlank { "没有可用的抓取预览" },
            onDismiss = onDismiss,
            modifier = modifier
        )
    }

    choiceRequest?.let { pendingChoice ->
        FetchInfoSearchChoiceDialog(
            controller = controller,
            toolId = pendingChoice.toolId,
            onDismiss = { controller.clearFetchInfoSearchChoiceRequest(pendingChoice.toolId) },
            onPrepared = {}
        )
    }
    controller.fetchInfoRetryRequest
        ?.takeIf { it.toolId == request.stepId }
        ?.let { retryRequest ->
            FetchInfoRetryDialog(
                controller = controller,
                toolId = retryRequest.toolId,
                onDismiss = { controller.clearFetchInfoRetryRequest(retryRequest.toolId) },
                onPrepared = {}
            )
        }
}

@Composable
private fun AutomationInsertChapterConfirmation(
    controller: EditorController,
    request: AutomationConfirmationRequest,
    onDismiss: () -> Unit,
    onApplied: () -> Unit,
    modifier: Modifier = Modifier
) {
    val step = controller.automationConfirmationStep(request)
    if (step == null) {
        AutomationConfirmationFallback(request, "执行链步骤已变化", onDismiss, modifier)
        return
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val rawSourceType = controller
        .automationStepParameterValue(step, INSERT_CHAPTER_PARAM_SOURCE_TYPE)
    val sourceType = if (rawSourceType == INSERT_CHAPTER_SOURCE_SOSAD) {
        INSERT_CHAPTER_SOURCE_SOSAD
    } else {
        INSERT_CHAPTER_SOURCE_UPLOAD
    }
    val sourceLabel = when (sourceType) {
        INSERT_CHAPTER_SOURCE_SOSAD -> "废文"
        else -> "上传文件"
    }
    val existingSourceUri = controller.insertChapterSourcePreview
        ?.takeIf { preview -> preview.sourceType == sourceType }
        ?.sourceUri
        .orEmpty()
    var sourceUri by remember(request.stepId, sourceType) {
        mutableStateOf(existingSourceUri)
    }
    var selectedSourceIndices by remember(request.stepId, sourceUri, sourceType) { mutableStateOf<Set<Int>>(emptySet()) }
    var sourceOrderReversed by remember(request.stepId, sourceUri, sourceType) {
        mutableStateOf(true)
    }
    var preparing by remember(request.stepId) { mutableStateOf(false) }
    var applying by remember(request.stepId) { mutableStateOf(false) }
    var attemptedSosadPrepare by remember(request.stepId) { mutableStateOf(false) }
    var paneMessage by remember(request.stepId) { mutableStateOf<String?>(null) }
    var progressPhase by remember(request.stepId) { mutableStateOf("") }
    var progressCompleted by remember(request.stepId) { mutableStateOf(0) }
    var progressTotal by remember(request.stepId) { mutableStateOf(0) }
    fun resetProgress() {
        progressPhase = ""
        progressCompleted = 0
        progressTotal = 0
    }
    fun updateProgress(phase: String, completed: Int, total: Int) {
        progressPhase = phase
        progressCompleted = completed
        progressTotal = total
        controller.setAutomationRunStepProgress(
            step = step,
            progress = insertChapterProgressValue(completed, total),
            progressText = insertChapterProgressLabel("插入章节", phase, completed, total)
        )
    }

    val sosadMode = sourceType == INSERT_CHAPTER_SOURCE_SOSAD
    val sosadQuery = controller
        .automationStepParameterValue(step, INSERT_CHAPTER_PARAM_SOSAD_QUERY)
        .ifBlank { controller.defaultCoverTitle() }
    val sosadAuthCookie = controller
        .automationStepParameterValue(step, INSERT_CHAPTER_PARAM_SOSAD_AUTH_COOKIE)
        .ifBlank { controller.sosadLoginCookie() }
    val sosadRangeStart = controller.automationStepParameterValue(step, INSERT_CHAPTER_PARAM_SOSAD_RANGE_START)
    val sosadRangeEnd = controller.automationStepParameterValue(step, INSERT_CHAPTER_PARAM_SOSAD_RANGE_END)
    val activeSourceUri = if (sosadMode) {
        controller.insertChapterSosadSourceUri(sosadQuery, sosadRangeStart, sosadRangeEnd)
    } else {
        sourceUri
    }
    val sourceReady = if (sosadMode) {
        sosadQuery.trim().isNotBlank() && controller.sosadLoginReady(sosadAuthCookie)
    } else {
        sourceUri.isNotBlank()
    }
    val sourcePreview = controller.insertChapterSourcePreview
        ?.takeIf { preview -> preview.sourceUri == activeSourceUri && preview.sourceType == sourceType }
    val choiceRequest = controller.fetchInfoSearchChoiceRequest?.takeIf { it.toolId == request.stepId }
    suspend fun prepareCurrentSource(): Boolean {
        if (!sourceReady) {
            paneMessage = if (sosadMode) {
                if (sosadAuthCookie.isBlank()) {
                    "请先选择按书名或填写自定义内容，并登录废文"
                } else {
                    "废文登录已失效，请重新登录"
                }
            } else {
                "请选择来源文件"
            }
            return false
        }
        controller.setAutomationRunStepProgress(step, 0.08f, "读取来源")
        yieldToAppUiBeforeHeavyWork()
        val prepared = if (sosadMode) {
            controller.prepareInsertChapterSosadPreviewForAutomationStep(request.stepId)
        } else {
            controller.prepareInsertChapterSourcePreview(sourceType, sourceUri)
        }
        if (prepared) {
            controller.setAutomationRunStepProgress(step, 1f, "读取来源 1/1")
        }
        return prepared
    }

    val sourcePicker = rememberLauncherForActivityResult(OpenEditableDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileError = insertChapterSourceFileError(context, uri)
        if (fileError != null) {
            paneMessage = fileError
            return@rememberLauncherForActivityResult
        }
        val pickedSourceUri = uri.toString()
        sourceUri = pickedSourceUri
        selectedSourceIndices = emptySet()
        scope.launch {
            preparing = true
            controller.setAutomationRunStepProgress(step, 0.08f, "读取来源")
            yieldToAppUiBeforeHeavyWork()
            val ok = controller.prepareInsertChapterSourcePreview(sourceType, pickedSourceUri)
            if (ok) {
                controller.setAutomationRunStepProgress(step, 1f, "读取来源 1/1")
            }
            preparing = false
            if (!ok) {
                paneMessage = controller.statusMessage.ifBlank { "来源读取失败" }
            }
        }
    }

    LaunchedEffect(sourcePreview?.sourceUri, sourcePreview?.items?.size, sourceType) {
        selectedSourceIndices = emptySet()
    }
    LaunchedEffect(request.stepId, sosadMode, sourceReady, sourcePreview?.sourceUri) {
        if (sosadMode && sourceReady && sourcePreview == null && !attemptedSosadPrepare) {
            attemptedSosadPrepare = true
            preparing = true
            controller.setAutomationRunStepProgress(step, 0.08f, "读取来源")
            yieldToAppUiBeforeHeavyWork()
            val ok = controller.prepareInsertChapterSosadPreviewForAutomationStep(request.stepId)
            if (ok) {
                controller.setAutomationRunStepProgress(step, 1f, "读取来源 1/1")
            }
            preparing = false
            if (!ok) {
                if (controller.fetchInfoSearchChoiceRequest?.toolId != request.stepId) {
                    paneMessage = controller.statusMessage.ifBlank { "废文目录读取失败" }
                }
            }
        }
    }

    val runInsert: () -> Unit = {
        val runSourceIndices = selectedSourceIndices
        val runSourceOrderReversed = sourceOrderReversed
        resetProgress()
        applying = true
        controller.setAutomationRunStepState(step, AutomationRunStepState.Running)
        controller.setAutomationRunStepProgress(step, 0f, "插入章节 0/${runSourceIndices.size}")
        controller.controllerScope.launch {
            delay(16)
            yieldToAppUiBeforeHeavyWork()
            val ok = if (sourcePreview == null && !prepareCurrentSource()) {
                false
            } else {
                controller.insertChaptersFromAutomationStep(
                    stepId = request.stepId,
                    sourceUri = activeSourceUri,
                    positionMode = INSERT_CHAPTER_POSITION_END,
                    targetChapterIndex = null,
                    selectedSourceIndices = runSourceIndices,
                    useSelectedSourceIndices = true,
                    // 废文来源：逆序显示方便挑选，但始终按正序插入
                    reverseSelectedOrder = !sosadMode && runSourceOrderReversed,
                    onProgress = ::updateProgress
                )
            }
            applying = false
            if (ok) {
                onApplied()
            } else {
                controller.failAutomationConfirmationStep(
                    step,
                    controller.statusMessage.ifBlank { "未插入章节" }
                )
            }
        }
    }

    // 来源文件需要先选文件；只有文件预览就绪后才弹章节勾选对话框。
    if (!sosadMode && sourcePreview == null && !preparing) {
        LaunchedEffect(request.stepId, sourceUri) {
            if (sourceUri.isBlank()) sourcePicker.launch(INSERT_CHAPTER_SOURCE_MIME_TYPES)
        }
    }

    // 抓取/读取目录的进度走执行链小卡内的进度条，预览未就绪时不弹对话框。
    if (sourcePreview != null || applying) {
        InsertChapterSourcePickerDialog(
            preview = sourcePreview,
            selectedSourceIndices = selectedSourceIndices,
            title = "选择${sourceLabel}章节",
            sourceOrderReversed = sourceOrderReversed,
            onToggle = { sourceIndex, checked ->
                selectedSourceIndices = if (checked) {
                    selectedSourceIndices + sourceIndex
                } else {
                    selectedSourceIndices - sourceIndex
                }
            },
            onSelectAll = {
                selectedSourceIndices = sourcePreview?.items?.map { it.sourceIndex }?.toSet().orEmpty()
            },
            onClear = { selectedSourceIndices = emptySet() },
            onReverseOrder = { sourceOrderReversed = !sourceOrderReversed },
            onDismiss = onDismiss,
            confirmLabel = "确认插入",
            confirmEnabled = sourceReady &&
                sourcePreview != null &&
                selectedSourceIndices.isNotEmpty() &&
                !controller.busy &&
                !preparing &&
                !applying,
            confirmRunning = applying,
            confirmProgressLabel = insertChapterProgressLabel("插入章节", progressPhase, progressCompleted, progressTotal),
            confirmProgress = insertChapterProgressValue(progressCompleted, progressTotal),
            onConfirm = runInsert
        )
    }
    choiceRequest?.let { pendingChoice ->
        FetchInfoSearchChoiceDialog(
            controller = controller,
            toolId = pendingChoice.toolId,
            onDismiss = { controller.clearFetchInfoSearchChoiceRequest(pendingChoice.toolId) },
            onPrepared = { paneMessage = null },
            preparingLabel = "正在抓取目录",
            onSelectChoice = { choice ->
                controller.selectInsertChapterSosadSearchChoice(pendingChoice.toolId, choice)
            }
        )
    }
    paneMessage?.let { message ->
        ToolRunMessageDialog(
            title = request.label,
            message = message,
            onDismiss = { paneMessage = null }
        )
    }
}

@Composable
private fun AutomationFetchInfoLoadingPane(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    Surface(
        shape = PreviewShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            LinearProgressIndicator(
                progress = { safeProgress },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun AutomationConfirmationFallback(
    request: AutomationConfirmationRequest,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ToolDetailTemplate(
        title = "确认 / ${request.label}",
        modifier = modifier,
        onBack = onDismiss
    ) {
        Surface(
            shape = RowShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
