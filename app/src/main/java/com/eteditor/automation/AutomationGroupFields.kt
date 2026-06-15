package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
internal fun AutomationGroupManagerDialog(
    controller: EditorController,
    onDismiss: () -> Unit
) {
    var errorMessage by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf(false) }
    var openSwipeGroup by remember { mutableStateOf<String?>(null) }
    val ungroupedLabel = "未分组"
    val groupCounts = remember(controller.automationChains, controller.automationChainGroups) {
        controller.automationChains
            .groupingBy { chain -> chain.group.ifBlank { ungroupedLabel } }
            .eachCount()
    }
    val displayGroups = remember(controller.automationChains, controller.automationChainGroups) {
        val savedOrder = controller.automationChainGroups
        val baseOrder = if (ungroupedLabel in savedOrder) savedOrder else listOf(ungroupedLabel) + savedOrder
        (baseOrder +
            controller.automationChains.mapNotNull { chain -> chain.group.takeIf { it.isNotBlank() } })
            .distinct()
    }
    val sortableGroupCount = displayGroups.size
    LaunchedEffect(sortMode) {
        if (sortMode) openSwipeGroup = null
    }
    LaunchedEffect(displayGroups, openSwipeGroup) {
        val openedGroup = openSwipeGroup ?: return@LaunchedEffect
        if (openedGroup !in displayGroups) {
            openSwipeGroup = null
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            modifier = Modifier.fixedDialogWidth(fraction = 0.54f, maxWidth = 248.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "分组管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "新建分组", modifier = Modifier.size(19.dp))
                    }
                    IconButton(
                        onClick = {
                            if (sortableGroupCount > 1) {
                                sortMode = !sortMode
                                errorMessage = ""
                            } else {
                                errorMessage = "至少需要两个分组才能排序"
                            }
                        },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = if (sortMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Outlined.SwapVert, contentDescription = "排序分组", modifier = Modifier.size(19.dp))
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(19.dp))
                    }
                }
                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 238.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(displayGroups, key = { _, group -> group }) { displayIndex, group ->
                        val isUngrouped = group == ungroupedLabel
                        AutomationGroupManagerRow(
                            group = group,
                            count = groupCounts[group] ?: 0,
                            sortMode = sortMode,
                            canEdit = !isUngrouped,
                            canMoveUp = displayIndex > 0,
                            canMoveDown = displayIndex < displayGroups.lastIndex,
                            onMoveUp = { controller.moveAutomationChainDisplayGroup(displayGroups, displayIndex, displayIndex - 1) },
                            onMoveDown = { controller.moveAutomationChainDisplayGroup(displayGroups, displayIndex, displayIndex + 1) },
                            onEdit = { editingGroup = group },
                            onDelete = { controller.deleteAutomationChainGroup(group) },
                            openSwipeRowKey = openSwipeGroup,
                            onOpenSwipeRowChange = { openSwipeGroup = it }
                        )
                    }
                }
            }
        }
    }
    if (showCreateDialog) {
        AutomationGroupCreateDialog(
            controller = controller,
            onDismiss = { showCreateDialog = false },
            onCreated = {
                errorMessage = ""
                showCreateDialog = false
            },
            onError = { message -> errorMessage = message }
        )
    }
    editingGroup?.let { group ->
        AutomationGroupEditDialog(
            controller = controller,
            group = group,
            onDismiss = { editingGroup = null },
            onSaved = {
                errorMessage = ""
                editingGroup = null
            },
            onError = { message -> errorMessage = message }
        )
    }
}

@Composable
private fun AutomationGroupManagerRow(
    group: String,
    count: Int,
    sortMode: Boolean,
    canEdit: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    openSwipeRowKey: String?,
    onOpenSwipeRowChange: (String?) -> Unit
) {
    val actionWidth = 72.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    val canSwipe = canEdit && !sortMode
    var rowOffsetPx by remember(group, sortMode) { mutableStateOf(0f) }
    LaunchedEffect(canSwipe, openSwipeRowKey, group) {
        if (!canSwipe || openSwipeRowKey != group) rowOffsetPx = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        if (canSwipe) {
            Surface(
                shape = RowShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(actionWidth)
                    .height(36.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = {
                            rowOffsetPx = 0f
                            onOpenSwipeRowChange(null)
                            onEdit()
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑分组", modifier = Modifier.size(17.dp))
                    }
                    IconButton(
                        onClick = {
                            rowOffsetPx = 0f
                            onOpenSwipeRowChange(null)
                            onDelete()
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "删除分组", modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
        Surface(
            shape = RowShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .offset { IntOffset(rowOffsetPx.roundToInt(), 0) }
                .pointerInput(canSwipe, actionWidthPx, group) {
                    if (!canSwipe) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            onOpenSwipeRowChange(group)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            rowOffsetPx = (rowOffsetPx + dragAmount).coerceIn(-actionWidthPx, 0f)
                        },
                        onDragEnd = {
                            val nextOffset = if (rowOffsetPx < -actionWidthPx * 0.42f) -actionWidthPx else 0f
                            rowOffsetPx = nextOffset
                            onOpenSwipeRowChange(if (nextOffset < 0f) group else null)
                        },
                        onDragCancel = {
                            val nextOffset = if (rowOffsetPx < -actionWidthPx * 0.42f) -actionWidthPx else 0f
                            rowOffsetPx = nextOffset
                            onOpenSwipeRowChange(if (nextOffset < 0f) group else null)
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = group,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                if (sortMode) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移", modifier = Modifier.size(17.dp))
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移", modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationGroupCreateDialog(
    controller: EditorController,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
    onError: (String) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fixedDialogWidth(fraction = 0.60f, maxWidth = 260.dp)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = "新建分组",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolTextInputField(
                    value = groupName,
                    onValueChange = {
                        groupName = it
                        localError = ""
                    },
                    label = "分组名",
                    height = 40.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                if (localError.isNotBlank()) {
                    Text(
                        text = localError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (controller.addAutomationChainGroup(groupName)) {
                        onCreated()
                    } else {
                        val message = controller.statusMessage.ifBlank { "分组添加失败" }
                        localError = message
                        onError(message)
                    }
                },
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Text("新建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = ControlShape) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = PreviewShape,
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun AutomationGroupEditDialog(
    controller: EditorController,
    group: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onError: (String) -> Unit
) {
    var groupName by remember(group) { mutableStateOf(group) }
    var localError by remember(group) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fixedDialogWidth(fraction = 0.60f, maxWidth = 260.dp)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "编辑分组",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(34.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(19.dp))
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolTextInputField(
                    value = groupName,
                    onValueChange = {
                        groupName = it
                        localError = ""
                    },
                    label = "分组名",
                    height = 40.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                if (localError.isNotBlank()) {
                    Text(
                        text = localError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (controller.renameAutomationChainGroup(group, groupName)) {
                        onSaved()
                    } else {
                        val message = controller.statusMessage.ifBlank { "分组保存失败" }
                        localError = message
                        onError(message)
                    }
                },
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = ControlShape) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = PreviewShape,
        containerColor = MaterialTheme.colorScheme.surface
    )
}
