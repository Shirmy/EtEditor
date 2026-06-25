package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
fun FetchInfoCatalogScopeHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = {},
        title = {
            DialogTitleWithClose(
                title = "抓取规则说明",
                onDismiss = onDismiss,
                style = MaterialTheme.typography.titleSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "封面写回 cover、Section0001 文件名",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "简介写入 Section0002 文件名",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "目录写回所有除 cover、Section0001、Section0002 外的文件名",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        shape = PreviewShape,
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun FetchInfoSearchChoiceDialog(
    controller: EditorController,
    toolId: String,
    onDismiss: () -> Unit,
    onPrepared: () -> Unit,
    preparingLabel: String = "正在读取详情页",
    onManualUrl: (() -> Unit)? = null,
    onSelectChoice: suspend (FetchInfoSearchChoice) -> Boolean = { choice ->
        controller.selectFetchInfoSearchChoice(toolId, choice)
    }
) {
    val request = controller.fetchInfoSearchChoiceRequest ?: return
    if (request.toolId != toolId) return
    val scope = rememberCoroutineScope()
    var selectingUrl by remember(request) { mutableStateOf<String?>(null) }
    val sourceLabel = FetchInfoSources.label(request.parameters.source)

    Dialog(
        onDismissRequest = {
            if (selectingUrl == null) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            modifier = Modifier.fixedDialogWidth(fraction = 0.76f, maxWidth = 360.dp)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "选择${sourceLabel}搜索结果",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (onManualUrl != null) {
                        IconButton(
                            onClick = onManualUrl,
                            enabled = selectingUrl == null,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = "填写详情页地址", modifier = Modifier.size(19.dp))
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = selectingUrl == null,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(19.dp))
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(request.choices, key = { it.detailUrl }) { choice ->
                        val selecting = selectingUrl == choice.detailUrl
                        Surface(
                            onClick = {
                                if (selectingUrl == null) {
                                    selectingUrl = choice.detailUrl
                                    scope.launch {
                                        yieldToAppUiBeforeHeavyWork()
                                        val prepared = onSelectChoice(choice)
                                        selectingUrl = null
                                        if (prepared) onPrepared()
                                    }
                                }
                            },
                            shape = RowShape,
                            color = if (selecting) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                            },
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = choice.title.ifBlank { "未命名" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = choice.author.ifBlank { "未知作者" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if (selectingUrl != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = preparingLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FetchInfoRetryDialog(
    controller: EditorController,
    toolId: String,
    onDismiss: () -> Unit,
    onPrepared: () -> Unit
) {
    val request = controller.fetchInfoRetryRequest?.takeIf { it.toolId == toolId } ?: return
    val scope = rememberCoroutineScope()
    var urlText by remember(toolId) { mutableStateOf("") }
    var retrying by remember(toolId) { mutableStateOf(false) }
    val manualInput = request.message == "请填写详情页网址"

    AlertDialog(
        onDismissRequest = {
            if (!retrying) onDismiss()
        },
        modifier = Modifier
            .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = if (manualInput) "填写详情页地址" else "抓取失败",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = request.message.ifBlank { "所有来源都没有抓到内容" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ToolTextInputField(
                    label = "详情页网址",
                    value = urlText,
                    onValueChange = { urlText = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (retrying) "正在重新抓取..." else "可以留空，留空会按当前书名重新搜索。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (retrying) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!retrying) {
                        retrying = true
                        scope.launch {
                            yieldToAppUiBeforeHeavyWork()
                            val prepared = controller.retryFetchInfoAfterFailure(toolId, urlText)
                            retrying = false
                            if (prepared) onPrepared()
                        }
                    }
                },
                enabled = !retrying,
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Text("重新抓取")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !retrying,
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
