package com.eteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun EpubLongPressSplitChapterDialog(
    controller: EditorController,
    chapterIndex: Int,
    lineIndex: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val chapter = controller.chapters.getOrNull(chapterIndex)
    val lineCount = remember(chapterIndex, controller.documentContentVersion) {
        controller.epubChapterBodyLineCount(chapterIndex)
    }
    val defaultTitle = remember(chapterIndex, lineIndex, controller.documentContentVersion) {
        controller.epubSplitChapterDefaultTitle(chapterIndex, lineIndex)
    }
    var title by remember(chapterIndex, lineIndex, defaultTitle) { mutableStateOf(defaultTitle) }
    val canSplit = chapter != null && lineIndex in 1 until lineCount
    val chapterLabel = chapter?.let { it.title.ifBlank { it.fileName } }.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = "分章",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = chapterLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "将从第 ${lineIndex + 1} 行开始生成新章节",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ToolTextInputField(
                    label = "新章节标题",
                    value = title,
                    onValueChange = { title = it },
                    height = 42.dp
                )
                if (!canSplit) {
                    Text(
                        text = if (lineIndex <= 0) {
                            "第一行不能作为分章位置。"
                        } else {
                            "当前行超出正文范围，无法分章。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSplit,
                onClick = { onConfirm(title) },
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Text("分章")
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
internal fun TxtSupplementChapterDialog(
    lineText: String,
    initialChapterNumber: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> String?
) {
    var chapterNumber by remember(initialChapterNumber) { mutableStateOf(initialChapterNumber) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("补章节") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "当前行：${lineText.trim().ifBlank { "空行" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                CompactDialogTextInputField(
                    label = "章节号",
                    value = chapterNumber,
                    onValueChange = {
                        chapterNumber = it.filterNot { char -> char.isWhitespace() }
                        errorMessage = null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val error = onConfirm(chapterNumber)
                    if (error == null) {
                        onDismiss()
                    } else {
                        errorMessage = error
                    }
                },
                enabled = chapterNumber.isNotBlank(),
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Text("确认")
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
