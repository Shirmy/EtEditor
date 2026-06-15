package com.eteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp

@Composable
internal fun DirectoryOptionBar(
    reverseOrderSelected: Boolean,
    onToggleReverseOrder: () -> Unit,
    allVolumesCollapsed: Boolean,
    onToggleAllVolumes: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 8.dp, end = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DirectoryCompactActionButton(
            icon = Icons.Outlined.SwapVert,
            text = "逆序",
            selected = reverseOrderSelected,
            onClick = onToggleReverseOrder,
            outlined = true,
            modifier = Modifier.weight(1f)
        )
        DirectoryCompactActionButton(
            icon = if (allVolumesCollapsed) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
            text = if (allVolumesCollapsed) "展开" else "收起",
            selected = false,
            onClick = onToggleAllVolumes,
            outlined = true,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun TxtDirectoryOptionBar(
    controller: EditorController,
    documentSessionKey: Int = 0,
    reverseOrderSelected: Boolean,
    onToggleReverseOrder: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onPickChapter: (Int) -> Unit
) {
    var issueDialogType by remember(documentSessionKey) { mutableStateOf<TxtChapterIssueType?>(null) }
    val issueSummary = remember(controller.chapters) { txtChapterIssueSummary(controller.chapters) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 8.dp, end = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DirectoryCompactActionButton(
            text = "逆序",
            icon = Icons.Outlined.SwapVert,
            selected = reverseOrderSelected,
            onClick = onToggleReverseOrder,
            outlined = true,
            modifier = Modifier.weight(1f)
        )
        DirectoryCompactActionButton(
            text = "刷新",
            icon = Icons.Outlined.Refresh,
            selected = false,
            onClick = onRefreshCatalog,
            outlined = true,
            modifier = Modifier.weight(1f)
        )
        if (issueSummary.lengthChapters.isNotEmpty()) {
            TxtChapterIssueButton(
                type = TxtChapterIssueType.Length,
                icon = Icons.Outlined.Straighten,
                onClick = { issueDialogType = TxtChapterIssueType.Length },
                modifier = Modifier.width(28.dp)
            )
        }
        if (issueSummary.otherChapters.isNotEmpty()) {
            TxtChapterIssueButton(
                type = TxtChapterIssueType.Other,
                icon = Icons.Outlined.Info,
                onClick = { issueDialogType = TxtChapterIssueType.Other },
                modifier = Modifier.width(28.dp)
            )
        }
    }

    issueDialogType?.let { type ->
        TxtChapterIssueDialog(
            summary = issueSummary,
            type = type,
            onPickChapter = { chapter ->
                issueDialogType = null
                onPickChapter(chapter.index - 1)
            },
            onDismiss = { issueDialogType = null }
        )
    }
}

@Composable
internal fun TxtBulkRemoveCatalogBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 8.dp, end = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DirectoryCompactActionButton(
            text = "取消",
            icon = Icons.Outlined.Close,
            selected = false,
            onClick = onCancel,
            outlined = true,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "已选$selectedCount",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        DirectoryCompactActionButton(
            text = "移除",
            icon = Icons.Outlined.Delete,
            selected = selectedCount > 0,
            onClick = onConfirm,
            outlined = true,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun TxtBulkMoveChapterBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 8.dp, end = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DirectoryCompactActionButton(
            text = "取消",
            icon = Icons.Outlined.Close,
            selected = false,
            onClick = onCancel,
            outlined = true,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "已选$selectedCount",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        DirectoryCompactActionButton(
            text = "移动",
            icon = Icons.AutoMirrored.Outlined.DriveFileMove,
            selected = selectedCount > 0,
            onClick = onConfirm,
            outlined = true,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun TxtBulkDeleteChapterBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 8.dp, end = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DirectoryCompactActionButton(
            text = "取消",
            icon = Icons.Outlined.Close,
            selected = false,
            onClick = onCancel,
            outlined = true,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "已选$selectedCount",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        DirectoryCompactActionButton(
            text = "删除",
            icon = Icons.Outlined.DeleteSweep,
            selected = selectedCount > 0,
            onClick = onConfirm,
            outlined = true,
            modifier = Modifier.weight(1f)
        )
    }
}
