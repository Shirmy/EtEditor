package com.eteditor

import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Build as BuildIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eteditor.core.DocumentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

@Composable
internal fun ToolParameterPanel(
    controller: EditorController,
    tool: ToolDefinition,
    documentSessionKey: Int = 0,
    resetKey: Int = 0,
    onSave: () -> Unit,
    onFileRenamePreview: (String) -> Unit,
    onTitleRenamePreview: (String) -> Unit,
    onTitleFormatPreview: (String) -> Unit,
    onTextReplacePreview: (String) -> Unit,
    onFetchInfoPreview: (String) -> Unit,
    onPickTextReplaceRuleFile: TextReplaceRuleFilePicker
) {
    var fileRenameRunMessage by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var runningToolName by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    var runProgress by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(0f) }
    var runJob by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<Job?>(null) }
    var fetchInfoLoading by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var runMessage by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<Pair<String, String>?>(null) }

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

    when (tool.id) {
        "file_rename" -> {
            val previewEnabled = controller
                .builtInToolParameterValue(tool.id, FILE_RENAME_PARAM_PREVIEW)
                .ifBlank { "true" } != "false"
            ToolParameterSection(
                controller = controller,
                toolId = tool.id,
                valueFor = { parameter -> controller.builtInToolParameterValue(tool.id, parameter.key) },
                onValueChange = { parameter, value -> controller.updateBuiltInToolParameter(tool.id, parameter.key, value) }
            )
            runningToolName?.let { name ->
                ToolRunProgress(
                    toolName = name,
                    progress = runProgress
                )
            }
            ButtonRow {
                Button(
                    enabled = controller.kind == DocumentKind.Epub && controller.chapters.isNotEmpty() && !controller.busy,
                    onClick = {
                        val planToolId = controller.builtInToolPlanId(tool.id)
                        startWorkingProgress(tool.title)
                        scope.launch {
                            var keepDirectFeedback = false
                            try {
                                yieldToAppUiBeforeHeavyWork()
                                val started = controller.runBuiltInTool(tool.id, manual = true)
                                if (!started) {
                                    fileRenameRunMessage = controller.statusMessage.ifBlank { "未生成命名计划" }
                                } else if (
                                    controller.fileRenamePlanToolId == planToolId &&
                                    controller.fileRenamePlan.isNotEmpty()
                                ) {
                                    onFileRenamePreview(planToolId)
                                } else {
                                    startDirectRunFeedback(tool.title)
                                    keepDirectFeedback = true
                                }
                            } finally {
                                if (!keepDirectFeedback) stopWorkingProgress()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = ControlShape,
                    contentPadding = CompactButtonPadding
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (previewEnabled) "预览" else "执行")
                }
            }
        }
        "text_replace" -> {
            val txtSimpleMode = controller.kind == DocumentKind.Txt
            val currentRawMode = controller.builtInToolParameterValue(tool.id, TEXT_REPLACE_PARAM_MODE)
            val currentBatchSource = controller.builtInToolParameterValue(tool.id, TEXT_REPLACE_PARAM_BATCH_SOURCE)
            val currentMode = textReplaceModeForUi(currentRawMode, currentBatchSource)
            val epubSingleMode = controller.kind == DocumentKind.Epub &&
                currentMode == TEXT_REPLACE_MODE_SINGLE
            val showSearchPresetSave = txtSimpleMode || epubSingleMode
            var showTextReplaceSavePresetDialog by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
            val replacementMode = !txtSimpleMode &&
                currentMode == TEXT_REPLACE_MODE_REPLACEMENT
            val previewEnabled = if (replacementMode) {
                true
            } else {
                controller.builtInToolParameterValue(tool.id, TEXT_REPLACE_PARAM_PREVIEW)
                    .ifBlank { "true" } != "false"
            }
            fun runTextReplaceNow() {
                if (replacementMode) {
                    startWorkingProgress("加载预览")
                    scope.launch {
                        var ready = false
                        try {
                            yieldToAppUiBeforeHeavyWork()
                            ready = controller.prepareReplacementFilePreviewForBuiltInAsync(
                                tool.id,
                                ::updateReplacementPreviewProgress
                            )
                            runJob?.cancel()
                            runProgress = 1f
                            delay(160)
                            if (ready) {
                                onTextReplacePreview(controller.builtInToolPlanId(tool.id))
                            } else {
                                runMessage = tool.title to controller.statusMessage.ifBlank { "未生成替换预览" }
                            }
                        } finally {
                            stopWorkingProgress()
                        }
                    }
                    return
                }
                val previewToolId = controller.builtInToolPlanId(tool.id)
                startWorkingProgress(tool.title)
                scope.launch {
                    var started = false
                    var keepDirectFeedback = false
                    try {
                        yieldToAppUiBeforeHeavyWork()
                        started = controller.runConfiguredToolAsync(controller.builtInEditorTool(tool.id), manual = true)
                        if (started &&
                            previewEnabled &&
                            controller.textSearchToolId == previewToolId &&
                            controller.textSearchResults.isNotEmpty()
                        ) {
                            onTextReplacePreview(previewToolId)
                        } else if (started && (!txtSimpleMode || !previewEnabled)) {
                            startDirectRunFeedback(tool.title)
                            keepDirectFeedback = true
                        } else {
                            runMessage = tool.title to controller.statusMessage.ifBlank { "文本替换未执行" }
                        }
                    } finally {
                        if (!keepDirectFeedback) {
                            stopWorkingProgress()
                        }
                    }
                }
            }
            ToolParameterSection(
                controller = controller,
                toolId = tool.id,
                valueFor = { parameter -> controller.builtInToolParameterValue(tool.id, parameter.key) },
                onValueChange = { parameter, value -> controller.updateBuiltInToolParameter(tool.id, parameter.key, value) }
            )
            runningToolName?.let { name ->
                ToolRunProgress(
                    toolName = name,
                    progress = runProgress
                )
            }
            ButtonRow {
                if (showSearchPresetSave) {
                    OutlinedButton(
                        onClick = { showTextReplaceSavePresetDialog = true },
                        enabled = tool.implemented && controller.kind != DocumentKind.None && !controller.busy,
                        modifier = Modifier.weight(1f),
                        shape = ControlShape,
                        contentPadding = CompactButtonPadding
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("保存预设")
                    }
                }
                Button(
                    enabled = tool.implemented && controller.kind != DocumentKind.None && !controller.busy,
                    onClick = {
                        if (replacementMode) {
                            onPickTextReplaceRuleFile { uri ->
                                controller.updateTextReplaceRuntimeFile(controller.builtInToolPlanId(tool.id), uri)
                                runTextReplaceNow()
                            }
                        } else if (controller.builtInTextReplaceNeedsRuleFile(tool.id)) {
                            onPickTextReplaceRuleFile { uri ->
                                controller.updateTextReplaceRuntimeFile(controller.builtInToolPlanId(tool.id), uri)
                                runTextReplaceNow()
                            }
                        } else {
                            runTextReplaceNow()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = ControlShape,
                    contentPadding = CompactButtonPadding
                ) {
                    Icon(Icons.Outlined.FindReplace, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (replacementMode) "选择文件" else if (previewEnabled) "预览" else "执行")
                }
            }
            if (showTextReplaceSavePresetDialog) {
                SavePresetNameDialog(
                    initialName = "",
                    onDismiss = { showTextReplaceSavePresetDialog = false },
                    onConfirm = { presetName ->
                        val saved = if (txtSimpleMode) {
                            controller.saveTxtTextReplacePreset(presetName)
                        } else {
                            controller.saveEpubTextReplacePreset(presetName)
                        }
                        if (saved) {
                            showTextReplaceSavePresetDialog = false
                        }
                    }
                )
            }
        }
        "chapter_title_rename" -> {
            val previewEnabled = controller
                .builtInToolParameterValue(tool.id, TITLE_RENAME_PARAM_PREVIEW)
                .ifBlank { "true" } != "false"
            ToolParameterSection(
                controller = controller,
                toolId = tool.id,
                valueFor = { parameter -> controller.builtInToolParameterValue(tool.id, parameter.key) },
                onValueChange = { parameter, value -> controller.updateBuiltInToolParameter(tool.id, parameter.key, value) }
            )
            runningToolName?.let { name ->
                ToolRunProgress(
                    toolName = name,
                    progress = runProgress
                )
            }
            ButtonRow {
                Button(
                    enabled = tool.implemented && controller.chapters.isNotEmpty() && !controller.busy,
                    onClick = {
                        val planToolId = controller.builtInToolPlanId(tool.id)
                        startWorkingProgress(tool.title)
                        scope.launch {
                            var keepDirectFeedback = false
                            try {
                                yieldToAppUiBeforeHeavyWork()
                                val started = controller.runBuiltInTool(tool.id, manual = true)
                                if (started &&
                                    controller.titleRenamePlanToolId == planToolId &&
                                    controller.titleRenamePlan.isNotEmpty()
                                ) {
                                    onTitleRenamePreview(planToolId)
                                } else if (started) {
                                    startDirectRunFeedback(tool.title)
                                    keepDirectFeedback = true
                                } else {
                                    runMessage = tool.title to controller.statusMessage.ifBlank { "未执行标题重命名" }
                                }
                            } finally {
                                if (!keepDirectFeedback) stopWorkingProgress()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = ControlShape,
                    contentPadding = CompactButtonPadding
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (previewEnabled) "预览" else "执行")
                }
            }
        }
        "title_format" -> {
            val selectedHtmlScope = controller
                .builtInToolParameterValue(tool.id, TITLE_FORMAT_PARAM_SCOPE)
                .ifBlank { TITLE_FORMAT_SCOPE_ALL } == TITLE_FORMAT_SCOPE_SELECTED
            val previewEnabled = if (selectedHtmlScope) {
                true
            } else {
                controller
                    .builtInToolParameterValue(tool.id, TITLE_FORMAT_PARAM_PREVIEW)
                    .ifBlank { "true" } != "false"
            }
            var chapterPickerRequestKey by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(0) }
            fun updateTitleFormatProgress(completed: Int, total: Int) {
                runJob?.cancel()
                runningToolName = countProgressLabel(tool.title, completed, total)
                runProgress = countProgressFraction(completed, total)
            }
            fun runTitleFormatNow() {
                if (selectedHtmlScope) {
                    controller.updateBuiltInToolParameter(tool.id, TITLE_FORMAT_PARAM_PREVIEW, BOOL_TRUE)
                }
                val planToolId = controller.builtInToolPlanId(tool.id)
                startWorkingProgress(tool.title)
                scope.launch {
                    var keepDirectFeedback = false
                    try {
                        yieldToAppUiBeforeHeavyWork()
                        val started = controller.runBuiltInTool(tool.id, manual = true)
                        if (started &&
                            controller.titleFormatPlanToolId == planToolId &&
                            controller.titleFormatPlan.isNotEmpty()
                        ) {
                            if (selectedHtmlScope) {
                                val applied = controller.applyPreparedTitleFormatPlanWithProgress(
                                    planToolId,
                                    ::updateTitleFormatProgress
                                )
                                if (applied) {
                                    startDirectRunFeedback(tool.title)
                                    keepDirectFeedback = true
                                } else {
                                    runMessage = tool.title to controller.statusMessage.ifBlank { "未执行标题格式" }
                                }
                            } else {
                                onTitleFormatPreview(planToolId)
                            }
                        } else if (started) {
                            startDirectRunFeedback(tool.title)
                            keepDirectFeedback = true
                        } else {
                            runMessage = tool.title to controller.statusMessage.ifBlank { "未执行标题格式" }
                        }
                    } finally {
                        if (!keepDirectFeedback) stopWorkingProgress()
                    }
                }
            }
            ToolParameterSection(
                controller = controller,
                toolId = tool.id,
                valueFor = { parameter -> controller.builtInToolParameterValue(tool.id, parameter.key) },
                onValueChange = { parameter, value -> controller.updateBuiltInToolParameter(tool.id, parameter.key, value) },
                titleFormatOptions = ToolParameterTitleFormatOptions(
                    useBottomActionForSelectedScope = true,
                    chapterPickerRequestKey = chapterPickerRequestKey,
                    onSelectedChaptersConfirmed = { runTitleFormatNow() }
                )
            )
            runningToolName?.let { name ->
                ToolRunProgress(
                    toolName = name,
                    progress = runProgress
                )
            }
            ButtonRow {
                Button(
                    enabled = tool.implemented && controller.chapters.isNotEmpty() && !controller.busy,
                    onClick = {
                        if (selectedHtmlScope) {
                            chapterPickerRequestKey += 1
                        } else {
                            runTitleFormatNow()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = ControlShape,
                    contentPadding = CompactButtonPadding
                ) {
                    Icon(Icons.Outlined.BuildIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (selectedHtmlScope) "选择HTML章节" else if (previewEnabled) "预览" else "执行")
                }
            }
        }
        "fetch_info" -> {
            ToolParameterSection(
                controller = controller,
                toolId = tool.id,
                valueFor = { parameter -> controller.builtInToolParameterValue(tool.id, parameter.key) },
                onValueChange = { parameter, value -> controller.updateBuiltInToolParameter(tool.id, parameter.key, value) }
            )
            if (fetchInfoLoading) {
                ToolRunProgress(
                    toolName = fetchProgressDisplayText(controller.statusMessage)
                        .ifBlank { controller.fetchInfoInitialProgressTextForBuiltIn(tool.id) },
                    progress = controller.fetchInfoProgress.takeIf { it > 0f } ?: runProgress
                )
            }
            ButtonRow {
                Button(
                    enabled = tool.implemented && controller.kind == DocumentKind.Epub && !controller.busy && !fetchInfoLoading,
                    onClick = {
                        runJob?.cancel()
                        fetchInfoLoading = true
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
                                prepared = controller.prepareFetchInfoPreviewForBuiltIn(tool.id)
                                runJob?.cancel()
                                runProgress = 1f
                                delay(160)
                                if (prepared) {
                                    onFetchInfoPreview(controller.builtInToolPlanId(tool.id))
                                } else if (
                                    controller.fetchInfoSearchChoiceRequest?.toolId != controller.builtInToolPlanId(tool.id) &&
                                    controller.fetchInfoRetryRequest?.toolId != controller.builtInToolPlanId(tool.id)
                                ) {
                                    runMessage = tool.title to controller.statusMessage.ifBlank { "未生成抓取预览" }
                                }
                            } finally {
                                fetchInfoLoading = false
                                runJob?.cancel()
                                runProgress = 0f
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = ControlShape,
                    contentPadding = CompactButtonPadding
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("预览")
                }
            }
        }
        "insert_chapter" -> {
            InsertChapterToolParameterPanel(
                controller = controller,
                tool = tool,
                documentSessionKey = documentSessionKey,
                resetKey = resetKey,
                scope = scope,
                runningToolName = runningToolName,
                runProgress = { runProgress },
                currentRunJob = { runJob },
                onRunProgressChange = { progress -> runProgress = progress },
                onRunJobChange = { job -> runJob = job },
                startDirectRunFeedback = { name -> startDirectRunFeedback(name) },
                onRunMessage = { message -> runMessage = message }
            )
        }
        "generate_cover" -> {
            GenerateCoverToolParameterPanel(
                controller = controller,
                tool = tool,
                documentSessionKey = documentSessionKey,
                resetKey = resetKey,
                startDirectRunFeedback = { name -> startDirectRunFeedback(name) },
                onRunMessage = { message -> runMessage = message }
            )
        }
        else -> {
            NativeFormSection("普通参数") {
                Text(
                    text = "框架已建立：后续会在这里放这个功能自己的参数、预览和执行反馈。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    fileRenameRunMessage?.let { message ->
        FileRenameRunMessageDialog(
            message = message,
            onDismiss = { fileRenameRunMessage = null }
        )
    }
    runMessage?.let { (title, message) ->
        ToolRunMessageDialog(
            title = title,
            message = message,
            onDismiss = { runMessage = null }
        )
    }
    controller.fetchInfoSearchChoiceRequest
        ?.takeIf { tool.id != "insert_chapter" && it.toolId == controller.builtInToolPlanId(tool.id) }
        ?.let { request ->
            FetchInfoSearchChoiceDialog(
                controller = controller,
                toolId = request.toolId,
                onDismiss = { controller.clearFetchInfoSearchChoiceRequest(request.toolId) },
                onPrepared = { onFetchInfoPreview(request.toolId) },
                onManualUrl = { controller.openFetchInfoManualUrlRetry(request.toolId) }
            )
        }
    controller.fetchInfoRetryRequest
        ?.takeIf { tool.id != "insert_chapter" && it.toolId == controller.builtInToolPlanId(tool.id) }
        ?.let { request ->
            FetchInfoRetryDialog(
                controller = controller,
                toolId = request.toolId,
                onDismiss = { controller.clearFetchInfoRetryRequest(request.toolId) },
                onPrepared = { onFetchInfoPreview(request.toolId) }
            )
        }
}
