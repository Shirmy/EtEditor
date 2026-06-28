package com.eteditor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties

data class DeleteConfirmRequest(
    val title: String = "确认删除",
    val message: String,
    val confirmLabel: String = "\u5220\u9664",
    val onConfirm: () -> Unit
)

@Composable
fun FileRenameRunMessageDialog(
    message: String,
    onDismiss: () -> Unit
) {
    ToolRunMessageDialog(
        title = "文件重命名",
        message = message,
        onDismiss = onDismiss
    )
}

@Composable
internal fun ToolRunMessageDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .adaptiveDialogWidth(AdaptiveDialogWidth.Narrow)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = {},
        title = {
            DialogTitleWithClose(
                title = title,
                onDismiss = onDismiss,
                style = MaterialTheme.typography.titleSmall
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        shape = PreviewShape,
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun DeleteConfirmDialog(
    request: DeleteConfirmRequest,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = request.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    request.onConfirm()
                    onDismiss()
                },
                shape = ControlShape,
                contentPadding = CompactButtonPadding,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(request.confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = ControlShape, contentPadding = CompactButtonPadding) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = PreviewShape,
        containerColor = MaterialTheme.colorScheme.surface
    )
}

data class ConfirmActionRequest(
    val title: String,
    val message: String,
    val confirmLabel: String = "\u7ee7\u7eed",
    val onConfirm: () -> Unit
)

@Composable
fun ConfirmActionDialog(
    request: ConfirmActionRequest,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = request.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    request.onConfirm()
                    onDismiss()
                },
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Text(request.confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = ControlShape, contentPadding = CompactButtonPadding) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = PreviewShape,
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun SavePresetNameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = "保存预设",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            ToolTextInputField(
                label = "预设名字",
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Text("保存")
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

@Composable
fun AddStepNameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = "添加步骤",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            ToolTextInputField(
                label = "步骤名字",
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim()) },
                enabled = name.trim().isNotBlank(),
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Text("添加")
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
