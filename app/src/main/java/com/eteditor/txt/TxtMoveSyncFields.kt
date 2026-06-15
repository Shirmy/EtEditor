package com.eteditor

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal enum class PendingTxtMoveSyncAction(val label: String, val waitBeforeAction: Boolean = true) {
    OpenFile("打开文件"),
    Save("保存", waitBeforeAction = false),
    Exit("退出")
}

internal fun CoroutineScope.launchAfterTxtMoveChapterSync(
    controller: EditorController,
    action: String,
    block: suspend () -> Unit
) {
    launch {
        if (controller.awaitTxtMoveChapterSyncBefore(action)) {
            block()
        }
    }
}

@Composable
internal fun TxtMoveSyncConfirmDialog(
    action: PendingTxtMoveSyncAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val message = when (action) {
        PendingTxtMoveSyncAction.OpenFile ->
            "目录已刷新，正文还在后台同步。打开新文件前需要先等同步完成，否则这次移动可能还没写回正文件"
        PendingTxtMoveSyncAction.Exit ->
            "目录已刷新，正文还在后台同步。退出前需要先等同步完成，否则这次移动可能还没写回正文件"
        else ->
            "目录已刷新，正文还在后台同步。需要先等同步完成后“${action.label}”"
    }
    val confirmText = when (action) {
        PendingTxtMoveSyncAction.OpenFile -> "等待后打开"
        PendingTxtMoveSyncAction.Exit -> "等待后退"
        else -> "等待完成"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = "章节移动未完成",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = PreviewShape,
        containerColor = MaterialTheme.colorScheme.surface
    )
}
