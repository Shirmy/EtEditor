package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun AutomationStepCreatorButton(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onToggle,
        modifier = modifier,
        shape = RowShape,
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.Add,
                contentDescription = "添加步骤",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun AutomationStepFunctionGrid(
    controller: EditorController,
    modifier: Modifier = Modifier,
    onAddFunction: (String) -> Unit
) {
    val tools = controller.automationFunctionTools()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RowShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (tools.isEmpty()) {
                Text(
                    text = "\u6682\u65e0\u53ef\u6dfb\u52a0\u529f\u80fd",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                tools.chunked(2).forEach { rowTools ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowTools.forEach { tool ->
                            AutomationStepFunctionCard(
                                tool = tool,
                                onClick = { onAddFunction(tool.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowTools.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationStepFunctionCard(
    tool: ToolDefinition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RowShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.heightIn(min = 46.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                builtInToolIcon(tool.id),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tool.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun AutomationStepList(
    controller: EditorController,
    steps: List<AutomationStep>,
    modifier: Modifier = Modifier,
    onEditStep: (String) -> Unit,
    onOpenPresets: () -> Unit
) {
    var sortMode by remember { mutableStateOf(false) }
    LaunchedEffect(steps.size) {
        if (steps.size < 2) sortMode = false
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "执行步骤",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (steps.size >= 2) {
                IconButton(
                    onClick = { sortMode = !sortMode },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (sortMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Outlined.SwapVert, contentDescription = if (sortMode) "退出排序" else "排序", modifier = Modifier.size(18.dp))
                }
            }
            IconButton(
                onClick = onOpenPresets,
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = "我的预设", modifier = Modifier.size(18.dp))
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RowShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, top = 2.dp, end = 2.dp, bottom = 4.dp)
            ) {
                if (steps.isEmpty()) {
                    Text(
                        text = "未添加步骤",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    steps.forEachIndexed { index, step ->
                        val canEditPreset = step.presetId.isBlank() || controller.editorTools.any { it.id == step.presetId }
                        AutomationStepRow(
                            index = index + 1,
                            title = controller.automationStepLabel(step),
                            isPreset = step.presetId.isNotBlank(),
                            canEdit = canEditPreset,
                            sortMode = sortMode,
                            itemCount = steps.size,
                            onEdit = { onEditStep(step.id) },
                            onRemove = { controller.removeAutomationStepFromSelected(index) },
                            onMove = { targetIndex -> controller.moveAutomationStepFromSelected(index, targetIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationStepRow(
    index: Int,
    title: String,
    isPreset: Boolean,
    canEdit: Boolean,
    sortMode: Boolean,
    itemCount: Int,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit
) {
    var deleteConfirm by remember(index, title) { mutableStateOf<DeleteConfirmRequest?>(null) }
    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 3.dp, end = 4.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = index.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isPreset) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "预设",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            if (sortMode) {
                IconButton(
                    onClick = { if (index > 1) onMove(index - 2) },
                    enabled = index > 1,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { if (index < itemCount) onMove(index) },
                    enabled = index < itemCount,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移", modifier = Modifier.size(18.dp))
                }
            } else {
                if (canEdit) {
                    IconButton(
                        onClick = onEdit,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑步骤", modifier = Modifier.size(17.dp))
                    }
                }
                IconButton(
                    onClick = {
                        deleteConfirm = DeleteConfirmRequest(
                            title = "确认移除步骤",
                            message = "确定从执行链中移除第 ${index} 步“$title”吗？",
                            onConfirm = onRemove
                        )
                    },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "移除步骤", modifier = Modifier.size(17.dp))
                }
            }
        }
    }
    deleteConfirm?.let { request ->
        DeleteConfirmDialog(
            request = request,
            onDismiss = { deleteConfirm = null }
        )
    }
}

@Composable
internal fun AutomationFunctionDetail(
    controller: EditorController,
    toolId: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onAdded: () -> Unit,
    onPickTextReplaceRuleFile: TextReplaceRuleFilePicker
) {
    val tool = controller.availableTools.firstOrNull { it.id == toolId }
    var name by remember(toolId) { mutableStateOf("") }
    var showSavePresetDialog by remember(toolId) { mutableStateOf(false) }
    var showDirectAddNameDialog by remember(toolId) { mutableStateOf(false) }
    var showFileRenameHelp by remember(toolId) { mutableStateOf(false) }
    var showFetchInfoHelp by remember(toolId) { mutableStateOf(false) }
    var showTitleFormatHelp by remember(toolId) { mutableStateOf(false) }
    var showInsertChapterHelp by remember(toolId) { mutableStateOf(false) }
    var showCoverHelp by remember(toolId) { mutableStateOf(false) }

    if (tool == null) {
        ToolDetailTemplate(
            title = "功能 / 缺失功能",
            modifier = modifier,
            onBack = onBack
        ) {
            Text(
                text = "这个功能已不存在。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    ToolDetailTemplate(
        title = "功能 / ${tool.title}",
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
        }
    ) {
        NativeFormSection("预设信息") {
            ToolTextInputField(
                value = name,
                onValueChange = { name = it },
                label = "名字",
                modifier = Modifier.fillMaxWidth()
            )
        }
        ToolParameterSection(
            controller = controller,
            toolId = tool.id,
            valueFor = { parameter -> controller.builtInToolParameterValue(tool.id, parameter.key) },
            onValueChange = { parameter, value ->
                if (parameter.key == INSERT_CHAPTER_PARAM_SOURCE_TYPE) {
                    controller.clearInsertChapterSourcePreview()
                }
                controller.updateBuiltInToolParameter(tool.id, parameter.key, value)
            },
            coverOptions = ToolParameterCoverOptions(imageFileContent = {})
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val presetName = name.trim()
                    if (presetName.isNotBlank()) {
                        if (controller.saveBuiltInToolAsPresetAndAddToSelectedChain(tool.id, presetName)) {
                            onAdded()
                        }
                    } else {
                        showSavePresetDialog = true
                    }
                },
                enabled = tool.implemented && controller.builtInToolCanSavePreset(tool.id) && !controller.busy,
                modifier = Modifier.weight(1f),
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("保存预设并添加")
            }
            Button(
                onClick = {
                    val stepName = name.trim()
                    if (stepName.isBlank()) {
                        showDirectAddNameDialog = true
                    } else if (controller.addConfiguredBuiltInToolToSelectedChain(tool.id, stepName) != null) {
                        onAdded()
                    }
                },
                enabled = tool.implemented && !controller.busy,
                modifier = Modifier.weight(1f),
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("直接添加")
            }
        }
    }

    if (showSavePresetDialog) {
        SavePresetNameDialog(
            initialName = name,
            onDismiss = { showSavePresetDialog = false },
            onConfirm = { presetName ->
                if (controller.saveBuiltInToolAsPresetAndAddToSelectedChain(tool.id, presetName)) {
                    showSavePresetDialog = false
                    onAdded()
                }
            }
        )
    }
    if (showDirectAddNameDialog) {
        AddStepNameDialog(
            initialName = name,
            onDismiss = { showDirectAddNameDialog = false },
            onConfirm = { stepName ->
                name = stepName
                if (controller.addConfiguredBuiltInToolToSelectedChain(tool.id, stepName) != null) {
                    showDirectAddNameDialog = false
                    onAdded()
                }
            }
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

@Composable
internal fun AutomationStepEditor(
    controller: EditorController,
    step: AutomationStep,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onPickTextReplaceRuleFile: TextReplaceRuleFilePicker
) {
    val baseTool = controller.availableTools.firstOrNull { it.id == step.toolId }
    var showSavePresetDialog by remember(step.id) { mutableStateOf(false) }
    var showFileRenameHelp by remember(step.id) { mutableStateOf(false) }
    var showFetchInfoHelp by remember(step.id) { mutableStateOf(false) }
    var showTitleFormatHelp by remember(step.id) { mutableStateOf(false) }
    var showInsertChapterHelp by remember(step.id) { mutableStateOf(false) }
    var showCoverHelp by remember(step.id) { mutableStateOf(false) }
    if (baseTool == null) {
        ToolDetailTemplate(
            title = "步骤 / 缺失功能",
            modifier = modifier,
            onBack = onBack
        ) {
            Text(
                text = "这个步骤引用的功能已不存在。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    ToolDetailTemplate(
        title = "步骤 / ${baseTool.title}",
        modifier = modifier,
        onBack = onBack,
        onHelp = when (baseTool.id) {
            "file_rename" -> ({ showFileRenameHelp = true })
            "fetch_info" -> ({ showFetchInfoHelp = true })
            "title_format" -> ({ showTitleFormatHelp = true })
            "insert_chapter" -> ({ showInsertChapterHelp = true })
            "generate_cover" -> ({ showCoverHelp = true })
            else -> null
        },
        helpContentDescription = when (baseTool.id) {
            "fetch_info" -> "抓取范围说明"
            "title_format" -> "标题格式说明"
            "insert_chapter" -> "插入章节说明"
            "generate_cover" -> "封面标题说明"
            else -> "命名格式用法"
        },
    ) {
        NativeFormSection("功能") {
            if (step.presetId.isBlank()) {
                ToolTextInputField(
                    value = step.name,
                    onValueChange = { value -> controller.renameAutomationStep(step.id, value) },
                    label = "步骤名字",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            ToolReadOnlyField(
                label = "功能",
                value = baseTool.title,
                modifier = Modifier.fillMaxWidth()
            )
        }
        ToolParameterSection(
            controller = controller,
            toolId = step.toolId,
            valueFor = { parameter -> controller.automationStepParameterValue(step, parameter.key) },
            onValueChange = { parameter, value ->
                if (parameter.key == INSERT_CHAPTER_PARAM_SOURCE_TYPE) {
                    controller.clearInsertChapterSourcePreview()
                }
                controller.updateAutomationStepParameter(step.id, parameter.key, value)
            },
            coverOptions = ToolParameterCoverOptions(imageFileContent = {})
        )

        if (step.presetId.isBlank()) {
            Button(
                onClick = onBack,
                enabled = !controller.busy,
                modifier = Modifier.fillMaxWidth(),
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("保存")
            }
        }
    }

    if (showSavePresetDialog) {
        SavePresetNameDialog(
            initialName = "",
            onDismiss = { showSavePresetDialog = false },
            onConfirm = { presetName ->
                if (controller.saveAutomationStepAsPreset(step.id, presetName)) {
                    showSavePresetDialog = false
                }
            }
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
