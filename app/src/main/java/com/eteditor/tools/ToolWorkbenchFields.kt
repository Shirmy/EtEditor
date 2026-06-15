package com.eteditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eteditor.core.DocumentKind
import kotlinx.coroutines.launch

@Composable
internal fun ToolWorkbench(
    controller: EditorController,
    tool: ToolDefinition,
    documentSessionKey: Int = 0,
    resetKey: Int = 0,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSave: () -> Unit,
    onTextReplacePreviewModeChange: (Boolean) -> Unit = {},
    onPickTextReplaceRuleFile: TextReplaceRuleFilePicker
) {
    var showFileRenameHelp by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var showFetchInfoHelp by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var showTitleFormatHelp by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var showInsertChapterHelp by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var showCoverHelp by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var showMoreMenu by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var showTextReplacePresetDialog by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf(false) }
    var saveMessage by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    var fileRenamePreviewToolId by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    var titleRenamePreviewToolId by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    var titleFormatPreviewToolId by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    var textReplacePreviewToolId by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    var fetchInfoPreviewToolId by remember(documentSessionKey, resetKey, tool.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val showBuiltInToolMenu = controller.kind != DocumentKind.Txt
    val textReplaceMode = if (tool.id == "text_replace") {
        textReplaceModeForUi(
            controller.builtInToolParameterValue(tool.id, TEXT_REPLACE_PARAM_MODE),
            controller.builtInToolParameterValue(tool.id, TEXT_REPLACE_PARAM_BATCH_SOURCE)
        )
    } else {
        ""
    }
    val showTextReplaceSearchPresets = tool.id == "text_replace" &&
        (controller.kind == DocumentKind.Txt ||
            (controller.kind == DocumentKind.Epub && textReplaceMode == TEXT_REPLACE_MODE_SINGLE))
    val canSaveDefault = showBuiltInToolMenu && tool.implemented && controller.builtInToolCanSaveDefault(tool.id)
    LaunchedEffect(documentSessionKey, resetKey, textReplacePreviewToolId) {
        onTextReplacePreviewModeChange(textReplacePreviewToolId != null)
    }
    DisposableEffect(documentSessionKey, resetKey) {
        onDispose { onTextReplacePreviewModeChange(false) }
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
            onApplyStarted = { fetchInfoPreviewToolId = null }
        )
    }
    ToolDetailTemplate(
        title = "${if (controller.kind == DocumentKind.Txt) "搜索" else "功能"} / ${tool.title}",
        modifier = modifier,
        onBack = onBack,
        onHelp = when (tool.id) {
            "file_rename" -> ({ showFileRenameHelp = true })
            "fetch_info" -> ({ showFetchInfoHelp = true })
            "title_format" -> ({ showTitleFormatHelp = true })
            "insert_chapter" -> ({ showInsertChapterHelp = true })
            "generate_cover" -> ({ showCoverHelp = true })
            else -> null
        },
        helpContentDescription = when (tool.id) {
            "fetch_info" -> "抓取范围说明"
            "title_format" -> "标题格式说明"
            "insert_chapter" -> "插入章节说明"
            "generate_cover" -> "封面标题说明"
            else -> "命名格式用法"
        },
        trailingContent = {
            if (showTextReplaceSearchPresets) {
                IconButton(
                    onClick = { showTextReplacePresetDialog = true },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = "搜索预设", modifier = Modifier.size(19.dp))
                }
            }
            if (!tool.implemented) {
                Text(
                    text = "待实现",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canSaveDefault) {
                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "更多", modifier = Modifier.size(19.dp))
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 6.dp,
                        modifier = Modifier.dialogBorder()
                    ) {
                        if (canSaveDefault) {
                            DropdownMenuItem(
                                text = { Text("设为默认") },
                                onClick = {
                                    showMoreMenu = false
                                    if (!controller.saveBuiltInToolDefaults(tool.id)) {
                                        saveMessage = controller.statusMessage.ifBlank { "无法设为默认" }
                                    }
                                }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            DropdownMenuItem(
                                text = { Text("重置默认") },
                                onClick = {
                                    showMoreMenu = false
                                    controller.resetBuiltInToolDefaults(tool.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    ) {
        ToolParameterPanel(
            controller = controller,
            tool = tool,
            documentSessionKey = documentSessionKey,
            resetKey = resetKey,
            onSave = onSave,
            onFileRenamePreview = { fileRenamePreviewToolId = it },
            onTitleRenamePreview = { titleRenamePreviewToolId = it },
            onTitleFormatPreview = { titleFormatPreviewToolId = it },
            onTextReplacePreview = { textReplacePreviewToolId = it },
            onFetchInfoPreview = { fetchInfoPreviewToolId = it },
            onPickTextReplaceRuleFile = onPickTextReplaceRuleFile
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
    if (showTextReplacePresetDialog && showTextReplaceSearchPresets) {
        if (controller.kind == DocumentKind.Txt) {
            TxtTextReplacePresetDialog(
                controller = controller,
                onDismiss = { showTextReplacePresetDialog = false },
                onApply = { preset ->
                    controller.updateBuiltInToolParameter(
                        tool.id,
                        TEXT_REPLACE_PARAM_FIND,
                        controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_FIND)
                    )
                    controller.updateBuiltInToolParameter(
                        tool.id,
                        TEXT_REPLACE_PARAM_REPLACE,
                        controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_REPLACE)
                    )
                    controller.updateBuiltInToolParameter(
                        tool.id,
                        TEXT_REPLACE_PARAM_FIND_REGEX,
                        controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_FIND_REGEX)
                    )
                    controller.updateBuiltInToolParameter(
                        tool.id,
                        TEXT_REPLACE_PARAM_SCOPE,
                        controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_SCOPE)
                    )
                    showTextReplacePresetDialog = false
                }
            )
        } else {
            EpubTextReplacePresetDialog(
                controller = controller,
                onDismiss = { showTextReplacePresetDialog = false },
                onApply = { preset ->
                    controller.updateBuiltInToolParameter(
                        tool.id,
                        TEXT_REPLACE_PARAM_SCOPE,
                        controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_SCOPE)
                    )
                    controller.updateBuiltInToolParameter(
                        tool.id,
                        TEXT_REPLACE_PARAM_TARGET,
                        controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_TARGET)
                    )
                    controller.updateBuiltInToolParameter(
                        tool.id,
                        TEXT_REPLACE_PARAM_FIND,
                        controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_FIND)
                    )
                    controller.updateBuiltInToolParameter(
                        tool.id,
                        TEXT_REPLACE_PARAM_REPLACE,
                        controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_REPLACE)
                    )
                    controller.updateBuiltInToolParameter(
                        tool.id,
                        TEXT_REPLACE_PARAM_FIND_REGEX,
                        controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_FIND_REGEX)
                    )
                    showTextReplacePresetDialog = false
                }
            )
        }
    }
    saveMessage?.let { message ->
        ToolRunMessageDialog(
            title = tool.title,
            message = message,
            onDismiss = { saveMessage = null }
        )
    }
}
