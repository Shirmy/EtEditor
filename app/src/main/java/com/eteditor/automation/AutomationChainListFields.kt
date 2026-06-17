package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun AutomationChainList(
    controller: EditorController,
    modifier: Modifier = Modifier,
    onRun: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCreate: () -> Unit
) {
    val ungroupedLabel = "未分组"
    var sortingGroup by remember { mutableStateOf<String?>(null) }
    var openSwipeChainId by remember { mutableStateOf<String?>(null) }
    val chainsByGroup = controller.automationChains.groupBy { chain -> chain.group.ifBlank { ungroupedLabel } }
    val orderedGroupNames = (
        controller.automationChainGroups.filter { group -> chainsByGroup.containsKey(group) } +
            chainsByGroup.keys.filter { group -> group != ungroupedLabel && group !in controller.automationChainGroups } +
            listOfNotNull(ungroupedLabel.takeIf { chainsByGroup.containsKey(it) })
        ).distinct()
    LaunchedEffect(sortingGroup) {
        if (sortingGroup != null) openSwipeChainId = null
    }
    LaunchedEffect(controller.automationChains, openSwipeChainId) {
        val openedId = openSwipeChainId ?: return@LaunchedEffect
        if (controller.automationChains.none { chain -> chain.id == openedId }) {
            openSwipeChainId = null
        }
    }
    if (controller.automationChains.isEmpty()) {
        AutomationChainEmptyState(
            onCreate = onCreate,
            modifier = modifier.fillMaxSize()
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        orderedGroupNames.forEach { groupName ->
            val chains = chainsByGroup[groupName].orEmpty()
            val chainIds = chains.map { it.id }
            item(key = "automation-group-$groupName") {
                AutomationGroupHeader(
                    groupName = groupName,
                    count = chains.size,
                    sorting = sortingGroup == groupName,
                    onToggleSort = {
                        sortingGroup = if (sortingGroup == groupName) null else groupName
                    }
                )
            }
            chains.forEachIndexed { chainIndex, chain ->
                item(key = chain.id) {
                    AutomationChainRow(
                        rowKey = chain.id,
                        name = chain.name,
                        selected = chain.id == controller.selectedAutomationChainId,
                        stepCount = chain.steps.size,
                        sorting = sortingGroup == groupName,
                        canMoveUp = chainIndex > 0,
                        canMoveDown = chainIndex < chains.lastIndex,
                        onMoveUp = {
                            controller.moveAutomationChainWithinDisplayGroup(
                                groupName,
                                chainIds,
                                chainIndex,
                                chainIndex - 1
                            )
                        },
                        onMoveDown = {
                            controller.moveAutomationChainWithinDisplayGroup(
                                groupName,
                                chainIds,
                                chainIndex,
                                chainIndex + 1
                            )
                        },
                        onRun = { onRun(chain.id) },
                        onEdit = { onEdit(chain.id) },
                        onDelete = { onDelete(chain.id) },
                        openSwipeRowKey = openSwipeChainId,
                        onOpenSwipeRowChange = { openSwipeChainId = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomationGroupHeader(
    groupName: String,
    count: Int,
    sorting: Boolean,
    onToggleSort: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = groupName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
            )
        }
        IconButton(
            onClick = onToggleSort,
            enabled = count > 1,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (sorting) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier.size(30.dp)
        ) {
            Icon(Icons.Outlined.SwapVert, contentDescription = "组内排序", modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun AutomationChainEmptyState(
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(50)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = "还没有执行链",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "把多个功能串成一条链,一键按顺序执行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onCreate,
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("新建执行链")
            }
        }
    }
}

private fun automationChainAvatarText(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "链"
    return trimmed.take(1).uppercase()
}

@Composable
private fun AutomationChainRow(
    rowKey: String,
    name: String,
    selected: Boolean,
    stepCount: Int,
    sorting: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    openSwipeRowKey: String?,
    onOpenSwipeRowChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var deleteConfirm by remember(rowKey) { mutableStateOf<DeleteConfirmRequest?>(null) }
    var rowOffsetPx by remember(rowKey) { mutableStateOf(0f) }
    val actionWidth = 72.dp
    val rowHeight = 56.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    val canSwipe = !sorting
    LaunchedEffect(canSwipe, openSwipeRowKey, rowKey) {
        if (!canSwipe || openSwipeRowKey != rowKey) rowOffsetPx = 0f
    }
    val rowColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clipToBounds()
    ) {
        if (canSwipe) {
            Surface(
                shape = RowShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(actionWidth)
                    .height(rowHeight)
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
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑 $name", modifier = Modifier.size(17.dp))
                    }
                    IconButton(
                        onClick = {
                            rowOffsetPx = 0f
                            onOpenSwipeRowChange(null)
                            deleteConfirm = DeleteConfirmRequest(
                                title = "确认删除执行中",
                                message = "确定删除执行链“$name”吗？链条里的步骤也会一起移除。",
                                onConfirm = onDelete
                            )
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "删除 $name", modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
        Surface(
            shape = RowShape,
            color = rowColor,
            border = BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(rowOffsetPx.roundToInt(), 0) }
                .pointerInput(canSwipe, actionWidthPx, rowKey) {
                    if (!canSwipe) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            onOpenSwipeRowChange(rowKey)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            rowOffsetPx = (rowOffsetPx + dragAmount).coerceIn(-actionWidthPx, 0f)
                        },
                        onDragEnd = {
                            val nextOffset = if (rowOffsetPx < -actionWidthPx * 0.45f) -actionWidthPx else 0f
                            rowOffsetPx = nextOffset
                            onOpenSwipeRowChange(if (nextOffset < 0f) rowKey else null)
                        },
                        onDragCancel = {
                            val nextOffset = if (rowOffsetPx < -actionWidthPx * 0.45f) -actionWidthPx else 0f
                            rowOffsetPx = nextOffset
                            onOpenSwipeRowChange(if (nextOffset < 0f) rowKey else null)
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            },
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = automationChainAvatarText(name),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    }
                ) {
                    Text(
                        text = "$stepCount 步",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (sorting) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移 $name", modifier = Modifier.size(17.dp))
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移 $name", modifier = Modifier.size(17.dp))
                    }
                } else {
                    IconButton(
                        onClick = {
                            rowOffsetPx = 0f
                            onOpenSwipeRowChange(null)
                            onRun()
                        },
                        enabled = stepCount > 0,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = "执行 $name", modifier = Modifier.size(18.dp))
                    }
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
