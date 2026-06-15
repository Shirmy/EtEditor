package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val TxtTextReplaceScopeOptions = listOf(
    TOOL_SCOPE_ALL to "全文",
    TOOL_SCOPE_CURRENT to "本章"
)

private val EpubTextReplaceScopeOptions = listOf(
    TOOL_SCOPE_ALL to "全文",
    TOOL_SCOPE_CURRENT to "本章"
)

private val TXT_PRESET_DIALOG_MAX_HEIGHT = 560.dp
private val TXT_PRESET_SECTION_MAX_HEIGHT = 420.dp
private val TXT_PRESET_COMPACT_SECTION_MAX_HEIGHT = 220.dp

fun cleanTxtTextReplaceScopeValue(scope: String): String {
    return scope.takeIf { value -> TxtTextReplaceScopeOptions.any { it.first == value } }
        ?: TOOL_SCOPE_ALL
}

private fun cleanEpubTextReplaceScopeValue(scope: String): String {
    return scope.takeIf { value -> EpubTextReplaceScopeOptions.any { it.first == value } }
        ?: TOOL_SCOPE_ALL
}

@Composable
fun TxtTextReplacePresetDialog(
    controller: EditorController,
    onDismiss: () -> Unit,
    onApply: (EditorTool) -> Unit
) {
    val presets = controller.txtTextReplacePresets
        .filter { preset ->
            preset.toolId == "text_replace" &&
                controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_FIND).isNotEmpty()
        }
    var openSwipePresetId by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(presets, openSwipePresetId) {
        val openedId = openSwipePresetId ?: return@LaunchedEffect
        if (presets.none { preset -> preset.id == openedId }) {
            openSwipePresetId = null
        }
    }
    val allPresets = presets.filter { preset ->
        cleanTxtTextReplaceScopeValue(
            controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_SCOPE)
        ) == TOOL_SCOPE_ALL
    }
    val currentPresets = presets.filter { preset ->
        cleanTxtTextReplaceScopeValue(
            controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_SCOPE)
        ) == TOOL_SCOPE_CURRENT
    }
    val sections = listOf(
        "全文" to allPresets,
        "本章" to currentPresets
    ).filter { (_, sectionPresets) -> sectionPresets.isNotEmpty() }
    val compactSections = sections.size > 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fixedDialogWidth(fraction = 0.78f, maxWidth = 320.dp)
                .heightIn(max = TXT_PRESET_DIALOG_MAX_HEIGHT)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RuleCreateDialogHeader(title = "搜索预设", onDismiss = onDismiss)
                if (presets.isEmpty()) {
                    UnifiedRuleList(
                        items = presets,
                        emptyText = "暂无搜索预设",
                        itemKey = { preset -> preset.id },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) { _, _, _ -> }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        sections.forEach { (title, sectionPresets) ->
                            TxtTextReplacePresetSection(
                                title = title,
                                presets = sectionPresets,
                                compact = compactSections,
                                controller = controller,
                                onApply = onApply,
                                openSwipeRowKey = openSwipePresetId,
                                onOpenSwipeRowChange = { openSwipePresetId = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TxtTextReplacePresetSection(
    title: String,
    presets: List<EditorTool>,
    compact: Boolean,
    controller: EditorController,
    onApply: (EditorTool) -> Unit,
    openSwipeRowKey: Any?,
    onOpenSwipeRowChange: (Any?) -> Unit
) {
    val maxHeight = if (compact) TXT_PRESET_COMPACT_SECTION_MAX_HEIGHT else TXT_PRESET_SECTION_MAX_HEIGHT
    val listHeight = (presets.size * 56).dp.coerceIn(56.dp, maxHeight)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        UnifiedRuleList(
            items = presets,
            emptyText = "",
            itemKey = { preset -> preset.id },
            modifier = Modifier
                .fillMaxWidth()
                .height(listHeight),
            openSwipeRowKey = openSwipeRowKey,
            onOpenSwipeRowChange = onOpenSwipeRowChange
        ) { preset, index, drag ->
            var editingPreset by remember(preset.id) { mutableStateOf<EditorTool?>(null) }
            var deleteConfirm by remember(preset.id) { mutableStateOf<DeleteConfirmRequest?>(null) }
            val presetName = preset.name.ifBlank { "未命名" }
            TxtTextReplacePresetRow(
                index = index,
                preset = preset,
                drag = drag,
                onApply = { onApply(preset) },
                onEdit = { editingPreset = preset },
                onDelete = {
                    deleteConfirm = DeleteConfirmRequest(
                        title = "确认删除预设",
                        message = "确定删除搜索预设“$presetName”吗？",
                        onConfirm = { controller.deleteTxtTextReplacePreset(preset.id) }
                    )
                },
                onMove = { targetIndex ->
                    presets.getOrNull(targetIndex)?.let { target ->
                        controller.moveTxtTextReplacePreset(preset.id, target.id)
                    }
                }
            )
            editingPreset?.let { editing ->
                TxtTextReplacePresetEditDialog(
                    controller = controller,
                    preset = editing,
                    onDismiss = { editingPreset = null }
                )
            }
            deleteConfirm?.let { request ->
                DeleteConfirmDialog(
                    request = request,
                    onDismiss = { deleteConfirm = null }
                )
            }
        }
    }
}

@Composable
private fun TxtTextReplacePresetRow(
    index: Int,
    preset: EditorTool,
    drag: RuleListDragContext,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit
) {
    DraggableCompactRuleListRow(
        rowKey = preset.id,
        index = index + 1,
        name = preset.name.ifBlank { "未命名" },
        onEdit = onEdit,
        onDelete = onDelete,
        onClick = null,
        position = drag.position,
        itemCount = drag.itemCount,
        reorderStepPx = drag.reorderStepPx,
        displacedOffsetPx = drag.displacedOffsetPx,
        onMove = onMove,
        onDragVisualChange = drag.onDragVisualChange,
        dragOffsetPx = drag.dragOffsetPx,
        dragging = drag.dragging,
        onDragOffsetChange = drag.onDragOffsetChange,
        onDraggingChange = drag.onDraggingChange,
        openSwipeRowKey = drag.openSwipeRowKey,
        onOpenSwipeRowChange = drag.onOpenSwipeRowChange,
        leadingContent = {
            Surface(
                onClick = onApply,
                shape = ControlShape,
                color = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .width(46.dp)
                    .height(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "应用",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }
    )
}

@Composable
private fun TxtTextReplacePresetEditDialog(
    controller: EditorController,
    preset: EditorTool,
    onDismiss: () -> Unit
) {
    var name by remember(preset.id, preset.name) { mutableStateOf(preset.name) }
    var find by remember(preset.id) {
        mutableStateOf(controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_FIND))
    }
    var replace by remember(preset.id) {
        mutableStateOf(controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_REPLACE))
    }
    var regex by remember(preset.id) {
        mutableStateOf(controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_FIND_REGEX) == "true")
    }
    var scope by remember(preset.id) {
        mutableStateOf(cleanTxtTextReplaceScopeValue(controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_SCOPE)))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fixedDialogWidth(fraction = 0.78f, maxWidth = 320.dp)
                .heightIn(max = 430.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RuleCreateDialogHeader(title = "编辑搜索预设", onDismiss = onDismiss)
                ToolTextInputField(
                    label = "预设名字",
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth()
                )
                ToolTextInputField(
                    label = "查找内容",
                    value = find,
                    onValueChange = { find = it },
                    modifier = Modifier.fillMaxWidth(),
                    showLineBreakMarks = true
                )
                ToolTextInputField(
                    label = "替换为",
                    value = replace,
                    onValueChange = { replace = it },
                    modifier = Modifier.fillMaxWidth(),
                    showLineBreakMarks = true
                )
                TxtTextReplacePresetSwitchRow(
                    label = "\u4ec5\u672c\u7ae0",
                    checked = scope == TOOL_SCOPE_CURRENT,
                    onCheckedChange = { checked ->
                        scope = if (checked) TOOL_SCOPE_CURRENT else TOOL_SCOPE_ALL
                    }
                )
                TxtTextReplacePresetSwitchRow(
                    label = "正则",
                    checked = regex,
                    onCheckedChange = { regex = it }
                )
                RuleCreateDialogActions(
                    onDismiss = onDismiss,
                    onConfirm = {
                        if (controller.updateTxtTextReplacePreset(preset.id, name, find, replace, regex, scope)) {
                            onDismiss()
                        }
                    },
                    confirmEnabled = name.isNotBlank() && find.isNotEmpty(),
                    confirmLabel = "保存"
                )
            }
        }
    }
}

@Composable
private fun TxtTextReplacePresetSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Surface(
            onClick = { onCheckedChange(!checked) },
            shape = RoundedCornerShape(999.dp),
            color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .width(38.dp)
                .height(20.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (checked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = if (checked) 0.dp else 1.dp,
                    modifier = Modifier
                        .padding(start = if (checked) 20.dp else 2.dp)
                        .width(16.dp)
                        .height(16.dp)
                ) {}
            }
        }
    }
}

@Composable
fun EpubTextReplacePresetDialog(
    controller: EditorController,
    onDismiss: () -> Unit,
    onApply: (EditorTool) -> Unit
) {
    val presets = controller.epubTextReplacePresets
        .filter { preset ->
            preset.toolId == "text_replace" &&
                controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_FIND).isNotEmpty()
        }
    val listHeight = if (presets.isEmpty()) {
        48.dp
    } else {
        (presets.size * 56).coerceIn(56, 320).dp
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fixedDialogWidth(fraction = 0.78f, maxWidth = 320.dp)
                .heightIn(max = 430.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RuleCreateDialogHeader(title = "EPUB 搜索预设", onDismiss = onDismiss)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(listHeight)
                ) {
                    UnifiedRuleList(
                        items = presets,
                        emptyText = "暂无 EPUB 搜索预设",
                        itemKey = { preset -> preset.id },
                        modifier = Modifier.fillMaxSize()
                    ) { preset, index, drag ->
                        var editingPreset by remember(preset.id) { mutableStateOf<EditorTool?>(null) }
                        var deleteConfirm by remember(preset.id) { mutableStateOf<DeleteConfirmRequest?>(null) }
                        val presetName = preset.name.ifBlank { "未命名" }
                        EpubTextReplacePresetRow(
                            index = index,
                            preset = preset,
                            drag = drag,
                            onApply = { onApply(preset) },
                            onEdit = { editingPreset = preset },
                            onDelete = {
                                deleteConfirm = DeleteConfirmRequest(
                                    title = "确认删除预设",
                                    message = "确定删除 EPUB 搜索预设“$presetName”吗？",
                                    onConfirm = { controller.deleteEpubTextReplacePreset(preset.id) }
                                )
                            },
                            onMove = { targetIndex ->
                                presets.getOrNull(targetIndex)?.let { target ->
                                    controller.moveEpubTextReplacePreset(preset.id, target.id)
                                }
                            }
                        )
                        editingPreset?.let { editing ->
                            EpubTextReplacePresetEditDialog(
                                controller = controller,
                                preset = editing,
                                onDismiss = { editingPreset = null }
                            )
                        }
                        deleteConfirm?.let { request ->
                            DeleteConfirmDialog(
                                request = request,
                                onDismiss = { deleteConfirm = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpubTextReplacePresetRow(
    index: Int,
    preset: EditorTool,
    drag: RuleListDragContext,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit
) {
    DraggableCompactRuleListRow(
        rowKey = preset.id,
        index = index + 1,
        name = preset.name.ifBlank { "未命名" },
        onEdit = onEdit,
        onDelete = onDelete,
        onClick = null,
        position = drag.position,
        itemCount = drag.itemCount,
        reorderStepPx = drag.reorderStepPx,
        displacedOffsetPx = drag.displacedOffsetPx,
        onMove = onMove,
        onDragVisualChange = drag.onDragVisualChange,
        dragOffsetPx = drag.dragOffsetPx,
        dragging = drag.dragging,
        onDragOffsetChange = drag.onDragOffsetChange,
        onDraggingChange = drag.onDraggingChange,
        openSwipeRowKey = drag.openSwipeRowKey,
        onOpenSwipeRowChange = drag.onOpenSwipeRowChange,
        leadingContent = {
            Surface(
                onClick = onApply,
                shape = ControlShape,
                color = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .width(46.dp)
                    .height(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "应用",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }
    )
}

@Composable
private fun EpubTextReplacePresetEditDialog(
    controller: EditorController,
    preset: EditorTool,
    onDismiss: () -> Unit
) {
    var name by remember(preset.id, preset.name) { mutableStateOf(preset.name) }
    var find by remember(preset.id) {
        mutableStateOf(controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_FIND))
    }
    var replace by remember(preset.id) {
        mutableStateOf(controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_REPLACE))
    }
    var regex by remember(preset.id) {
        mutableStateOf(controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_FIND_REGEX) == "true")
    }
    var scope by remember(preset.id) {
        mutableStateOf(cleanEpubTextReplaceScopeValue(controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_SCOPE)))
    }
    var target by remember(preset.id) {
        mutableStateOf(controller.editorToolParameterValue(preset, TEXT_REPLACE_PARAM_TARGET))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fixedDialogWidth(fraction = 0.78f, maxWidth = 320.dp)
                .heightIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RuleCreateDialogHeader(title = "编辑 EPUB 搜索预设", onDismiss = onDismiss)
                ToolTextInputField(
                    label = "预设名字",
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth()
                )
                ToolTextInputField(
                    label = "查找内容",
                    value = find,
                    onValueChange = { find = it },
                    modifier = Modifier.fillMaxWidth(),
                    showLineBreakMarks = true
                )
                ToolTextInputField(
                    label = "替换为",
                    value = replace,
                    onValueChange = { replace = it },
                    modifier = Modifier.fillMaxWidth(),
                    showLineBreakMarks = true
                )
                ToolSwitchField(
                    label = if (scope == TOOL_SCOPE_CURRENT) "本章" else "全文",
                    checked = scope == TOOL_SCOPE_CURRENT,
                    onCheckedChange = { checked ->
                        scope = if (checked) TOOL_SCOPE_CURRENT else TOOL_SCOPE_ALL
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                ToolSwitchField(
                    label = if (target == TEXT_REPLACE_TARGET_VISIBLE) "文本" else "源码",
                    checked = target == TEXT_REPLACE_TARGET_VISIBLE,
                    onCheckedChange = { checked ->
                        target = if (checked) {
                            TEXT_REPLACE_TARGET_VISIBLE
                        } else {
                            TEXT_REPLACE_TARGET_SOURCE
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                ToolSwitchField(
                    label = "正则",
                    checked = regex,
                    onCheckedChange = { regex = it },
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                )
                ButtonRow {
                    Button(
                        onClick = {
                            if (controller.updateEpubTextReplacePreset(preset.id, name, find, replace, regex, scope, target)) {
                                onDismiss()
                            }
                        },
                        enabled = name.isNotBlank() && find.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        shape = ControlShape,
                        contentPadding = CompactButtonPadding
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
