package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.eteditor.core.DocumentKind

@Composable
fun TxtBookTitleEditDialog(
    controller: EditorController,
    onDismiss: () -> Unit
) {
    var draft by remember(controller.title) { mutableStateOf(controller.title) }
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
                .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
                .heightIn(max = 260.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "编辑书名",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                    }
                }
                ToolTextInputField(
                    label = "书名",
                    value = draft,
                    onValueChange = { draft = it },
                    autoFocus = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ButtonRow {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = ControlShape,
                        contentPadding = CompactButtonPadding
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            if (controller.updateTxtBookTitle(draft)) {
                                onDismiss()
                            }
                        },
                        enabled = draft.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = ControlShape,
                        contentPadding = CompactButtonPadding
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

@Composable
fun TxtBookTitleFilterDialog(
    controller: EditorController,
    onDismiss: () -> Unit
) {
    var showCreateBookTitleRuleDialog by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(false) }
    var applyMessage by remember { mutableStateOf("") }
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
                .fixedDialogWidth(fraction = 0.90f, maxWidth = 380.dp)
                .heightIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "书名过滤",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RuleDialogIconButton(
                            icon = Icons.Outlined.Add,
                            contentDescription = "新建规则",
                            onClick = { showCreateBookTitleRuleDialog = true }
                        )
                        RuleDialogIconButton(
                            icon = Icons.Outlined.SwapVert,
                            contentDescription = if (sortMode) "退出排序" else "排序",
                            selected = sortMode,
                            onClick = { sortMode = !sortMode }
                        )
                        RuleDialogIconButton(
                            icon = Icons.Outlined.Close,
                            contentDescription = "关闭",
                            onClick = onDismiss
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    TxtBookTitleRuleList(
                        controller = controller,
                        sortMode = sortMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (applyMessage.isNotBlank()) {
                    Text(
                        text = applyMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                ButtonRow {
                    Button(
                        onClick = {
                            if (controller.applyTxtBookTitleFilter()) {
                                onDismiss()
                            } else {
                                applyMessage = controller.statusMessage.ifBlank { "书名过滤没有命中规则" }
                            }
                        },
                        enabled = controller.kind == DocumentKind.Txt && !controller.busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ControlShape,
                        contentPadding = CompactButtonPadding
                    ) {
                        Text("应用")
                    }
                }
            }
        }
    }
    if (showCreateBookTitleRuleDialog) {
        TxtBookTitleRuleCreateDialog(
            onDismiss = { showCreateBookTitleRuleDialog = false },
            onConfirm = { name, pattern, replacement ->
                controller.addTxtBookTitleRule(
                    name = name,
                    pattern = pattern,
                    replacement = replacement
                ).also { saved ->
                    if (saved) showCreateBookTitleRuleDialog = false
                }
            }
        )
    }
}

@Composable
private fun TxtBookTitleRuleCreateDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Boolean
) {
    var name by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
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
                .fixedDialogWidth(fraction = 0.86f, maxWidth = 340.dp)
                .heightIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RuleCreateDialogHeader(title = "新建书名过滤", onDismiss = onDismiss)
                ToolTextInputField(
                    label = "名称",
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth()
                )
                ToolTextInputField(
                    label = "原书名",
                    value = pattern,
                    onValueChange = {
                        pattern = it
                        errorMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                ToolTextInputField(
                    label = "新书名",
                    value = replacement,
                    onValueChange = { replacement = it },
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                RuleCreateDialogActions(
                    onDismiss = onDismiss,
                    onConfirm = {
                        if (!onConfirm(name, pattern, replacement)) {
                            errorMessage = "正则错误，请检查原书名表达式"
                        }
                    },
                    confirmEnabled = pattern.isNotBlank() && replacement.isNotBlank(),
                    confirmLabel = "保存"
                )
            }
        }
    }
}

@Composable
private fun TxtBookTitleRuleList(
    controller: EditorController,
    sortMode: Boolean,
    modifier: Modifier = Modifier
) {
    val rules = controller.txtBookTitleRuleItems()
    UnifiedRuleList(
        items = rules,
        emptyText = "暂无书名过滤规则",
        itemKey = { rule -> "book-title-${rule.index}" },
        modifier = modifier,
        sortMode = sortMode
    ) { rule, _, drag ->
        TxtBookTitleRuleRow(
            rule = rule,
            controller = controller,
            drag = drag,
            sortMode = sortMode
        )
    }
}

@Composable
private fun TxtBookTitleRuleRow(
    rule: TxtBookTitleRuleItem,
    controller: EditorController,
    drag: RuleListDragContext,
    sortMode: Boolean = false
) {
    var showEditDialog by remember(rule.index) { mutableStateOf(false) }
    var deleteConfirm by remember(rule.index) { mutableStateOf<DeleteConfirmRequest?>(null) }
    val displayName = rule.name.ifBlank { "未命名" }
    DraggableCompactRuleListRow(
        rowKey = "book-title-${rule.index}",
        index = rule.index + 1,
        name = displayName,
        onEdit = { showEditDialog = true },
        onDelete = {
            deleteConfirm = DeleteConfirmRequest(
                title = "确认删除规则",
                message = "确定删除书名过滤规则“$displayName”吗？",
                onConfirm = { controller.deleteTxtBookTitleRule(rule.index) }
            )
        },
        position = drag.position,
        itemCount = drag.itemCount,
        reorderStepPx = drag.reorderStepPx,
        displacedOffsetPx = drag.displacedOffsetPx,
        onMove = { targetIndex -> controller.moveTxtBookTitleRule(rule.index, targetIndex) },
        onDragVisualChange = drag.onDragVisualChange,
        dragOffsetPx = drag.dragOffsetPx,
        dragging = drag.dragging,
        onDragOffsetChange = drag.onDragOffsetChange,
        onDraggingChange = drag.onDraggingChange,
        openSwipeRowKey = drag.openSwipeRowKey,
        onOpenSwipeRowChange = drag.onOpenSwipeRowChange,
        showDragHandle = drag.reorderEnabled,
        sortMode = sortMode,
        leadingContent = {
            RuleSelectCircle(
                selected = rule.matchCount > 0
            )
        }
    )
    if (showEditDialog) {
        TxtBookTitleRuleEditDialog(
            rule = rule,
            controller = controller,
            onDismiss = { showEditDialog = false }
        )
    }
    deleteConfirm?.let { request ->
        DeleteConfirmDialog(
            request = request,
            onDismiss = { deleteConfirm = null }
        )
    }
}

@Composable
private fun TxtBookTitleRuleEditDialog(
    rule: TxtBookTitleRuleItem,
    controller: EditorController,
    onDismiss: () -> Unit
) {
    var name by remember(rule.index, rule.name) { mutableStateOf(rule.name) }
    var pattern by remember(rule.index, rule.pattern) { mutableStateOf(rule.pattern) }
    var replacement by remember(rule.index, rule.replacement) { mutableStateOf(rule.replacement) }
    var errorMessage by remember(rule.index) { mutableStateOf("") }
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
                .fixedDialogWidth(fraction = 0.86f, maxWidth = 340.dp)
                .heightIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RuleCreateDialogHeader(title = "编辑书名过滤", onDismiss = onDismiss)
                ToolTextInputField(
                    label = "名称",
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth()
                )
                ToolTextInputField(
                    label = "原书名",
                    value = pattern,
                    onValueChange = {
                        pattern = it
                        errorMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                ToolTextInputField(
                    label = "新书名",
                    value = replacement,
                    onValueChange = { replacement = it },
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                RuleCreateDialogActions(
                    onDismiss = onDismiss,
                    onConfirm = {
                        if (controller.updateTxtBookTitleRule(rule.index, name, pattern, replacement)) {
                            onDismiss()
                        } else {
                            errorMessage = controller.statusMessage.ifBlank { "正则错误，请检查原书名表达式" }
                        }
                    },
                    confirmEnabled = pattern.isNotBlank() && replacement.isNotBlank(),
                    confirmLabel = "保存"
                )
            }
        }
    }
}

@Composable
private fun RuleSelectCircle(
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        modifier = modifier.size(24.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "当前命中规则",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}
