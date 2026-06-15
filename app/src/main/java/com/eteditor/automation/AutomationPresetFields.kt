package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

@Composable
internal fun PresetPickerDialog(
    controller: EditorController,
    onDismiss: () -> Unit,
    onPickPreset: (String) -> Unit,
    onEditPreset: (String) -> Unit
) {
    val presets = controller.editorToolsForEpubAutomation()
    var openSwipePresetId by remember { mutableStateOf<String?>(null) }
    val groupedPresets = controller.automationFunctionTools().mapNotNull { definition ->
        val groupPresets = presets.filter { preset -> preset.toolId == definition.id }
        if (groupPresets.isEmpty()) null else definition to groupPresets
    }
    LaunchedEffect(presets, openSwipePresetId) {
        val openedId = openSwipePresetId ?: return@LaunchedEffect
        if (presets.none { preset -> preset.id == openedId }) {
            openSwipePresetId = null
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RowShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            tonalElevation = 0.dp,
            modifier = Modifier.adaptiveDialogWidth(AdaptiveDialogWidth.Medium)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "我的预设",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                    }
                }
                Surface(
                    shape = RowShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 292.dp)
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (presets.isEmpty()) {
                            item {
                                Text(
                                    text = "暂无预设",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        } else {
                            groupedPresets.forEach { (definition, groupPresets) ->
                                item(key = "preset-group-${definition.id}") {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        PresetPickerGroupHeader(definition = definition)
                                        groupPresets.chunked(2).forEach { rowPresets ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                rowPresets.forEach { preset ->
                                                    PresetPickerCell(
                                                        controller = controller,
                                                        preset = preset,
                                                        onPick = { onPickPreset(preset.id) },
                                                        onEdit = { onEditPreset(preset.id) },
                                                        openSwipeRowKey = openSwipePresetId,
                                                        onOpenSwipeRowChange = { openSwipePresetId = it },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                if (rowPresets.size == 1) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetPickerCell(
    controller: EditorController,
    preset: EditorTool,
    onPick: () -> Unit,
    onEdit: () -> Unit,
    openSwipeRowKey: String?,
    onOpenSwipeRowChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var deleteConfirm by remember(preset.id) { mutableStateOf<DeleteConfirmRequest?>(null) }
    PresetPickerRow(
        preset = preset,
        onPick = onPick,
        onEdit = onEdit,
        onDelete = {
            deleteConfirm = DeleteConfirmRequest(
                title = "确认删除预设",
                message = "确定删除预设“${preset.name}”吗？使用它的执行步骤会失效。",
                onConfirm = { controller.deleteEditorTool(preset.id) }
            )
        },
        openSwipeRowKey = openSwipeRowKey,
        onOpenSwipeRowChange = onOpenSwipeRowChange,
        modifier = modifier
    )
    deleteConfirm?.let { request ->
        DeleteConfirmDialog(
            request = request,
            onDismiss = { deleteConfirm = null }
        )
    }
}

@Composable
private fun PresetPickerGroupHeader(definition: ToolDefinition) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 7.dp, end = 4.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            builtInToolIcon(definition.id),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = definition.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PresetPickerRow(
    preset: EditorTool,
    onPick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    openSwipeRowKey: String?,
    onOpenSwipeRowChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var rowOffsetPx by remember(preset.id) { mutableStateOf(0f) }
    val actionWidth = 80.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    LaunchedEffect(openSwipeRowKey, preset.id) {
        if (openSwipeRowKey != preset.id) rowOffsetPx = 0f
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        Surface(
            shape = RowShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(actionWidth)
                .height(40.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                IconButton(
                    onClick = {
                        rowOffsetPx = 0f
                        onOpenSwipeRowChange(null)
                        onEdit()
                    },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑预设", modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = {
                        rowOffsetPx = 0f
                        onOpenSwipeRowChange(null)
                        onDelete()
                    },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除预设", modifier = Modifier.size(16.dp))
                }
            }
        }
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .offset { IntOffset(rowOffsetPx.roundToInt(), 0) }
                .pointerInput(preset.id) {
                    detectTapGestures(
                        onTap = {
                            rowOffsetPx = 0f
                            onOpenSwipeRowChange(null)
                            onPick()
                        }
                    )
                }
                .pointerInput(preset.id, actionWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            onOpenSwipeRowChange(preset.id)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            rowOffsetPx = (rowOffsetPx + dragAmount).coerceIn(-actionWidthPx, 0f)
                        },
                        onDragEnd = {
                            val nextOffset = if (rowOffsetPx < -actionWidthPx * 0.45f) -actionWidthPx else 0f
                            rowOffsetPx = nextOffset
                            onOpenSwipeRowChange(if (nextOffset < 0f) preset.id else null)
                        },
                        onDragCancel = {
                            val nextOffset = if (rowOffsetPx < -actionWidthPx * 0.45f) -actionWidthPx else 0f
                            rowOffsetPx = nextOffset
                            onOpenSwipeRowChange(if (nextOffset < 0f) preset.id else null)
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 9.dp, top = 5.dp, end = 9.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = preset.name.ifBlank { "未命名" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
