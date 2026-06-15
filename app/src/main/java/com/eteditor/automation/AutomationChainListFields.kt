package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SwapVert
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
import kotlin.math.roundToInt

@Composable
internal fun AutomationChainList(
    controller: EditorController,
    modifier: Modifier = Modifier,
    onRun: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (controller.automationChains.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .padding(top = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无执行中",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        orderedGroupNames.forEach { groupName ->
            val chains = chainsByGroup[groupName].orEmpty()
            item(key = "automation-group-$groupName") {
                Surface(
                    shape = RowShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(7.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = groupName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${chains.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = {
                                    sortingGroup = if (sortingGroup == groupName) null else groupName
                                },
                                enabled = chains.size > 1,
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = if (sortingGroup == groupName) {
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
                        chains.forEachIndexed { chainIndex, chain ->
                            val chainIds = chains.map { it.id }
                            AutomationChainRow(
                                rowKey = chain.id,
                                name = chain.name,
                                selected = chain.id == controller.selectedAutomationChainId,
                                stepCount = chain.steps.size,
                                stepSummary = automationChainStepFlow(controller, chain),
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
    }
}

private fun automationChainStepFlow(controller: EditorController, chain: AutomationChain): String {
    if (chain.steps.isEmpty()) return "未添加步骤"
    return chain.steps.mapIndexed { index, step ->
        "${index + 1}.${controller.automationStepLabel(step)}"
    }.joinToString(" → ")
}

@Composable
private fun AutomationChainRow(
    rowKey: String,
    name: String,
    selected: Boolean,
    stepCount: Int,
    stepSummary: String,
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
                    .height(62.dp)
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
            Column(
                modifier = Modifier.padding(start = 8.dp, top = 7.dp, end = 8.dp, bottom = 7.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(18.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary else rowColor,
                                RoundedCornerShape(999.dp)
                            )
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$stepCount 步",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sorting) {
                        IconButton(
                            onClick = onMoveUp,
                            enabled = canMoveUp,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移 $name", modifier = Modifier.size(17.dp))
                        }
                        IconButton(
                            onClick = onMoveDown,
                            enabled = canMoveDown,
                            modifier = Modifier.size(28.dp)
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
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            ),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = "执行 $name", modifier = Modifier.size(17.dp))
                        }
                    }
                }
                Text(
                    text = stepSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp, end = 2.dp)
                )
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
