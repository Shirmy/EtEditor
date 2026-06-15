package com.eteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eteditor.core.DocumentKind

@Composable
internal fun BodyPreviewHeader(
    controller: EditorController,
    editing: Boolean,
    modifiedCount: Int,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onToggleEpubLongPressSplitChapter: () -> Unit,
    onToggleTxtSupplementLongPressMode: () -> Unit,
    onFormatTxt: () -> Unit,
    onStartEditing: () -> Unit,
    onSaveEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (controller.kind == DocumentKind.Epub) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!editing) {
                IconButton(
                    onClick = onPreviousChapter,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "上一章")
                }
            }
            if (!editing) {
                IconButton(
                    onClick = onToggleEpubLongPressSplitChapter,
                    enabled = controller.previewText.isNotBlank() &&
                        !controller.busy &&
                        !controller.isEpubPackageTextPreviewSource(),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (controller.epubLongPressSplitChapter) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (controller.epubLongPressSplitChapter) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    ),
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Outlined.AutoStories,
                        contentDescription = if (controller.epubLongPressSplitChapter) "关闭长按正文操作" else "开启长按正文操作",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            ChapterTitleWithLineEndingHint(
                title = controller.previewHeaderTitle(),
                modifier = Modifier.weight(1f)
            )
            if (editing) {
                BodyEditToolbarButtons(
                    editing = true,
                    enabled = !controller.busy,
                    onStart = onStartEditing,
                    onSave = onSaveEditing,
                    onCancel = onCancelEditing,
                    size = 34.dp,
                    modifiedCount = modifiedCount
                )
            }
            if (!editing) {
                IconButton(
                    onClick = onNextChapter,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "下一章")
                }
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val showTxtChapterSwitcher = controller.kind == DocumentKind.Txt &&
                !editing &&
                controller.txtPreviewMode == TXT_PREVIEW_MODE_CHAPTER &&
                controller.previewChapterCount > 0
            if (showTxtChapterSwitcher) {
                IconButton(
                    onClick = onPreviousChapter,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "上一章")
                }
            }
            if (controller.kind == DocumentKind.Txt && !editing) {
                IconButton(
                    onClick = onToggleTxtSupplementLongPressMode,
                    enabled = controller.previewText.isNotBlank() && !controller.busy,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (controller.txtSupplementLongPressMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (controller.txtSupplementLongPressMode) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    ),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.AutoStories,
                        contentDescription = if (controller.txtSupplementLongPressMode) "关闭长按补章节" else "开启长按补章节",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            ChapterTitleWithLineEndingHint(
                title = controller.previewHeaderTitle(),
                modifier = Modifier.weight(1f)
            )
            if (controller.kind == DocumentKind.Txt && editing) {
                BodyEditToolbarButtons(
                    editing = true,
                    enabled = !controller.busy,
                    onStart = onStartEditing,
                    onSave = onSaveEditing,
                    onCancel = onCancelEditing,
                    size = 32.dp,
                    modifiedCount = modifiedCount
                )
            }
            if (controller.kind == DocumentKind.Txt && !editing) {
                IconButton(
                    onClick = onFormatTxt,
                    enabled = !controller.busy,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    ),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.CleaningServices,
                        contentDescription = "整理全文",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (showTxtChapterSwitcher) {
                IconButton(
                    onClick = onNextChapter,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "下一章")
                }
            }
        }
    }
}

@Composable
internal fun BodyEditToolbarButtons(
    editing: Boolean,
    enabled: Boolean,
    onStart: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    size: Dp,
    modifiedCount: Int = 0
) {
    if (editing) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onSave,
                enabled = enabled,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                ),
                modifier = Modifier.size(size)
            ) {
                Icon(Icons.Outlined.Save, contentDescription = "保存正文", modifier = Modifier.size(18.dp))
            }
            if (modifiedCount > 0) {
                Text(
                    text = "累计修改${modifiedCount}个文件",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onCancel,
                enabled = enabled,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                ),
                modifier = Modifier.size(size)
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "退出正文编辑", modifier = Modifier.size(18.dp))
            }
        }
    } else {
        IconButton(
            onClick = onStart,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            ),
            modifier = Modifier.size(size)
        ) {
            Icon(Icons.Outlined.Edit, contentDescription = "编辑正文", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun ChapterTitleWithLineEndingHint(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
