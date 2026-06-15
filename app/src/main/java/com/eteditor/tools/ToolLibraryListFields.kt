package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun EditorToolLibraryList(
    controller: EditorController,
    modifier: Modifier = Modifier,
    runningToolId: String?,
    onRunTool: (EditorTool) -> Unit,
    onEditTool: (String) -> Unit,
    onDeleteTool: (String) -> Unit
) {
    val visibleTools = controller.editorToolsForCurrentDocument()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 4.dp)
    ) {
        if (visibleTools.isEmpty()) {
            item {
                Text(
                    text = "暂无预设",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                )
            }
        } else {
            items(visibleTools, key = { it.id }) { tool ->
                EditorToolListRow(
                    tool = tool,
                    baseLabel = controller.editorToolBaseLabel(tool),
                    running = tool.id == runningToolId,
                    onRun = { onRunTool(tool) },
                    onEdit = { onEditTool(tool.id) },
                    onDelete = { onDeleteTool(tool.id) }
                )
            }
        }
    }
}

@Composable
private fun EditorToolListRow(
    tool: EditorTool,
    baseLabel: String,
    running: Boolean,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var deleteConfirm by remember(tool.id, tool.name) { mutableStateOf<DeleteConfirmRequest?>(null) }
    val requestDelete = {
        deleteConfirm = DeleteConfirmRequest(
            title = "确认删除预设",
            message = "确定删除预设“${tool.name}”吗？使用它的执行步骤会失效。",
            onConfirm = onDelete
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(horizontal = 2.dp, vertical = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .height(0.dp)
                .width(0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑预设", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = requestDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除预设",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        Surface(
            shape = RowShape,
            color = if (running) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 6.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onRun,
                    enabled = !running,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "执行预设", modifier = Modifier.size(18.dp))
                }
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = baseLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 92.dp)
                )
                IconButton(
                    onClick = onEdit,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑预设", modifier = Modifier.size(17.dp))
                }
                IconButton(
                    onClick = requestDelete,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除预设", modifier = Modifier.size(17.dp))
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

