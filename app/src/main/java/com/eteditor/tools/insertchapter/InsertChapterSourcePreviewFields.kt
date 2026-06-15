package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun InsertChapterSourcePreviewPane(
    preview: InsertChapterSourcePreview?,
    manual: Boolean,
    selectedSourceIndices: Set<Int>,
    sourceOrderReversed: Boolean = false,
    onToggle: (Int, Boolean) -> Unit,
    onReverseOrder: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    expanded: Boolean = false
) {
    val title = if (manual) "选择插入章节" else "预览"
    val listState = rememberLazyListState()
    LaunchedEffect(sourceOrderReversed, preview?.sourceUri) {
        listState.scrollToItem(0)
    }
    val content: @Composable ColumnScope.() -> Unit = {
        if (preview == null) {
            Text(
                text = "读取来源后显示章节",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val sourceItems = preview.items
            val displayedItems = if (sourceOrderReversed) sourceItems.asReversed() else sourceItems
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "共 ${sourceItems.size} 项",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (manual) {
                    Text(
                        text = "已选${selectedSourceIndices.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (manual && onReverseOrder != null) {
                    TextButton(
                        onClick = onReverseOrder,
                        shape = ControlShape,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (sourceOrderReversed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(if (sourceOrderReversed) "逆序" else "正序")
                    }
                }
            }
            val listModifier = if (expanded) {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            } else {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
            }
            Box(modifier = listModifier) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(displayedItems, key = { it.sourceIndex }) { item ->
                        val selected = item.sourceIndex in selectedSourceIndices
                        Surface(
                            shape = ControlShape,
                            color = if (selected && manual) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (manual) {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = { checked -> onToggle(item.sourceIndex, checked) }
                                    )
                                }
                                Text(
                                    text = (item.sourceIndex + 1).toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.width(28.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = item.title.ifBlank { "未命名章节" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (item.isVolume) FontWeight.SemiBold else FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.fileName.ifBlank { "TXT" },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = when {
                                        item.isVolume -> "卷"
                                        preview.sourceType == INSERT_CHAPTER_SOURCE_SOSAD &&
                                            item.wordCount <= 0 -> "未抓"
                                        else -> "${item.wordCount}"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                ContentScrollbar(
                    state = listState,
                    itemCount = sourceItems.size,
                    fixedItemHeight = 62.dp,
                    directDrag = true,
                    thumbFollowsDrag = true,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
    if (expanded) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)),
            modifier = modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                content()
            }
        }
    } else {
        NativeFormSection(title) {
            content()
        }
    }
}

@Composable
fun InsertChapterSourcePickerDialog(
    preview: InsertChapterSourcePreview?,
    selectedSourceIndices: Set<Int>,
    title: String = "选择插入章节",
    sourceOrderReversed: Boolean = false,
    onToggle: (Int, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onReverseOrder: () -> Unit = {},
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
    confirmEnabled: Boolean = true,
    confirmRunning: Boolean = false,
    confirmProgressLabel: String = "执行中",
    confirmProgress: Float = 0f,
    onConfirm: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()
    Dialog(
        onDismissRequest = {
            if (!confirmRunning) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            modifier = Modifier.fixedDialogWidth(fraction = 0.78f, maxWidth = 440.dp)
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
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onSelectAll,
                        enabled = !confirmRunning,
                        shape = ControlShape,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("全选")
                    }
                    TextButton(
                        onClick = onClear,
                        enabled = !confirmRunning,
                        shape = ControlShape,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("全不选")
                    }
                    TextButton(
                        onClick = onReverseOrder,
                        enabled = !confirmRunning,
                        shape = ControlShape,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (sourceOrderReversed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(if (sourceOrderReversed) "逆序" else "正序")
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !confirmRunning,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(19.dp))
                    }
                }
                val sourceItems = preview?.items.orEmpty()
                val displayedSourceItems = if (sourceOrderReversed) sourceItems.asReversed() else sourceItems
                Text(
                    text = "共 ${sourceItems.size} 项，已选${selectedSourceIndices.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RowShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    if (sourceItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "没有可选章节",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .padding(end = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                items(displayedSourceItems, key = { it.sourceIndex }) { item ->
                                    val selected = item.sourceIndex in selectedSourceIndices
                                    Surface(
                                        onClick = {
                                            if (!confirmRunning) onToggle(item.sourceIndex, !selected)
                                        },
                                        shape = RowShape,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = selected,
                                                onCheckedChange = { checked -> onToggle(item.sourceIndex, checked) },
                                                enabled = !confirmRunning
                                            )
                                            Text(
                                                text = (item.sourceIndex + 1).toString(),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                modifier = Modifier.width(28.dp)
                                            )
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = item.title.ifBlank { "未命名章节" },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (item.isVolume) FontWeight.SemiBold else FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = item.fileName.ifBlank { "TXT" },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
                                }
                            }
                            ContentScrollbar(
                                state = listState,
                                itemCount = sourceItems.size,
                                fixedItemHeight = 58.dp,
                                directDrag = true,
                                thumbFollowsDrag = true,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
                if (onConfirm != null && confirmLabel != null) {
                    if (confirmRunning) {
                        ToolRunProgress(
                            toolName = confirmProgressLabel,
                            progress = confirmProgress
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = confirmEnabled && !confirmRunning,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ControlShape,
                        contentPadding = CompactButtonPadding
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(confirmLabel)
                    }
                }
            }
        }
    }
}
