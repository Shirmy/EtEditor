package com.eteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.eteditor.core.ChapterInfo
import kotlinx.coroutines.launch

@Composable
internal fun DirectoryBulkTitleEditDialog(
    controller: EditorController,
    chaptersSnapshot: List<ChapterInfo>,
    targetIndexes: List<Int>,
    scopeLabel: String,
    onDismiss: () -> Unit,
    onApplied: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var regexEnabled by remember { mutableStateOf(false) }
    var previewEnabled by remember { mutableStateOf(false) }
    var previewItems by remember { mutableStateOf<List<BulkTitleEditPlanItem>?>(null) }
    var message by remember { mutableStateOf(scopeLabel) }
    var applying by remember { mutableStateOf(false) }

    fun buildPlan(): BulkTitleEditPlanBuildResult {
        return buildBulkTitleEditPlan(
            chapters = chaptersSnapshot,
            targetIndexes = targetIndexes,
            find = findText,
            replace = replaceText,
            regex = regexEnabled
        )
    }

    AlertDialog(
        onDismissRequest = {
            if (!applying) onDismiss()
        },
        modifier = Modifier
            .adaptiveDialogWidth(AdaptiveDialogWidth.Medium)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = "批量编辑标题",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = scopeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ToolTextInputField(
                    label = "查找",
                    value = findText,
                    onValueChange = {
                        findText = it
                        previewItems = null
                        message = scopeLabel
                    },
                    enabled = !applying,
                    height = 42.dp
                )
                ToolTextInputField(
                    label = "替换为",
                    value = replaceText,
                    onValueChange = {
                        replaceText = it
                        previewItems = null
                        message = scopeLabel
                    },
                    enabled = !applying,
                    height = 42.dp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("正则", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = regexEnabled,
                        onCheckedChange = {
                            regexEnabled = it
                            previewItems = null
                            message = scopeLabel
                        },
                        enabled = !applying
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("预览", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = previewEnabled,
                        onCheckedChange = {
                            previewEnabled = it
                            if (!it) previewItems = null
                        },
                        enabled = !applying
                    )
                }
                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message == scopeLabel) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
                val shownPreview = previewItems?.filter { it.changed }.orEmpty()
                if (previewEnabled && shownPreview.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(shownPreview, key = { it.chapterIndex }) { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.oldTitle.ifBlank { "（空标题）" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "→ ${item.newTitle}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if (applying) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "正在修改…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !applying && findText.isNotEmpty(),
                onClick = {
                    if (applying) return@Button
                    val plan = buildPlan()
                    message = plan.message
                    if (plan.changedCount <= 0) {
                        previewItems = if (previewEnabled) plan.items else null
                        return@Button
                    }
                    if (previewEnabled && previewItems == null) {
                        previewItems = plan.items
                        return@Button
                    }
                    applying = true
                    scope.launch {
                        try {
                            val changed = controller.applyDirectoryBulkTitleEdits(
                                plan.items.filter { it.changed }.map { it.chapterIndex to it.newTitle }
                            )
                            if (changed > 0) {
                                onApplied()
                            } else {
                                // 失败或无需修改：保留弹窗，展示原因，便于重试或取消。
                                message = controller.statusMessage.ifBlank { "没有修改任何标题" }
                            }
                        } catch (error: Throwable) {
                            message = error.message?.takeIf { it.isNotBlank() }
                                ?: controller.statusMessage.ifBlank { "修改失败，请重试" }
                        } finally {
                            applying = false
                        }
                    }
                },
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Text(
                    when {
                        previewEnabled && previewItems == null -> "预览"
                        previewEnabled -> "确认修改"
                        else -> "直接修改"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !applying,
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
