package com.eteditor

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eteditor.core.DocumentKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun EditorToolEditor(
    controller: EditorController,
    tool: EditorTool,
    documentSessionKey: Int = 0,
    resetKey: Int = 0,
    modifier: Modifier = Modifier,
    isDraft: Boolean,
    onBack: () -> Unit,
    onSaveDraft: () -> Unit,
    onPickTextReplaceRuleFile: TextReplaceRuleFilePicker
) {
    val editorToolTypes = controller.editorToolFunctionOptions().map { it.first }.toSet()
    val baseTool = controller.availableTools.firstOrNull { it.id == tool.toolId && it.id in editorToolTypes }
    if (baseTool == null) {
        LaunchedEffect(tool.id, tool.toolId) {
            if (isDraft) {
                controller.createEditorTool("file_rename")
            } else {
                onBack()
            }
        }
        return
    }
    val isFileRename = baseTool.id == "file_rename"
    val isTextReplace = baseTool.id == "text_replace"
    val isTitleRename = baseTool.id == "chapter_title_rename"
    val isTitleFormat = baseTool.id == "title_format"
    val isFetchInfo = baseTool.id == "fetch_info"
    val isInsertChapter = baseTool.id == "insert_chapter"
    val isCover = baseTool.id == "generate_cover"
    var showFileRenameHelp by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var showFetchInfoHelp by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var showTitleFormatHelp by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var showInsertChapterHelp by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var showCoverHelp by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var fileRenamePreviewToolId by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    var titleRenamePreviewToolId by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    var titleFormatPreviewToolId by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    var textReplacePreviewToolId by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    var fetchInfoPreviewToolId by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    var coverPreviewToolId by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var runningToolName by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    var runProgress by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(0f) }
    var runJob by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<Job?>(null) }
    var fetchInfoLoading by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var runMessage by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<Pair<String, String>?>(null) }
    var showTxtTextReplacePresetDialog by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    fun startDirectRunFeedback(name: String) {
        runJob?.cancel()
        runningToolName = name
        runProgress = 0f
        runJob = scope.launch {
            repeat(20) { index ->
                delay(40)
                runProgress = (index + 1) / 20f
            }
            runProgress = 1f
            delay(160)
            runningToolName = null
            runProgress = 0f
        }
    }

    fun startWorkingProgress(name: String) {
        runJob?.cancel()
        runningToolName = name
        runProgress = 0.08f
        runJob = scope.launch {
            while (runningToolName != null) {
                delay(90)
                runProgress = nextWorkingProgress(runProgress)
            }
        }
    }

    fun stopWorkingProgress() {
        runJob?.cancel()
        runningToolName = null
        runProgress = 0f
    }
    fun updateReplacementPreviewProgress(phase: String, completed: Int, total: Int) {
        val progress = countProgressFraction(completed, total)
        val label = countProgressLabel(phase, completed, total)
        runJob?.cancel()
        runningToolName = label
        runProgress = progress
    }
    val txtSimpleTextReplaceMode = isTextReplace && controller.kind == DocumentKind.Txt
    val editorTextReplaceMode = if (isTextReplace) {
        textReplaceModeForUi(
            controller.editorToolParameterValue(tool, TEXT_REPLACE_PARAM_MODE),
            controller.editorToolParameterValue(tool, TEXT_REPLACE_PARAM_BATCH_SOURCE)
        )
    } else {
        TEXT_REPLACE_MODE_SINGLE
    }
    val editorReplacementMode = isTextReplace && !txtSimpleTextReplaceMode &&
        editorTextReplaceMode == TEXT_REPLACE_MODE_REPLACEMENT
    val previewEnabled = when (baseTool.id) {
        "file_rename" -> controller.editorToolParameterValue(tool, FILE_RENAME_PARAM_PREVIEW) != "false"
        "text_replace" -> if (editorReplacementMode) {
            true
        } else {
            controller.editorToolParameterValue(tool, TEXT_REPLACE_PARAM_PREVIEW)
                .ifBlank { "true" } != "false"
        }
        "chapter_title_rename" -> controller.editorToolParameterValue(tool, TITLE_RENAME_PARAM_PREVIEW) != "false"
        "fetch_info" -> true
        "title_format" -> controller.editorToolParameterValue(tool, TITLE_FORMAT_PARAM_PREVIEW) != "false"
        "insert_chapter" -> false
        "generate_cover" -> controller.coverParameters(tool).preview
        else -> false
    }
    val runButtonLabel = if (previewEnabled) "预览" else "执行"
    fun runEditedToolNow() {
        if (isFetchInfo) {
            runJob?.cancel()
            fetchInfoLoading = true
            runningToolName = controller.fetchInfoInitialProgressTextForEditorTool(tool.id)
            runProgress = 0.08f
            runJob = scope.launch {
                while (fetchInfoLoading) {
                    delay(90)
                    runProgress = nextWorkingProgress(runProgress)
                }
            }
            scope.launch {
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
                        runMessage = baseTool.title to controller.statusMessage.ifBlank { "未生成抓取预览" }
                    }
                } finally {
                    fetchInfoLoading = false
                    runJob?.cancel()
                    runningToolName = null
                    runProgress = 0f
                }
            }
            return
        }
        if (editorReplacementMode) {
            startWorkingProgress("加载预览")
            scope.launch {
                try {
                    yieldToAppUiBeforeHeavyWork()
                    if (controller.prepareReplacementFilePreviewForEditorToolAsync(
                            tool.id,
                            ::updateReplacementPreviewProgress
                        )) {
                        textReplacePreviewToolId = tool.id
                    } else {
                        runMessage = baseTool.title to controller.statusMessage.ifBlank { "未生成替换预览" }
                    }
                } finally {
                    stopWorkingProgress()
                }
            }
            return
        }
        if (isTextReplace || isCover) {
            startWorkingProgress(tool.name.ifBlank { baseTool.title })
            scope.launch {
                var keepDirectFeedback = false
                try {
                    yieldToAppUiBeforeHeavyWork()
                    val started = controller.runConfiguredToolAsync(tool, manual = true)
                    if (!started) {
                        if (isTextReplace) {
                            controller.clearTextReplaceRuntimeFile(tool.id)
                            runMessage = baseTool.title to controller.statusMessage.ifBlank { "文本替换未执行" }
                        }
                        if (isCover) {
                            if (controller.statusMessage == controller.needsConfirmationMessage() && controller.generatedCoverPreview != null) {
                                coverPreviewToolId = tool.id
                            } else {
                                runMessage = baseTool.title to controller.statusMessage.ifBlank { "图片未执行" }
                            }
                        }
                        return@launch
                    }
                    if (isTextReplace &&
                        controller.textSearchToolId == tool.id &&
                        controller.textSearchResults.isNotEmpty()
                    ) {
                        textReplacePreviewToolId = tool.id
                    } else {
                        if (isTextReplace) controller.clearTextReplaceRuntimeFile(tool.id)
                        startDirectRunFeedback(tool.name.ifBlank { baseTool.title })
                        keepDirectFeedback = true
                    }
                } finally {
                    if (!keepDirectFeedback) stopWorkingProgress()
                }
            }
            return
        }
        startWorkingProgress(tool.name.ifBlank { baseTool.title })
        scope.launch {
            var keepDirectFeedback = false
            try {
                yieldToAppUiBeforeHeavyWork()
                val started = controller.runEditorTool(tool.id, manual = true)
                if (!started) {
                    if (isFileRename) {
                        runMessage = baseTool.title to controller.statusMessage.ifBlank { "文件重命名未执行" }
                    }
                    if (isTextReplace) {
                        controller.clearTextReplaceRuntimeFile(tool.id)
                        runMessage = baseTool.title to controller.statusMessage.ifBlank { "文本替换未执行" }
                    }
                    if (isTitleRename) {
                        runMessage = baseTool.title to controller.statusMessage.ifBlank { "标题重命名未执行" }
                    }
                    if (isTitleFormat) {
                        runMessage = baseTool.title to controller.statusMessage.ifBlank { "标题格式未执行" }
                    }
                    if (isInsertChapter) {
                        runMessage = baseTool.title to "请在功能页选择来源文件后执行"
                    }
                    if (isCover) {
                        if (controller.statusMessage == controller.needsConfirmationMessage() && controller.generatedCoverPreview != null) {
                            coverPreviewToolId = tool.id
                        } else {
                            runMessage = baseTool.title to controller.statusMessage.ifBlank { "图片未执行" }
                        }
                    }
                    return@launch
                }
                if (isFileRename &&
                    controller.fileRenamePlanToolId == tool.id &&
                    controller.fileRenamePlan.isNotEmpty()
                ) {
                    fileRenamePreviewToolId = tool.id
                } else if (
                    isTitleRename &&
                    controller.titleRenamePlanToolId == tool.id &&
                    controller.titleRenamePlan.isNotEmpty()
                ) {
                    titleRenamePreviewToolId = tool.id
                } else if (
                    isTitleFormat &&
                    controller.titleFormatPlanToolId == tool.id &&
                    controller.titleFormatPlan.isNotEmpty()
                ) {
                    titleFormatPreviewToolId = tool.id
                } else if (
                    isTextReplace &&
                    controller.textSearchToolId == tool.id &&
                    controller.textSearchResults.isNotEmpty()
                ) {
                    textReplacePreviewToolId = tool.id
                } else if (
                    (!txtSimpleTextReplaceMode || !previewEnabled) &&
                    !(isTextReplace && controller.textSearchToolId == tool.id && controller.textSearchResults.isNotEmpty())
                ) {
                    if (isTextReplace) controller.clearTextReplaceRuntimeFile(tool.id)
                    startDirectRunFeedback(tool.name.ifBlank { baseTool.title })
                    keepDirectFeedback = true
                }
            } finally {
                if (!keepDirectFeedback) stopWorkingProgress()
            }
        }
    }

    fun runEditedTool() {
        if (isTextReplace && editorReplacementMode) {
            onPickTextReplaceRuleFile { uri ->
                controller.updateTextReplaceRuntimeFile(tool.id, uri)
                startWorkingProgress("加载预览")
                scope.launch {
                    try {
                        yieldToAppUiBeforeHeavyWork()
                        if (controller.prepareReplacementFilePreviewForEditorToolAsync(
                                tool.id,
                                ::updateReplacementPreviewProgress
                            )) {
                            textReplacePreviewToolId = tool.id
                        } else {
                            runMessage = baseTool.title to controller.statusMessage.ifBlank { "未生成替换预览" }
                        }
                    } finally {
                        stopWorkingProgress()
                    }
                }
            }
            return
        }
        runEditedToolNow()
    }
    fileRenamePreviewToolId?.let { toolId ->
        FileRenamePlanPane(
            controller = controller,
            toolId = toolId,
            onDismiss = { fileRenamePreviewToolId = null },
            onApplyStarted = { fileRenamePreviewToolId = null },
            modifier = modifier
        )
        return
    }
    titleRenamePreviewToolId?.let { toolId ->
        TitleRenamePlanPane(
            controller = controller,
            toolId = toolId,
            onDismiss = { titleRenamePreviewToolId = null },
            onApplyStarted = { titleRenamePreviewToolId = null },
            modifier = modifier
        )
        return
    }
    titleFormatPreviewToolId?.let { toolId ->
        TitleFormatPlanPane(
            controller = controller,
            toolId = toolId,
            onDismiss = { titleFormatPreviewToolId = null },
            onApplyStarted = { titleFormatPreviewToolId = null },
            modifier = modifier
        )
        return
    }
    textReplacePreviewToolId?.let { toolId ->
        TextSearchResultsPane(
            controller = controller,
            toolId = toolId,
            onDismiss = {
                controller.clearReplacementFilePreview(toolId)
                controller.clearTextReplaceRuntimeFile(toolId)
                textReplacePreviewToolId = null
            },
            onApplyStarted = { textReplacePreviewToolId = null },
            modifier = modifier
        )
        return
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
    coverPreviewToolId?.let {
        GeneratedCoverPreviewDialog(
            preview = controller.generatedCoverPreview,
            onDismiss = {
                coverPreviewToolId = null
                controller.clearGeneratedCoverPreview()
            },
            confirmEnabled = controller.generatedCoverPreview != null && !controller.busy,
            onConfirm = {
                startWorkingProgress("写入中")
                scope.launch {
                    var keepDirectFeedback = false
                    try {
                        yieldToAppUiBeforeHeavyWork()
                        if (controller.applyGeneratedCoverPreview()) {
                            coverPreviewToolId = null
                            startDirectRunFeedback(tool.name.ifBlank { baseTool.title })
                            keepDirectFeedback = true
                        } else {
                            runMessage = baseTool.title to controller.statusMessage.ifBlank { "图片写入失败" }
                        }
                    } finally {
                        if (!keepDirectFeedback) stopWorkingProgress()
                    }
                }
            }
        )
    }
    controller.fetchInfoSearchChoiceRequest
        ?.takeIf { it.toolId == tool.id }
        ?.let { request ->
            FetchInfoSearchChoiceDialog(
                controller = controller,
                toolId = request.toolId,
                onDismiss = { controller.clearFetchInfoSearchChoiceRequest(request.toolId) },
                onPrepared = { fetchInfoPreviewToolId = request.toolId }
            )
        }
    controller.fetchInfoRetryRequest
        ?.takeIf { it.toolId == tool.id }
        ?.let { request ->
            FetchInfoRetryDialog(
                controller = controller,
                toolId = request.toolId,
                onDismiss = { controller.clearFetchInfoRetryRequest(request.toolId) },
                onPrepared = { fetchInfoPreviewToolId = request.toolId }
            )
        }
    ToolDetailTemplate(
        title = "${if (isDraft && controller.creatingToolDraft) "新建预设" else "编辑预设"} / ${baseTool.title}",
        modifier = modifier,
        onBack = onBack,
        onHelp = when {
            isFileRename -> ({ showFileRenameHelp = true })
            isFetchInfo -> ({ showFetchInfoHelp = true })
            isTitleFormat -> ({ showTitleFormatHelp = true })
            isInsertChapter -> ({ showInsertChapterHelp = true })
            isCover -> ({ showCoverHelp = true })
            else -> null
        },
        helpContentDescription = when {
            isFetchInfo -> "抓取范围说明"
            isTitleFormat -> "标题格式说明"
            isInsertChapter -> "插入章节说明"
            isCover -> "封面标题说明"
            else -> "命名格式用法"
        },
        onSave = null,
        saveEnabled = tool.name.isNotBlank()
    ) {
        runningToolName?.let { name ->
            ToolRunProgress(
                toolName = if (isFetchInfo) fetchProgressDisplayText(controller.statusMessage).ifBlank { name } else name,
                progress = if (isFetchInfo) controller.fetchInfoProgress.takeIf { it > 0f } ?: runProgress else runProgress
            )
        }
        ConfiguredToolEditorFields(
            controller = controller,
            tool = tool,
            baseTool = baseTool
        )

        if (!isDraft) {
            ButtonRow {
                Button(
                    enabled = controller.kind != DocumentKind.None && !controller.busy && baseTool.implemented && !fetchInfoLoading,
                    onClick = { runEditedTool() },
                    modifier = Modifier.weight(1f),
                    shape = ControlShape,
                    contentPadding = CompactButtonPadding
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (editorReplacementMode) "选择文件" else runButtonLabel)
                }
                OutlinedButton(
                    onClick = { controller.addEditorToolToSelectedChain(tool.id) },
                    modifier = Modifier.weight(1f),
                    shape = ControlShape,
                    contentPadding = CompactButtonPadding
                ) {
                    Text("加入执行")
                }
            }
        }

    }
    runMessage?.let { (title, message) ->
        ToolRunMessageDialog(
            title = title,
            message = message,
            onDismiss = { runMessage = null }
        )
    }
    if (showFileRenameHelp) {
        FileRenameTemplateHelpDialog(onDismiss = { showFileRenameHelp = false })
    }
    if (showFetchInfoHelp) {
        FetchInfoCatalogScopeHelpDialog(onDismiss = { showFetchInfoHelp = false })
    }
    if (showTitleFormatHelp) {
        TitleFormatHelpDialog(onDismiss = { showTitleFormatHelp = false })
    }
    if (showInsertChapterHelp) {
        InsertChapterHelpDialog(onDismiss = { showInsertChapterHelp = false })
    }
    if (showCoverHelp) {
        CoverTitleHelpDialog(onDismiss = { showCoverHelp = false })
    }
}
