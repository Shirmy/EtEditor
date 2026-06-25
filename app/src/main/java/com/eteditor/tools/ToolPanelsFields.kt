package com.eteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun ToolsPanel(
    controller: EditorController,
    onSave: () -> Unit,
    onPickTextReplaceRuleFile: TextReplaceRuleFilePicker,
    onCreateTool: ((String) -> Unit)? = null,
    onEditTool: ((String) -> Unit)? = null,
    resetKey: Int = 0,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    var editingTool by remember(resetKey) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var runningToolId by remember(resetKey) { mutableStateOf<String?>(null) }
    var runningToolName by remember(resetKey) { mutableStateOf("") }
    var runProgress by remember(resetKey) { mutableStateOf(0f) }
    var runJob by remember(resetKey) { mutableStateOf<Job?>(null) }
    var fetchInfoRunJob by remember(resetKey) { mutableStateOf<Job?>(null) }
    var fileRenamePreviewToolId by remember(resetKey) { mutableStateOf<String?>(null) }
    var titleRenamePreviewToolId by remember(resetKey) { mutableStateOf<String?>(null) }
    var titleFormatPreviewToolId by remember(resetKey) { mutableStateOf<String?>(null) }
    var textReplacePreviewToolId by remember(resetKey) { mutableStateOf<String?>(null) }
    var fetchInfoPreviewToolId by remember(resetKey) { mutableStateOf<String?>(null) }
    var coverPreviewToolId by remember(resetKey) { mutableStateOf<String?>(null) }
    var toolRunMessage by remember(resetKey) { mutableStateOf<Pair<String, String>?>(null) }
    val tool = controller.selectedEditorTool
    val showToolEditor = editingTool || controller.editingToolDraft

    fun startToolWorkingProgress(toolId: String, name: String) {
        runJob?.cancel()
        fetchInfoRunJob?.cancel()
        runningToolId = toolId
        runningToolName = name
        runProgress = 0.08f
        runJob = scope.launch {
            while (runningToolId == toolId) {
                delay(90)
                runProgress = nextWorkingProgress(runProgress)
            }
        }
    }

    fun stopToolWorkingProgress() {
        runJob?.cancel()
        runningToolId = null
        runningToolName = ""
        runProgress = 0f
    }

    fun updateReplacementPreviewProgress(toolId: String, phase: String, completed: Int, total: Int) {
        val progress = countProgressFraction(completed, total)
        val label = countProgressLabel(phase, completed, total)
        runJob?.cancel()
        runningToolId = toolId
        runningToolName = label
        runProgress = progress
    }

    fun startToolRunNow(tool: EditorTool) {
        startToolWorkingProgress(tool.id, tool.name.ifBlank { controller.toolLabel(tool.toolId) })
        scope.launch {
            var keepDirectFeedback = false
            try {
                yieldToAppUiBeforeHeavyWork()
                val started = controller.runEditorTool(tool.id, manual = true)
                if (!started) {
                    if (tool.toolId == "file_rename") {
                        toolRunMessage = tool.name.ifBlank { "文件重命名" } to
                            controller.statusMessage.ifBlank { "文件重命名未执行" }
                    }
                    if (tool.toolId == "text_replace") {
                        controller.clearTextReplaceRuntimeFile(tool.id)
                        toolRunMessage = tool.name.ifBlank { "文本替换" } to
                            controller.statusMessage.ifBlank { "文本替换未执行" }
                    }
                    if (tool.toolId == "chapter_title_rename") {
                        toolRunMessage = tool.name.ifBlank { "标题重命名" } to
                            controller.statusMessage.ifBlank { "标题重命名未执行" }
                    }
                    if (tool.toolId == "title_format") {
                        toolRunMessage = tool.name.ifBlank { "标题格式" } to
                            controller.statusMessage.ifBlank { "标题格式未执行" }
                    }
                    if (tool.toolId == "insert_chapter") {
                        toolRunMessage = tool.name.ifBlank { "插入章节" } to "请在功能页选择来源文件后执行"
                    }
                    if (tool.toolId == "generate_cover") {
                        if (controller.statusMessage == controller.needsConfirmationMessage() && controller.generatedCoverPreview != null) {
                            coverPreviewToolId = tool.id
                        } else {
                            toolRunMessage = tool.name.ifBlank { "插入图片" } to
                                controller.statusMessage.ifBlank { "图片未执行" }
                        }
                    }
                    return@launch
                }
                if (tool.toolId == "file_rename" &&
                    controller.fileRenamePlanToolId == tool.id &&
                    controller.fileRenamePlan.isNotEmpty()
                ) {
                    fileRenamePreviewToolId = tool.id
                    return@launch
                }
                if (tool.toolId == "chapter_title_rename" &&
                    controller.titleRenamePlanToolId == tool.id &&
                    controller.titleRenamePlan.isNotEmpty()
                ) {
                    titleRenamePreviewToolId = tool.id
                    return@launch
                }
                if (tool.toolId == "title_format" &&
                    controller.titleFormatPlanToolId == tool.id &&
                    controller.titleFormatPlan.isNotEmpty()
                ) {
                    titleFormatPreviewToolId = tool.id
                    return@launch
                }
                if (tool.toolId == "text_replace" &&
                    controller.textSearchToolId == tool.id &&
                    controller.textSearchResults.isNotEmpty()
                ) {
                    textReplacePreviewToolId = tool.id
                    return@launch
                }
                if (tool.toolId == "text_replace") {
                    controller.clearTextReplaceRuntimeFile(tool.id)
                }
                runJob?.cancel()
                runningToolId = tool.id
                runningToolName = tool.name
                runProgress = 0f
                runJob = scope.launch {
                    repeat(20) { index ->
                        delay(45)
                        runProgress = (index + 1) / 20f
                    }
                    delay(180)
                    stopToolWorkingProgress()
                }
                keepDirectFeedback = true
            } finally {
                if (!keepDirectFeedback) stopToolWorkingProgress()
            }
        }
    }

    fun startToolRun(tool: EditorTool) {
        if (tool.toolId == "fetch_info") {
            runJob?.cancel()
            fetchInfoRunJob?.cancel()
            runningToolId = tool.id
            runningToolName = controller.fetchInfoInitialProgressTextForEditorTool(tool.id)
            runProgress = 0.08f
            runJob = scope.launch {
                while (true) {
                    delay(90)
                    runProgress = nextWorkingProgress(runProgress)
                }
            }
            fetchInfoRunJob = scope.launch {
                var prepared = false
                try {
                    yieldToAppUiBeforeHeavyWork()
                    prepared = controller.prepareFetchInfoPreviewForEditorTool(tool.id)
                    runJob?.cancel()
                    runProgress = 1f
                    delay(160)
                    if (prepared) {
                        fetchInfoPreviewToolId = tool.id
                    } else if (
                        controller.fetchInfoSearchChoiceRequest?.toolId != tool.id &&
                        controller.fetchInfoRetryRequest?.toolId != tool.id
                    ) {
                        toolRunMessage = tool.name.ifBlank { "抓取信息" } to
                            controller.statusMessage.ifBlank { "未生成抓取预览" }
                    }
                } finally {
                    runJob?.cancel()
                    runningToolId = null
                    runningToolName = ""
                    runProgress = 0f
                }
            }
            return
        }
        if (controller.editorToolNeedsTextReplaceRuleFile(tool)) {
            onPickTextReplaceRuleFile { uri ->
                controller.updateTextReplaceRuntimeFile(tool.id, uri)
                startToolWorkingProgress(tool.id, "加载预览")
                scope.launch {
                    try {
                        yieldToAppUiBeforeHeavyWork()
                        if (controller.prepareReplacementFilePreviewForEditorToolAsync(
                                tool.id,
                                { phase, completed, total ->
                                    updateReplacementPreviewProgress(tool.id, phase, completed, total)
                                }
                            )) {
                            textReplacePreviewToolId = tool.id
                        } else {
                            toolRunMessage = tool.name.ifBlank { "文本替换" } to
                                controller.statusMessage.ifBlank { "未生成替换预览" }
                        }
                    } finally {
                        stopToolWorkingProgress()
                    }
                }
            }
            return
        }
        val textReplaceMode = if (tool.toolId == "text_replace") {
            textReplaceModeForUi(
                controller.editorToolParameterValue(tool, TEXT_REPLACE_PARAM_MODE),
                controller.editorToolParameterValue(tool, TEXT_REPLACE_PARAM_BATCH_SOURCE)
            )
        } else {
            TEXT_REPLACE_MODE_SINGLE
        }
        if (tool.toolId == "text_replace" && textReplaceMode == TEXT_REPLACE_MODE_REPLACEMENT) {
            onPickTextReplaceRuleFile { uri ->
                controller.updateTextReplaceRuntimeFile(tool.id, uri)
                startToolWorkingProgress(tool.id, "加载预览")
                scope.launch {
                    try {
                        yieldToAppUiBeforeHeavyWork()
                        if (controller.prepareReplacementFilePreviewForEditorToolAsync(
                                tool.id,
                                { phase, completed, total ->
                                    updateReplacementPreviewProgress(tool.id, phase, completed, total)
                                }
                            )) {
                            textReplacePreviewToolId = tool.id
                        } else {
                            toolRunMessage = tool.name.ifBlank { "文本替换" } to
                                controller.statusMessage.ifBlank { "未生成替换预览" }
                        }
                    } finally {
                        stopToolWorkingProgress()
                    }
                }
            }
            return
        }
        if (tool.toolId == "text_replace" || tool.toolId == "generate_cover") {
            startToolWorkingProgress(tool.id, tool.name.ifBlank { controller.toolLabel(tool.toolId) })
            scope.launch {
                var keepDirectFeedback = false
                try {
                    yieldToAppUiBeforeHeavyWork()
                    val started = controller.runConfiguredToolAsync(tool, manual = true)
                    if (!started) {
                        if (tool.toolId == "text_replace") {
                            controller.clearTextReplaceRuntimeFile(tool.id)
                            toolRunMessage = tool.name.ifBlank { "文本替换" } to
                                controller.statusMessage.ifBlank { "文本替换未执行" }
                        }
                        if (tool.toolId == "generate_cover") {
                            if (controller.statusMessage == controller.needsConfirmationMessage() && controller.generatedCoverPreview != null) {
                                coverPreviewToolId = tool.id
                            } else {
                                toolRunMessage = tool.name.ifBlank { "插入图片" } to
                                    controller.statusMessage.ifBlank { "图片未执行" }
                            }
                        }
                        return@launch
                    }
                    if (tool.toolId == "text_replace" &&
                        controller.textSearchToolId == tool.id &&
                        controller.textSearchResults.isNotEmpty()
                    ) {
                        textReplacePreviewToolId = tool.id
                    } else {
                        if (tool.toolId == "text_replace") controller.clearTextReplaceRuntimeFile(tool.id)
                        runJob?.cancel()
                        runningToolId = tool.id
                        runningToolName = tool.name
                        runProgress = 0f
                        runJob = scope.launch {
                            repeat(20) { index ->
                                delay(45)
                                runProgress = (index + 1) / 20f
                            }
                            delay(180)
                            stopToolWorkingProgress()
                        }
                        keepDirectFeedback = true
                    }
                } finally {
                    if (!keepDirectFeedback) stopToolWorkingProgress()
                }
            }
            return
        }
        startToolRunNow(tool)
    }

    fun createPreset() {
        val defaultToolId = controller.editorToolFunctionOptions().firstOrNull()?.first ?: return
        if (onCreateTool != null) {
            onCreateTool(defaultToolId)
        } else {
            controller.createEditorTool(defaultToolId)
            editingTool = true
        }
    }

    LaunchedEffect(controller.editingToolDraft, tool?.id) {
        if (controller.editingToolDraft && tool != null) {
            editingTool = true
        }
    }
    LaunchedEffect(resetKey) {
        runJob?.cancel()
        fetchInfoRunJob?.cancel()
        runningToolId = null
        runningToolName = ""
        runProgress = 0f
        fileRenamePreviewToolId = null
        titleRenamePreviewToolId = null
        titleFormatPreviewToolId = null
        textReplacePreviewToolId = null
        fetchInfoPreviewToolId = null
        coverPreviewToolId = null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(if (compact) 0.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        val activeFileRenamePreviewToolId = fileRenamePreviewToolId
        val activeTitleRenamePreviewToolId = titleRenamePreviewToolId
        val activeTitleFormatPreviewToolId = titleFormatPreviewToolId
        val activeTextReplacePreviewToolId = textReplacePreviewToolId
        if (activeFileRenamePreviewToolId != null) {
            FileRenamePlanPane(
                controller = controller,
                toolId = activeFileRenamePreviewToolId,
                onDismiss = { fileRenamePreviewToolId = null },
                onApplyStarted = { fileRenamePreviewToolId = null },
                modifier = Modifier.weight(1f)
            )
        } else if (activeTitleRenamePreviewToolId != null) {
            TitleRenamePlanPane(
                controller = controller,
                toolId = activeTitleRenamePreviewToolId,
                onDismiss = { titleRenamePreviewToolId = null },
                onApplyStarted = { titleRenamePreviewToolId = null },
                modifier = Modifier.weight(1f)
            )
        } else if (activeTitleFormatPreviewToolId != null) {
            TitleFormatPlanPane(
                controller = controller,
                toolId = activeTitleFormatPreviewToolId,
                onDismiss = { titleFormatPreviewToolId = null },
                onApplyStarted = { titleFormatPreviewToolId = null },
                modifier = Modifier.weight(1f)
            )
        } else if (activeTextReplacePreviewToolId != null) {
            val previewTool = controller.editorTools.firstOrNull { it.id == activeTextReplacePreviewToolId }
            TextSearchResultsPane(
                controller = controller,
                toolId = activeTextReplacePreviewToolId,
                onDismiss = {
                    controller.clearReplacementFilePreview(activeTextReplacePreviewToolId)
                    controller.clearTextReplaceRuntimeFile(activeTextReplacePreviewToolId)
                    textReplacePreviewToolId = null
                },
                onApplyStarted = { textReplacePreviewToolId = null },
                modifier = Modifier.weight(1f)
            )
        } else {
            if (!showToolEditor) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "预设",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    IconButton(
                        onClick = { createPreset() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(34.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "新建预设")
                    }
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
            }
            if (runningToolId != null) {
                val progressValue = if (tool?.toolId == "fetch_info") {
                    controller.fetchInfoProgress.takeIf { it > 0f } ?: runProgress
                } else {
                    runProgress
                }
                ToolRunProgress(
                    toolName = fetchProgressDisplayText(controller.statusMessage).ifBlank { runningToolName },
                    progress = progressValue
                )
            }

            if (showToolEditor && tool != null) {
                EditorToolEditor(
                    controller = controller,
                    tool = tool,
                    documentSessionKey = controller.documentSessionKey,
                    resetKey = resetKey,
                    modifier = Modifier.weight(1f),
                    isDraft = controller.editingToolDraft,
                    onBack = {
                        if (controller.editingToolDraft) controller.discardEditorToolDraft()
                        editingTool = false
                    },
                    onSaveDraft = {
                        if (controller.saveEditorToolDraft()) {
                            editingTool = false
                        } else {
                            toolRunMessage = (tool.name.ifBlank { "预设" }) to
                                controller.statusMessage.ifBlank { "无法保存预设" }
                        }
                    },
                    onPickTextReplaceRuleFile = onPickTextReplaceRuleFile
                )
            } else {
                EditorToolLibraryList(
                    controller = controller,
                    modifier = Modifier.weight(1f),
                    runningToolId = runningToolId,
                    onRunTool = ::startToolRun,
                    onEditTool = { toolId ->
                        if (onEditTool != null) {
                            onEditTool(toolId)
                        } else {
                            controller.selectEditorTool(toolId)
                            editingTool = true
                        }
                    },
                    onDeleteTool = { toolId ->
                        if (controller.deleteEditorTool(toolId) && runningToolId == toolId) {
                            runJob?.cancel()
                            runningToolId = null
                            runningToolName = ""
                            runProgress = 0f
                        }
                    }
                )
            }
        }
    }
    fetchInfoPreviewToolId?.let { toolId ->
        FetchInfoPreviewDialog(
            controller = controller,
            toolId = toolId,
            onDismiss = {
                controller.clearFetchInfoPreview(toolId)
                fetchInfoPreviewToolId = null
            },
            onApplyStarted = { fetchInfoPreviewToolId = null },
            onReselected = { fetchInfoPreviewToolId = null }
        )
    }
    coverPreviewToolId?.let { toolId ->
        val previewTool = controller.editorTools.firstOrNull { it.id == toolId }
        GeneratedCoverPreviewDialog(
            preview = controller.generatedCoverPreview,
            onDismiss = {
                coverPreviewToolId = null
                controller.clearGeneratedCoverPreview()
            },
            confirmEnabled = controller.generatedCoverPreview != null && !controller.busy,
            onConfirm = {
                startToolWorkingProgress(toolId, "写入中")
                scope.launch {
                    var keepDirectFeedback = false
                    try {
                        yieldToAppUiBeforeHeavyWork()
                        if (controller.applyGeneratedCoverPreview()) {
                            coverPreviewToolId = null
                            runJob?.cancel()
                            runningToolId = toolId
                            runningToolName = previewTool?.name.orEmpty().ifBlank { "插入图片" }
                            runProgress = 0f
                            runJob = scope.launch {
                                repeat(20) { index ->
                                    delay(45)
                                    runProgress = (index + 1) / 20f
                                }
                                delay(180)
                                stopToolWorkingProgress()
                            }
                            keepDirectFeedback = true
                        } else {
                            toolRunMessage = previewTool?.name.orEmpty().ifBlank { "插入图片" } to
                                controller.statusMessage.ifBlank { "图片写入失败" }
                        }
                    } finally {
                        if (!keepDirectFeedback) stopToolWorkingProgress()
                    }
                }
            }
        )
    }
    if (!showToolEditor) {
        controller.fetchInfoSearchChoiceRequest
            ?.takeIf { request -> controller.editorTools.any { it.id == request.toolId } }
            ?.let { request ->
                FetchInfoSearchChoiceDialog(
                    controller = controller,
                    toolId = request.toolId,
                    onDismiss = { controller.clearFetchInfoSearchChoiceRequest(request.toolId) },
                    onPrepared = { fetchInfoPreviewToolId = request.toolId },
                    onManualUrl = { controller.openFetchInfoManualUrlRetry(request.toolId) }
                )
            }
        controller.fetchInfoRetryRequest
            ?.takeIf { request -> controller.editorTools.any { it.id == request.toolId } }
            ?.let { request ->
                FetchInfoRetryDialog(
                    controller = controller,
                    toolId = request.toolId,
                    onDismiss = { controller.clearFetchInfoRetryRequest(request.toolId) },
                    onPrepared = { fetchInfoPreviewToolId = request.toolId }
                )
            }
    }
    toolRunMessage?.let { (title, message) ->
        ToolRunMessageDialog(
            title = title,
            message = message,
            onDismiss = { toolRunMessage = null }
        )
    }
}

@Composable
internal fun ToolEditorPanel(
    controller: EditorController,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSaveDraft: () -> Unit,
    onPickTextReplaceRuleFile: TextReplaceRuleFilePicker
) {
    val tool = controller.selectedEditorTool
    if (tool == null) {
        LaunchedEffect(Unit) {
            controller.createEditorTool("file_rename")
        }
        Box(modifier = modifier.fillMaxSize())
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        EditorToolEditor(
            controller = controller,
            tool = tool,
            documentSessionKey = controller.documentSessionKey,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (controller.editingToolDraft) 52.dp else 0.dp),
            isDraft = controller.editingToolDraft,
            onBack = {
                if (controller.editingToolDraft) controller.discardEditorToolDraft()
                onBack()
            },
            onSaveDraft = onSaveDraft,
            onPickTextReplaceRuleFile = onPickTextReplaceRuleFile
        )
        if (controller.editingToolDraft) {
            Button(
                enabled = tool.name.isNotBlank(),
                onClick = onSaveDraft,
                shape = ControlShape,
                contentPadding = CompactButtonPadding,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("保存")
            }
        }
    }
}
