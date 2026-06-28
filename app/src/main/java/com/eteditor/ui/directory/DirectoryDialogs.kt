package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eteditor.core.ChapterInfo
import com.eteditor.core.DocumentKind

@Composable
internal fun AddVolumeDialog(
    controller: EditorController,
    fixedInsertIndex: Int? = null,
    onDismiss: () -> Unit
) {
    var kind by remember { mutableStateOf(VOLUME_KIND_EXTRA) }
    var volumeName by remember { mutableStateOf(controller.defaultVolumeTitle(VOLUME_KIND_EXTRA)) }
    var insertIndexValue by remember(controller.chapters.size) { mutableStateOf("0") }
    val kindOptions = listOf(
        VOLUME_KIND_NORMAL to "普通卷",
        VOLUME_KIND_EXTRA to "番外卷",
        VOLUME_KIND_SPECIAL_EXTRA to "特殊番外卷"
    )
    val kindLabel = kindOptions.firstOrNull { it.first == kind }?.second ?: "普通卷"
    val insertOptions = buildList {
        add("0" to "书籍开头")
        controller.chapters.forEach { chapter ->
            val title = chapter.title.ifBlank { chapter.fileName.ifBlank { "\u65e0\u6807\u9898" } }
            add(chapter.index.toString() to "插入到 $title 之后")
        }
    }.distinctBy { it.first }
    val insertLabel = insertOptions.firstOrNull { it.first == insertIndexValue }?.second
        ?: insertOptions.firstOrNull()?.second
        ?: "书籍开头"
    val insertIndex = fixedInsertIndex?.coerceIn(0, controller.chapters.size)
        ?: insertIndexValue.toIntOrNull()?.coerceIn(0, controller.chapters.size)
        ?: 0
    val extraExists = controller.hasExtraVolume()
    val canAdd = controller.kind == DocumentKind.Epub &&
        !(kind == VOLUME_KIND_EXTRA && extraExists)

    LaunchedEffect(kind) {
        volumeName = controller.defaultVolumeTitle(kind)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = "增加卷",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "类型",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    PanelDropdownField(
                        value = kindLabel,
                        options = kindOptions,
                        onSelect = { value -> kind = value },
                        height = 42.dp
                    )
                }
                ToolTextInputField(
                    label = "卷名",
                    value = volumeName,
                    onValueChange = { volumeName = it },
                    height = 42.dp
                )
                if (fixedInsertIndex == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = "插入位置",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        DirectoryPickerButton(
                            value = insertLabel,
                            options = insertOptions,
                            selectedKey = insertIndexValue,
                            dialogTitle = "选择插入位置",
                            onSelect = { value -> insertIndexValue = value },
                            height = 42.dp
                        )
                    }
                }
                ToolReadOnlyField(
                    label = "文件名",
                    value = controller.volumeFileNamePreview(kind).ifBlank { "-" },
                    height = 38.dp
                )
                if (kind == VOLUME_KIND_EXTRA && extraExists) {
                    Text(
                        text = "番外卷已存在",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canAdd,
                onClick = {
                    val name = volumeName
                    if (controller.addEpubVolume(kind, name, insertIndex)) {
                        onDismiss()
                    }
                },
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

@Composable
internal fun EpubDirectoryItemMenuDialog(
    chapter: ChapterInfo,
    allowStructureActions: Boolean = true,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onAddVolume: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            shadowElevation = 10.dp,
            modifier = Modifier.adaptiveDialogWidth(AdaptiveDialogWidth.Menu)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = chapter.title.ifBlank { chapter.fileName.ifBlank { "\u65e0\u6807\u9898" } },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                EpubDirectoryMenuButton(
                    icon = Icons.Outlined.Edit,
                    text = "编辑",
                    onClick = onEdit
                )
                if (allowStructureActions) {
                    EpubDirectoryMenuButton(
                        icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                        text = "移动至",
                        onClick = onMove
                    )
                }
                EpubDirectoryMenuButton(
                    icon = Icons.Outlined.CreateNewFolder,
                    text = "增卷",
                    onClick = onAddVolume
                )
                if (allowStructureActions) {
                    EpubDirectoryMenuButton(
                        icon = Icons.Outlined.DeleteSweep,
                        text = "删除此章节",
                        destructive = true,
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

@Composable
internal fun EpubDirectoryEditDialog(
    controller: EditorController,
    chapter: ChapterInfo,
    onDismiss: () -> Unit
) {
    var fileName by remember(chapter.index, chapter.fileName) { mutableStateOf(chapter.fileName) }
    var title by remember(chapter.index, chapter.title) { mutableStateOf(chapter.title) }
    val canSave = fileName.trim().isNotBlank() && title.trim().isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = "编辑目录项",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextInputField(
                    label = "文件名",
                    value = fileName,
                    onValueChange = { fileName = it },
                    height = 42.dp
                )
                ToolTextInputField(
                    label = "标题",
                    value = title,
                    onValueChange = { title = it },
                    height = 42.dp
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    if (controller.updateChapterItem(chapter.index - 1, fileName.trim(), title.trim())) {
                        onDismiss()
                    }
                },
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Text("保存")
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
internal fun TxtDirectoryItemMenuDialog(
    chapter: ChapterInfo,
    onDismiss: () -> Unit,
    onMove: () -> Unit,
    onStartBulkMove: () -> Unit,
    onRemove: () -> Unit,
    onStartBulkRemove: () -> Unit,
    onDelete: () -> Unit,
    onStartBulkDelete: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            shadowElevation = 10.dp,
            modifier = Modifier.adaptiveDialogWidth(AdaptiveDialogWidth.Menu)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = chapter.title.ifBlank { "章节" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                EpubDirectoryMenuButton(
                    icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                    text = "移动",
                    onClick = onMove,
                    onLongClick = onStartBulkMove
                )
                EpubDirectoryMenuButton(
                    icon = Icons.Outlined.Delete,
                    text = "移除",
                    onClick = onRemove,
                    onLongClick = onStartBulkRemove
                )
                EpubDirectoryMenuButton(
                    icon = Icons.Outlined.DeleteSweep,
                    text = "删除",
                    destructive = true,
                    onClick = onDelete,
                    onLongClick = onStartBulkDelete
                )
            }
        }
    }
}

@Composable
internal fun TxtDirectoryDeleteOptionsDialog(
    chapter: ChapterInfo,
    onDismiss: () -> Unit,
    onStartBulkDelete: () -> Unit,
    onDeleteEmptyChapters: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            shadowElevation = 10.dp,
            modifier = Modifier.adaptiveDialogWidth(AdaptiveDialogWidth.Menu)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = chapter.title.ifBlank { "章节" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                EpubDirectoryMenuButton(
                    icon = Icons.Outlined.DeleteSweep,
                    text = "批量删除",
                    destructive = true,
                    onClick = onStartBulkDelete
                )
                EpubDirectoryMenuButton(
                    icon = Icons.Outlined.Delete,
                    text = "一键删除重复章节",
                    destructive = true,
                    onClick = onDeleteEmptyChapters
                )
            }
        }
    }
}

@Composable
private fun EpubDirectoryMenuButton(
    icon: ImageVector,
    text: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val longClick = onLongClick
    val clickModifier = if (longClick == null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier.pointerInput(onClick, longClick) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = { longClick() }
            )
        }
    }
    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .then(clickModifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
