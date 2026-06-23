package com.eteditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eteditor.core.DocumentKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun InsertChapterToolParameterPanel(
    controller: EditorController,
    tool: ToolDefinition,
    documentSessionKey: Int,
    resetKey: Int,
    scope: CoroutineScope,
    runningToolName: String?,
    runProgress: () -> Float,
    currentRunJob: () -> Job?,
    onRunProgressChange: (Float) -> Unit,
    onRunJobChange: (Job?) -> Unit,
    startDirectRunFeedback: (String) -> Unit,
    onRunMessage: (Pair<String, String>) -> Unit
) {
    var sourceUri by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf("") }
    var sourceDisplayName by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf("") }
    val rawSourceType = controller
        .builtInToolParameterValue(tool.id, INSERT_CHAPTER_PARAM_SOURCE_TYPE)
    val sourceType = if (rawSourceType == INSERT_CHAPTER_SOURCE_SOSAD) {
        INSERT_CHAPTER_SOURCE_SOSAD
    } else {
        INSERT_CHAPTER_SOURCE_UPLOAD
    }
    val sosadMode = sourceType == INSERT_CHAPTER_SOURCE_SOSAD
    val sosadQuery = controller
        .builtInToolParameterValue(tool.id, INSERT_CHAPTER_PARAM_SOSAD_QUERY)
        .ifBlank { controller.defaultCoverTitle() }
    val sosadAuthCookie = controller
        .builtInToolParameterValue(tool.id, INSERT_CHAPTER_PARAM_SOSAD_AUTH_COOKIE)
        .ifBlank { controller.sosadLoginCookie() }
    val activeSourceUri = when {
        sosadMode -> controller.insertChapterSosadSourceUri(sosadQuery, "", "")
        else -> sourceUri
    }
    val sourceReady = when {
        sosadMode -> sosadQuery.trim().isNotBlank() && controller.sosadLoginReady(sosadAuthCookie)
        else -> sourceUri.isNotBlank()
    }
    var selectedSourceIndices by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<Set<Int>>(emptySet()) }
    var insertSourceOrderReversed by remember(documentSessionKey, resetKey, tool.id, activeSourceUri, sosadMode) {
        mutableStateOf(true)
    }
    var insertLoading by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var insertLoadingName by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf("\u5904\u7406\u4e2d") }
    var insertProgressPhase by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf("") }
    var insertProgressCompleted by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(0) }
    var insertProgressTotal by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(0) }
    var showManualRangeDialog by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var manualRangePreview by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<InsertChapterSourcePreview?>(null) }
    val context = LocalContext.current
    val previewEnabled = !sosadMode && controller
        .builtInToolParameterValue(tool.id, INSERT_CHAPTER_PARAM_PREVIEW)
        .ifBlank { "true" } != "false"
    val sourcePreview = controller.insertChapterSourcePreview
        ?.takeIf { it.sourceUri == activeSourceUri && it.sourceType == sourceType }

    fun resetInsertProgress() {
        insertProgressPhase = ""
        insertProgressCompleted = 0
        insertProgressTotal = 0
    }

    val sourcePicker = rememberLauncherForActivityResult(OpenEditableDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileError = insertChapterSourceFileError(context, uri)
        if (fileError != null) {
            onRunMessage(tool.title to fileError)
            return@rememberLauncherForActivityResult
        }
        val pickedSourceUri = uri.toString()
        sourceUri = pickedSourceUri
        selectedSourceIndices = emptySet()
        manualRangePreview = null
        showManualRangeDialog = false
        scope.launch {
            insertLoadingName = "读取来源"
            resetInsertProgress()
            onRunProgressChange(0.08f)
            insertLoading = true
            yieldToAppUiBeforeHeavyWork()
            sourceDisplayName = insertChapterSourceDisplayName(context, uri)
            val ok = controller.prepareInsertChapterSourcePreview(sourceType, pickedSourceUri)
            if (ok) {
                onRunProgressChange(1f)
                manualRangePreview = controller.insertChapterSourcePreview
                    ?.takeIf { preview -> preview.sourceType == sourceType }
                selectedSourceIndices = if (sosadMode) {
                    manualRangePreview?.items?.map { it.sourceIndex }?.toSet().orEmpty()
                } else {
                    emptySet()
                }
                insertLoading = false
                showManualRangeDialog = true
            } else {
                insertLoading = false
                onRunProgressChange(0f)
                onRunMessage(tool.title to controller.statusMessage.ifBlank { "来源读取失败" })
            }
        }
    }

    fun pickInsertSourceFile() {
        sourcePicker.launch(INSERT_CHAPTER_SOURCE_MIME_TYPES)
    }

    LaunchedEffect(sourcePreview?.sourceUri, sourcePreview?.items?.size, sourceType) {
        if (sourcePreview != null) {
            manualRangePreview = sourcePreview
            selectedSourceIndices = if (sosadMode) {
                sourcePreview.items.map { it.sourceIndex }.toSet()
            } else {
                emptySet()
            }
        }
    }

    fun updateInsertLoadingPhase(phase: String) {
        val displayPhase = if (phase.trim().startsWith("\u3010")) phase else "【废文】${phase.trim()}"
        val cleanPhase = displayPhase.substringAfter("\u3010", displayPhase).trim()
        insertLoadingName = displayPhase
        onRunProgressChange(
            when (cleanPhase) {
                "搜索中..." -> 0.08f
                else -> if (cleanPhase.startsWith("搜索镜像")) {
                    if (runProgress() < 0.22f) 0.22f else runProgress()
                } else if (cleanPhase == "正在抓取目录") {
                    if (runProgress() < 0.42f) 0.42f else runProgress()
                } else {
                    runProgress()
                }
            }
        )
    }

    fun updateInsertProgress(phase: String, completed: Int, total: Int) {
        insertProgressPhase = phase
        insertProgressCompleted = completed
        insertProgressTotal = total
        onRunProgressChange(insertChapterProgressValue(completed, total))
    }

    fun insertProgressLabel(): String {
        return insertChapterProgressLabel(
            fallbackLabel = insertLoadingName,
            phase = insertProgressPhase,
            completed = insertProgressCompleted,
            total = insertProgressTotal
        )
    }

    suspend fun prepareCurrentInsertSource(): Boolean {
        return when {
            sosadMode -> controller.prepareInsertChapterSosadRangeCatalogForBuiltIn(
                tool.id,
                { phase -> updateInsertLoadingPhase(phase) }
            )
            else -> controller.prepareInsertChapterSourcePreview(sourceType, sourceUri)
        }
    }

    fun runInsertChapters(useSelectedSourceIndices: Boolean = false) {
        scope.launch {
            currentRunJob()?.cancel()
            insertLoadingName = "插入章节"
            resetInsertProgress()
            onRunProgressChange(0f)
            insertLoading = true
            onRunJobChange(null)
            var ok = false
            try {
                yieldToAppUiBeforeHeavyWork()
                if (sosadMode && sourcePreview == null && !prepareCurrentInsertSource()) {
                    if (controller.fetchInfoSearchChoiceRequest?.toolId != controller.builtInToolPlanId(tool.id)) {
                        onRunMessage(tool.title to controller.statusMessage.ifBlank { "来源读取失败" })
                    }
                    return@launch
                }
                insertLoadingName = "插入章节"
                ok = controller.insertChaptersFromBuiltIn(
                    sourceUri = activeSourceUri,
                    positionMode = INSERT_CHAPTER_POSITION_END,
                    targetChapterIndex = null,
                    selectedSourceIndices = if (useSelectedSourceIndices) selectedSourceIndices else emptySet(),
                    useSelectedSourceIndices = useSelectedSourceIndices,
                    // 逆序显示方便挑选，但始终按正序插入
                    reverseSelectedOrder = false,
                    onProgress = ::updateInsertProgress
                )
                if (!ok) {
                    onRunMessage(tool.title to controller.statusMessage.ifBlank { "未插入章节" })
                } else {
                    showManualRangeDialog = false
                }
            } finally {
                insertLoading = false
                currentRunJob()?.cancel()
                onRunJobChange(null)
                if (!ok) onRunProgressChange(0f)
            }
            if (ok) startDirectRunFeedback(tool.title)
        }
    }

    fun revealInsertPreview() {
        scope.launch {
            currentRunJob()?.cancel()
            insertLoadingName = if (sosadMode) "【废文】搜索中..." else "读取来源"
            resetInsertProgress()
            onRunProgressChange(if (sosadMode) 0.08f else 0f)
            insertLoading = true
            if (sosadMode) {
                onRunJobChange(
                    scope.launch {
                        while (insertLoading) {
                            delay(90)
                            onRunProgressChange(nextWorkingProgress(runProgress()))
                        }
                    }
                )
            }
            try {
                yieldToAppUiBeforeHeavyWork()
                val ok = sourcePreview != null || prepareCurrentInsertSource()
                if (ok) {
                    currentRunJob()?.cancel()
                    if (sosadMode) {
                        onRunProgressChange(1f)
                        delay(140)
                    }
                    manualRangePreview = sourcePreview
                        ?: controller.insertChapterSourcePreview
                            ?.takeIf { preview -> preview.sourceType == sourceType }
                    selectedSourceIndices = if (sosadMode) {
                        emptySet()
                    } else {
                        manualRangePreview?.items?.map { it.sourceIndex }?.toSet().orEmpty()
                    }
                    showManualRangeDialog = true
                } else {
                    if (controller.fetchInfoSearchChoiceRequest?.toolId != controller.builtInToolPlanId(tool.id)) {
                        onRunMessage(tool.title to controller.statusMessage.ifBlank { "来源读取失败" })
                    }
                }
            } finally {
                insertLoading = false
                currentRunJob()?.cancel()
                onRunProgressChange(0f)
            }
        }
    }

    if (showManualRangeDialog) {
        val pickerPreview = sourcePreview
            ?: manualRangePreview?.takeIf { preview -> preview.sourceType == sourceType }
        InsertChapterSourcePickerDialog(
            preview = pickerPreview,
            selectedSourceIndices = selectedSourceIndices,
            sourceOrderReversed = insertSourceOrderReversed,
            onToggle = { sourceIndex, checked ->
                selectedSourceIndices = if (checked) {
                    selectedSourceIndices + sourceIndex
                } else {
                    selectedSourceIndices - sourceIndex
                }
            },
            onSelectAll = {
                selectedSourceIndices = pickerPreview?.items?.map { it.sourceIndex }?.toSet().orEmpty()
            },
            onClear = {
                selectedSourceIndices = emptySet()
            },
            onReverseOrder = {
                insertSourceOrderReversed = !insertSourceOrderReversed
            },
            onDismiss = { showManualRangeDialog = false },
            confirmLabel = "确认执行",
            confirmEnabled = tool.implemented &&
                controller.kind != DocumentKind.None &&
                sourceReady &&
                selectedSourceIndices.isNotEmpty() &&
                !controller.busy &&
                !insertLoading,
            confirmRunning = insertLoading,
            confirmProgressLabel = insertProgressLabel(),
            confirmProgress = runProgress(),
            onConfirm = {
                showManualRangeDialog = false
                runInsertChapters(useSelectedSourceIndices = true)
            }
        )
    }
    controller.fetchInfoSearchChoiceRequest
        ?.takeIf { it.toolId == controller.builtInToolPlanId(tool.id) }
        ?.let { request ->
            FetchInfoSearchChoiceDialog(
                controller = controller,
                toolId = request.toolId,
                onDismiss = {
                    controller.clearFetchInfoSearchChoiceRequest(request.toolId)
                },
                preparingLabel = "正在抓取目录",
                onPrepared = {
                    manualRangePreview = controller.insertChapterSourcePreview
                        ?.takeIf { preview -> preview.sourceType == sourceType }
                    selectedSourceIndices = emptySet()
                    showManualRangeDialog = true
                },
                onSelectChoice = { choice ->
                    controller.selectInsertChapterSosadSearchChoice(request.toolId, choice)
                }
            )
        }

    ToolParameterSection(
        controller = controller,
        toolId = tool.id,
        valueFor = { parameter -> controller.builtInToolParameterValue(tool.id, parameter.key) },
        onValueChange = { parameter, value ->
            if (parameter.key == INSERT_CHAPTER_PARAM_SOURCE_TYPE) {
                sourceUri = ""
                sourceDisplayName = ""
                selectedSourceIndices = emptySet()
                manualRangePreview = null
                showManualRangeDialog = false
                controller.clearInsertChapterSourcePreview()
            }
            if (parameter.key in setOf(
                    INSERT_CHAPTER_PARAM_SOSAD_QUERY,
                    INSERT_CHAPTER_PARAM_SOSAD_AUTH_COOKIE,
                    INSERT_CHAPTER_PARAM_SOSAD_RANGE_START,
                    INSERT_CHAPTER_PARAM_SOSAD_RANGE_END
                )
            ) {
                selectedSourceIndices = emptySet()
                manualRangePreview = null
                showManualRangeDialog = false
                controller.clearInsertChapterSourcePreview()
            }
            controller.updateBuiltInToolParameter(tool.id, parameter.key, value)
        },
        insertChapterOptions = ToolParameterInsertChapterOptions(
            sourceFileContent = { modifier ->
                ToolFileButtonField(
                    label = null,
                    value = sourceUri,
                    onPick = { pickInsertSourceFile() },
                    onClear = {
                        sourceUri = ""
                        sourceDisplayName = ""
                        selectedSourceIndices = emptySet()
                        manualRangePreview = null
                        showManualRangeDialog = false
                        controller.clearInsertChapterSourcePreview()
                    },
                    modifier = modifier,
                    displayValue = sourceDisplayName.ifBlank { null }
                )
            },
            useBottomActionForUpload = true
        )
    )
    if (insertLoading) {
        ToolRunProgress(
            toolName = insertProgressLabel(),
            progress = runProgress()
        )
    }
    runningToolName?.let { name ->
        ToolRunProgress(
            toolName = name,
            progress = runProgress()
        )
    }
    ButtonRow {
        val uploadMode = !sosadMode
        Button(
            enabled = tool.implemented &&
                controller.kind != DocumentKind.None &&
                (uploadMode || sourceReady) &&
                !controller.busy &&
                !insertLoading,
            onClick = {
                if (uploadMode) {
                    controller.updateBuiltInToolParameter(tool.id, INSERT_CHAPTER_PARAM_PREVIEW, BOOL_TRUE)
                    pickInsertSourceFile()
                } else if (sosadMode || previewEnabled) {
                    revealInsertPreview()
                } else {
                    runInsertChapters()
                }
            },
            modifier = Modifier.weight(1f),
            shape = ControlShape,
            contentPadding = CompactButtonPadding
        ) {
            Icon(
                if (uploadMode) Icons.Outlined.FolderOpen else Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(if (uploadMode) "选择文件" else if (!sosadMode && previewEnabled) "预览" else "执行")
        }
    }
}
