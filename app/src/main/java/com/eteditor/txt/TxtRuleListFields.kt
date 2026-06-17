package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun TxtChapterRecognitionPanel(
    controller: EditorController,
    sortMode: Boolean,
    modifier: Modifier = Modifier
) {
    TxtChapterRuleList(
        controller = controller,
        sortMode = sortMode,
        modifier = modifier
    )
}

@Composable
private fun TxtChapterRuleList(
    controller: EditorController,
    sortMode: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val rules = controller.txtChapterRuleItems()
    UnifiedRuleList(
        items = rules,
        emptyText = "暂无自定义规则",
        itemKey = { rule -> "chapter-rule-${rule.index}" },
        modifier = modifier,
        sortMode = sortMode
    ) { rule, _, drag ->
        var showEditDialog by remember(rule.index) { mutableStateOf(false) }
        var deleteConfirm by remember(rule.index) { mutableStateOf<DeleteConfirmRequest?>(null) }
        val displayName = rule.name.trim().ifBlank { "未命名" }
        TxtChapterRuleRow(
            rule = rule,
            name = displayName,
            drag = drag,
            sortMode = sortMode,
            onEnabledChange = { enabled ->
                scope.launchAfterTxtMoveChapterSync(controller, "启用目录规则") {
                    controller.updateTxtChapterRuleEnabled(rule.index, enabled, deferRefresh = true)
                }
            },
            onEdit = { showEditDialog = true },
            onDelete = {
                deleteConfirm = DeleteConfirmRequest(
                    title = "确认删除规则",
                    message = "确定删除章节识别规则“$displayName”吗？",
                    onConfirm = {
                        scope.launchAfterTxtMoveChapterSync(controller, "删除目录规则") {
                            controller.deleteTxtChapterRule(rule.index, deferRefresh = true)
                        }
                    }
                )
            },
            onMove = { targetIndex ->
                scope.launchAfterTxtMoveChapterSync(controller, "移动目录规则") {
                    controller.moveTxtChapterRule(rule.index, targetIndex, deferRefresh = true)
                }
            }
        )
        if (showEditDialog) {
            TxtChapterRuleEditDialog(
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
}

@Composable
private fun TxtChapterRuleRow(
    rule: TxtChapterRuleItem,
    name: String,
    drag: RuleListDragContext,
    sortMode: Boolean = false,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit
) {
    DraggableCompactRuleListRow(
        rowKey = "chapter-rule-${rule.index}",
        index = rule.index + 1,
        name = name,
        onEdit = onEdit,
        onDelete = onDelete,
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
        showDragHandle = drag.reorderEnabled,
        sortMode = sortMode,
        leadingContent = {
            RuleEnableSwitch(
                checked = rule.enabled,
                onCheckedChange = onEnabledChange
            )
        }
    )
}

@Composable
private fun TxtChapterRuleEditDialog(
    rule: TxtChapterRuleItem,
    controller: EditorController,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
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
                .fixedDialogWidth(fraction = 0.88f, maxWidth = 360.dp)
                .heightIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RuleCreateDialogHeader(title = "编辑目录规则", onDismiss = onDismiss)
                ToolTextInputField(
                    label = "名称",
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth()
                )
                ToolTextInputField(
                    label = "表达式",
                    value = pattern,
                    onValueChange = {
                        pattern = it
                        errorMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                ToolTextInputField(
                    label = "映射值",
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
                        val nextName = name
                        val nextPattern = pattern
                        val nextReplacement = replacement
                        if (controller.txtMoveChapterSyncPending) {
                            scope.launchAfterTxtMoveChapterSync(controller, "修改目录规则") {
                                if (controller.updateTxtChapterRuleItem(rule.index, nextName, nextPattern, nextReplacement, deferRefresh = true)) {
                                    onDismiss()
                                } else {
                                    errorMessage = controller.statusMessage.ifBlank { "正则错误，请检查表达式" }
                                }
                            }
                        } else {
                            if (controller.updateTxtChapterRuleItem(rule.index, nextName, nextPattern, nextReplacement, deferRefresh = true)) {
                                onDismiss()
                            } else {
                                errorMessage = controller.statusMessage.ifBlank { "正则错误，请检查表达式" }
                            }
                        }
                    },
                    confirmEnabled = pattern.isNotBlank()
                )
            }
        }
    }
}

@Composable
private fun RuleEnableSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(999.dp),
        color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.size(width = 46.dp, height = 24.dp)
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
                    .offset(x = if (checked) 23.dp else 3.dp)
                    .size(18.dp)
            ) {}
        }
    }
}

@Composable
internal fun TxtPurifyTextPanel(
    controller: EditorController,
    sortMode: Boolean,
    modifier: Modifier = Modifier
) {
    val rules = controller.txtPurifyRuleItems()
    val bodyRules = rules.filter { it.target == TXT_PURIFY_TARGET_BODY }
    val catalogRules = rules.filter { it.target == TXT_PURIFY_TARGET_CATALOG }
    val density = LocalDensity.current
    val reorderStepPx = with(density) { 48.dp.toPx() }
    val groupCount = listOf(bodyRules, catalogRules).count { it.isNotEmpty() }
    val scrollbarItemCount = if (rules.isEmpty()) 0 else rules.size + groupCount
    var draggingTarget by remember { mutableStateOf<String?>(null) }
    var draggingRuleIndex by remember { mutableStateOf<Int?>(null) }
    var draggingPosition by remember { mutableStateOf<Int?>(null) }
    var draggingOffsetPx by remember { mutableStateOf(0f) }
    var openSwipeRowKey by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(rules, openSwipeRowKey) {
        val openedKey = openSwipeRowKey ?: return@LaunchedEffect
        if (rules.none { rule -> "purify-${rule.index}" == openedKey }) {
            openSwipeRowKey = null
        }
    }
    LaunchedEffect(rules, draggingRuleIndex) {
        val ruleIndex = draggingRuleIndex ?: return@LaunchedEffect
        if (rules.none { rule -> rule.index == ruleIndex }) {
            draggingTarget = null
            draggingRuleIndex = null
            draggingPosition = null
            draggingOffsetPx = 0f
        }
    }
    fun dragTargetPositionFor(target: String, groupSize: Int): Int? {
        if (draggingTarget != target) return null
        val position = draggingPosition ?: return null
        return ruleDragTargetPosition(position, draggingOffsetPx, groupSize, reorderStepPx)
    }
    val bodyDragTargetPosition = dragTargetPositionFor(TXT_PURIFY_TARGET_BODY, bodyRules.size)
    val catalogDragTargetPosition = dragTargetPositionFor(TXT_PURIFY_TARGET_CATALOG, catalogRules.size)
    val bodyPreviewRules = previewRuleListForDrag(
        items = bodyRules,
        draggingPosition = draggingPosition.takeIf { draggingTarget == TXT_PURIFY_TARGET_BODY },
        targetPosition = bodyDragTargetPosition
    )
    val catalogPreviewRules = previewRuleListForDrag(
        items = catalogRules,
        draggingPosition = draggingPosition.takeIf { draggingTarget == TXT_PURIFY_TARGET_CATALOG },
        targetPosition = catalogDragTargetPosition
    )
    UnifiedRuleListFrame(
        modifier = modifier,
        itemCount = scrollbarItemCount,
        showScrollbar = draggingTarget == null,
        userScrollEnabled = draggingTarget == null
    ) {
        if (rules.isEmpty()) {
            item("empty") {
                Text(
                    text = "暂无净化规则",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            if (bodyRules.isNotEmpty()) {
                item("body-header") {
                    TxtPurifyRuleGroupHeader(title = "正文", count = bodyRules.size)
                }
                itemsIndexed(bodyPreviewRules, key = { _, rule -> "purify-body-${rule.index}" }) { actualPosition, rule ->
                    val rowDragging = draggingRuleIndex == rule.index
                    val rowSourcePosition = if (rowDragging) {
                        draggingPosition ?: actualPosition
                    } else {
                        actualPosition
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TxtPurifyRuleRow(
                            rule = rule,
                            controller = controller,
                            groupRules = bodyRules,
                            groupPosition = rowSourcePosition,
                            reorderStepPx = reorderStepPx,
                            dragOffsetPx = if (rowDragging) {
                                ruleDragVisualOffsetPx(
                                    sourcePosition = rowSourcePosition,
                                    displayPosition = actualPosition,
                                    dragOffsetPx = draggingOffsetPx,
                                    stepPx = reorderStepPx
                                )
                            } else {
                                0f
                            },
                            dragging = rowDragging,
                            displacedOffsetPx = 0f,
                            onDragOffsetChange = { offset ->
                                draggingTarget = rule.target
                                draggingRuleIndex = rule.index
                                draggingPosition = rowSourcePosition
                                draggingOffsetPx = offset
                            },
                            onDraggingChange = { isDragging ->
                                if (isDragging) {
                                    draggingTarget = rule.target
                                    draggingRuleIndex = rule.index
                                    draggingPosition = rowSourcePosition
                                    draggingOffsetPx = 0f
                                } else if (draggingRuleIndex == rule.index) {
                                    draggingTarget = null
                                    draggingRuleIndex = null
                                    draggingPosition = null
                                    draggingOffsetPx = 0f
                                }
                            },
                            onDragVisualChange = { target, dragPosition, offset ->
                                if (target != null && dragPosition != null) {
                                    draggingTarget = target
                                    draggingRuleIndex = rule.index
                                    draggingPosition = rowSourcePosition
                                    draggingOffsetPx = offset
                                } else if (draggingRuleIndex == rule.index) {
                                    draggingTarget = null
                                    draggingRuleIndex = null
                                    draggingPosition = null
                                    draggingOffsetPx = 0f
                                }
                            },
                            openSwipeRowKey = openSwipeRowKey,
                            onOpenSwipeRowChange = { openSwipeRowKey = it },
                            showDragHandle = sortMode,
                            sortMode = sortMode
                        )
                    }
                }
            }
            if (catalogRules.isNotEmpty()) {
                item("catalog-header") {
                    TxtPurifyRuleGroupHeader(title = "目录", count = catalogRules.size)
                }
                itemsIndexed(catalogPreviewRules, key = { _, rule -> "purify-catalog-${rule.index}" }) { actualPosition, rule ->
                    val rowDragging = draggingRuleIndex == rule.index
                    val rowSourcePosition = if (rowDragging) {
                        draggingPosition ?: actualPosition
                    } else {
                        actualPosition
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TxtPurifyRuleRow(
                            rule = rule,
                            controller = controller,
                            groupRules = catalogRules,
                            groupPosition = rowSourcePosition,
                            reorderStepPx = reorderStepPx,
                            dragOffsetPx = if (rowDragging) {
                                ruleDragVisualOffsetPx(
                                    sourcePosition = rowSourcePosition,
                                    displayPosition = actualPosition,
                                    dragOffsetPx = draggingOffsetPx,
                                    stepPx = reorderStepPx
                                )
                            } else {
                                0f
                            },
                            dragging = rowDragging,
                            displacedOffsetPx = 0f,
                            onDragOffsetChange = { offset ->
                                draggingTarget = rule.target
                                draggingRuleIndex = rule.index
                                draggingPosition = rowSourcePosition
                                draggingOffsetPx = offset
                            },
                            onDraggingChange = { isDragging ->
                                if (isDragging) {
                                    draggingTarget = rule.target
                                    draggingRuleIndex = rule.index
                                    draggingPosition = rowSourcePosition
                                    draggingOffsetPx = 0f
                                } else if (draggingRuleIndex == rule.index) {
                                    draggingTarget = null
                                    draggingRuleIndex = null
                                    draggingPosition = null
                                    draggingOffsetPx = 0f
                                }
                            },
                            onDragVisualChange = { target, dragPosition, offset ->
                                if (target != null && dragPosition != null) {
                                    draggingTarget = target
                                    draggingRuleIndex = rule.index
                                    draggingPosition = rowSourcePosition
                                    draggingOffsetPx = offset
                                } else if (draggingRuleIndex == rule.index) {
                                    draggingTarget = null
                                    draggingRuleIndex = null
                                    draggingPosition = null
                                    draggingOffsetPx = 0f
                                }
                            },
                            openSwipeRowKey = openSwipeRowKey,
                            onOpenSwipeRowChange = { openSwipeRowKey = it },
                            showDragHandle = sortMode,
                            sortMode = sortMode
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TxtPurifyRuleGroupHeader(
    title: String,
    count: Int
) {
    Text(
        text = "$title：共 $count 项规则",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun TxtPurifyRuleRow(
    rule: TxtPurifyRuleItem,
    controller: EditorController,
    groupRules: List<TxtPurifyRuleItem>,
    groupPosition: Int,
    reorderStepPx: Float,
    dragOffsetPx: Float,
    dragging: Boolean,
    displacedOffsetPx: Float,
    onDragOffsetChange: (Float) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    onDragVisualChange: (String?, Int?, Float) -> Unit,
    openSwipeRowKey: Any?,
    onOpenSwipeRowChange: (Any?) -> Unit,
    showDragHandle: Boolean,
    sortMode: Boolean = false
) {
    var showEditDialog by remember(rule.index) { mutableStateOf(false) }
    var deleteConfirm by remember(rule.index) { mutableStateOf<DeleteConfirmRequest?>(null) }
    val displayName = rule.name.ifBlank { "未命名" }
    DraggableCompactRuleListRow(
        rowKey = "purify-${rule.index}",
        index = rule.index + 1,
        name = displayName,
        onEdit = { showEditDialog = true },
        onDelete = {
            deleteConfirm = DeleteConfirmRequest(
                title = "确认删除规则",
                message = "确定删除净化规则“$displayName”吗？",
                onConfirm = { controller.deleteTxtPurifyRule(rule.index) }
            )
        },
        position = groupPosition,
        itemCount = groupRules.size,
        reorderStepPx = reorderStepPx,
        displacedOffsetPx = displacedOffsetPx,
        dragOffsetPx = dragOffsetPx,
        dragging = dragging,
        onDragOffsetChange = onDragOffsetChange,
        onDraggingChange = onDraggingChange,
        onMove = { targetPosition ->
            val targetIndex = groupRules.getOrNull(targetPosition)?.index ?: rule.index
            if (targetIndex != rule.index) {
                controller.moveTxtPurifyRule(rule.index, targetIndex)
            }
        },
        onDragVisualChange = { position, offset ->
            onDragVisualChange(rule.target.takeIf { position != null }, position, offset)
        },
        openSwipeRowKey = openSwipeRowKey,
        onOpenSwipeRowChange = onOpenSwipeRowChange,
        showDragHandle = showDragHandle,
        sortMode = sortMode,
        leadingContent = {
            RuleEnableSwitch(
                checked = rule.enabled,
                onCheckedChange = { enabled -> controller.updateTxtPurifyRuleEnabled(rule.index, enabled) }
            )
        }
    )
    if (showEditDialog) {
        TxtPurifyRuleEditDialog(
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
private fun TxtPurifyRuleEditDialog(
    rule: TxtPurifyRuleItem,
    controller: EditorController,
    onDismiss: () -> Unit
) {
    var target by remember(rule.index, rule.target) { mutableStateOf(rule.target) }
    var name by remember(rule.index, rule.name) { mutableStateOf(rule.name) }
    var regex by remember(rule.index, rule.regex) { mutableStateOf(rule.regex) }
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
                .fixedDialogWidth(fraction = 0.88f, maxWidth = 380.dp)
                .heightIn(max = 430.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RuleCreateDialogHeader(title = "编辑净化规则", onDismiss = onDismiss)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ToolTextInputField(
                        label = "名称",
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.weight(1f)
                    )
                    ToolDropdownField(
                        label = "分组",
                        value = txtPurifyTargetLabel(target),
                        options = listOf(
                            TXT_PURIFY_TARGET_BODY to "正文",
                            TXT_PURIFY_TARGET_CATALOG to "目录"
                        ),
                        onSelect = { target = it },
                        modifier = Modifier.width(116.dp)
                    )
                }
                ToolTextInputField(
                    label = "匹配",
                    value = pattern,
                    onValueChange = {
                        pattern = it
                        errorMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                ToolTextInputField(
                    label = "替换为",
                    value = replacement,
                    onValueChange = { replacement = it },
                    modifier = Modifier.fillMaxWidth()
                )
                CompactDialogSwitchField(
                    label = "正则",
                    checked = regex,
                    onCheckedChange = {
                        regex = it
                        errorMessage = ""
                    },
                    modifier = Modifier.widthIn(max = 120.dp)
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
                        if (controller.updateTxtPurifyRule(rule.index, target, name, regex, pattern, replacement)) {
                            onDismiss()
                        } else {
                            errorMessage = controller.statusMessage.ifBlank { "正则错误，请检查匹配表达式" }
                        }
                    },
                    confirmEnabled = pattern.isNotBlank()
                )
            }
        }
    }
}

internal fun txtPurifyTargetLabel(target: String): String {
    return when (target) {
        TXT_PURIFY_TARGET_CATALOG -> "目录"
        else -> "正文"
    }
}
