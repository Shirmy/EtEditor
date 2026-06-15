package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eteditor.core.ChapterInfo

@Composable
internal fun EpubMoveChapterDialog(
    controller: EditorController,
    chapter: ChapterInfo,
    onDismiss: () -> Unit
) {
    val currentIndex = chapter.index - 1
    var reverseOrder by remember(chapter.index) { mutableStateOf(false) }
    val options = remember(controller.chapters) {
        listOf(
            DirectoryPickerOption(
                key = MOVE_TARGET_BOOK_START.toString(),
                label = "书籍开头",
                isSpecial = true
            ),
            DirectoryPickerOption(
                key = MOVE_TARGET_BOOK_END.toString(),
                label = "书籍结尾",
                isSpecial = true
            )
        ) + controller.chapters.map { item ->
            DirectoryPickerOption(
                key = (item.index - 1).toString(),
                label = item.title.ifBlank { item.fileName },
                tocLevel = item.tocLevel,
                isVolume = item.isVolume
            )
        }
    }
    val displayedOptions = remember(options, reverseOrder) {
        val specialOptions = options.filter { it.isSpecial }
        val chapterOptions = options.filterNot { it.isSpecial }
        specialOptions + if (reverseOrder) chapterOptions.asReversed() else chapterOptions
    }
    val pickerOptions = remember(displayedOptions, currentIndex) {
        displayedOptions.map { option ->
            val targetIndex = option.key.toIntOrNull()
            option.copy(
                current = targetIndex == currentIndex,
                enabled = targetIndex != currentIndex
            )
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            shadowElevation = 10.dp,
            modifier = Modifier.fixedDialogWidth(fraction = 0.72f, maxWidth = 320.dp)
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
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "移动至",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = chapter.title.ifBlank { chapter.fileName },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                DirectoryOptionButton(
                    text = "逆序显示",
                    icon = Icons.Outlined.SwapVert,
                    selected = reverseOrder,
                    onClick = { reverseOrder = !reverseOrder },
                    modifier = Modifier.widthIn(min = 96.dp)
                )
                DirectoryPickerList(
                    options = pickerOptions,
                    onSelect = { key ->
                        val targetIndex = key.toIntOrNull() ?: return@DirectoryPickerList
                        if (controller.epubMoveChapterAfter(currentIndex, targetIndex)) {
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    scrollbarDirectDrag = true,
                    scrollbarThumbFollowsDrag = true
                )
            }
        }
    }
}

@Composable
internal fun TxtBulkMoveChapterTargetDialog(
    controller: EditorController,
    selectedChapterIndexes: Set<Int>,
    onDismiss: () -> Unit,
    onSelectTarget: (Int) -> Unit
) {
    var reverseOrder by remember(selectedChapterIndexes) { mutableStateOf(false) }
    val selectedIndexes = remember(selectedChapterIndexes, controller.chapters) {
        selectedChapterIndexes
            .filter { index -> index in controller.chapters.indices }
            .toSet()
    }
    val options = remember(controller.chapters, selectedIndexes) {
        listOf(
            DirectoryPickerOption(
                key = MOVE_TARGET_BOOK_START.toString(),
                label = "书籍开头",
                isSpecial = true
            ),
            DirectoryPickerOption(
                key = MOVE_TARGET_BOOK_END.toString(),
                label = "书籍结尾",
                isSpecial = true
            )
        ) + controller.chapters.map { item ->
            val chapterIndex = item.index - 1
            val title = item.title.ifBlank { "第 ${item.index} 章" }
            DirectoryPickerOption(
                key = chapterIndex.toString(),
                label = "$title 后",
                enabled = chapterIndex !in selectedIndexes,
                tocLevel = item.tocLevel
            )
        }
    }
    val displayedOptions = remember(options, reverseOrder) {
        val specialOptions = options.filter { it.isSpecial }
        val chapterOptions = options.filterNot { it.isSpecial }
        specialOptions + if (reverseOrder) chapterOptions.asReversed() else chapterOptions
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            shadowElevation = 10.dp,
            modifier = Modifier.adaptiveDialogWidth(AdaptiveDialogWidth.Narrow)
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
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "移动到章节后",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "已选${selectedIndexes.size} 个",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                DirectoryOptionButton(
                    text = "逆序显示",
                    icon = Icons.Outlined.SwapVert,
                    selected = reverseOrder,
                    onClick = { reverseOrder = !reverseOrder },
                    modifier = Modifier.widthIn(min = 96.dp)
                )
                DirectoryPickerList(
                    options = displayedOptions,
                    onSelect = { key ->
                        val targetIndex = key.toIntOrNull() ?: return@DirectoryPickerList
                        onDismiss()
                        onSelectTarget(targetIndex)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    scrollbarDirectDrag = true,
                    scrollbarThumbFollowsDrag = true
                )
            }
        }
    }
}

@Composable
internal fun TxtMoveChapterDialog(
    controller: EditorController,
    chapter: ChapterInfo,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentIndex = chapter.index - 1
    var reverseOrder by remember(chapter.index) { mutableStateOf(false) }
    val options = remember(controller.chapters) {
        listOf(
            DirectoryPickerOption(
                key = MOVE_TARGET_BOOK_START.toString(),
                label = "书籍开头",
                isSpecial = true
            ),
            DirectoryPickerOption(
                key = MOVE_TARGET_BOOK_END.toString(),
                label = "书籍结尾",
                isSpecial = true
            )
        ) + controller.chapters.map { item ->
            val title = item.title.ifBlank { "第 ${item.index} 章" }
            DirectoryPickerOption(
                key = (item.index - 1).toString(),
                label = "$title 后",
                tocLevel = item.tocLevel
            )
        }
    }
    val displayedOptions = remember(options, reverseOrder) {
        val specialOptions = options.filter { it.isSpecial }
        val chapterOptions = options.filterNot { it.isSpecial }
        specialOptions + if (reverseOrder) chapterOptions.asReversed() else chapterOptions
    }
    val pickerOptions = remember(displayedOptions, currentIndex) {
        displayedOptions.map { option ->
            val targetIndex = option.key.toIntOrNull()
            option.copy(
                current = targetIndex == currentIndex,
                enabled = targetIndex != currentIndex
            )
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            shadowElevation = 10.dp,
            modifier = Modifier.fixedDialogWidth(fraction = 0.72f, maxWidth = 320.dp)
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
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "移动",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = chapter.title.ifBlank { "章节" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                DirectoryOptionButton(
                    text = "逆序显示",
                    icon = Icons.Outlined.SwapVert,
                    selected = reverseOrder,
                    onClick = { reverseOrder = !reverseOrder },
                    modifier = Modifier.widthIn(min = 96.dp)
                )
                DirectoryPickerList(
                    options = pickerOptions,
                    onSelect = { key ->
                        val targetIndex = key.toIntOrNull() ?: return@DirectoryPickerList
                        scope.launchAfterTxtMoveChapterSync(controller, "移动章节") {
                            if (controller.txtMoveChapterBlock(currentIndex, targetIndex)) {
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    scrollbarDirectDrag = true,
                    scrollbarThumbFollowsDrag = true
                )
            }
        }
    }
}
