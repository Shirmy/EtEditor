package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eteditor.core.ChapterInfo
import com.eteditor.core.DocumentKind

private val EpubVolumeFileNameRegex = Regex("""(?i)^Vol(?:\d+|F\d+)\.(?:xhtml|html|htm)${'$'}""")

private fun isEpubVolumeFileName(fileName: String): Boolean {
    return EpubVolumeFileNameRegex.matches(fileName)
}

internal fun directoryChapterStableKey(kind: DocumentKind, chapter: ChapterInfo): String {
    return when (kind) {
        DocumentKind.Epub -> "epub:${chapter.source.ifBlank { chapter.fileName }}"
        DocumentKind.Txt -> if (chapter.index <= 0) {
            "txt:preface"
        } else {
            "txt:${chapter.lineNumber}:${chapter.source}"
        }
        DocumentKind.None -> "none:${chapter.index}:${chapter.source}"
    }
}

internal enum class DirectoryRowBoxPosition {
    Standalone,
    LevelSingle,
    LevelStart,
    LevelMiddle,
    LevelEnd
}

internal fun directoryRowBoxPosition(
    chapter: ChapterInfo,
    previous: ChapterInfo?,
    next: ChapterInfo?
): DirectoryRowBoxPosition {
    if (chapter.tocLevel <= 0) return DirectoryRowBoxPosition.Standalone
    val previousSameLevel = previous != null && previous.tocLevel == chapter.tocLevel
    val nextSameLevel = next != null && next.tocLevel == chapter.tocLevel
    return when {
        !previousSameLevel && !nextSameLevel -> DirectoryRowBoxPosition.LevelSingle
        !previousSameLevel -> DirectoryRowBoxPosition.LevelStart
        !nextSameLevel -> DirectoryRowBoxPosition.LevelEnd
        else -> DirectoryRowBoxPosition.LevelMiddle
    }
}

internal fun epubDirectoryRowHeight(showFileName: Boolean): Dp {
    return if (showFileName) 56.dp else 42.dp
}

private fun directoryDisplayTitle(title: String): String {
    val fallback = "(无标题"
    return title
        .ifBlank { fallback }
        .replace(Regex("""(?i)<br\s*/?>"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifBlank { fallback }
}

@Composable
internal fun DirectoryRow(
    controller: EditorController,
    index: Int,
    fileName: String,
    title: String,
    sequenceLabel: String?,
    lineNumber: Int,
    wordCount: Int,
    status: List<String>,
    selected: Boolean,
    showFileName: Boolean,
    hiddenFromToc: Boolean,
    tocLevel: Int,
    isVolume: Boolean,
    rowBoxPosition: DirectoryRowBoxPosition = DirectoryRowBoxPosition.Standalone,
    bulkSelectMode: Boolean = false,
    bulkSelected: Boolean = false,
    hasChildren: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    moveMode: Boolean,
    moveTargetOptions: List<DirectoryPickerOption>,
    selectedMoveTargetIndex: Int?,
    onMoveTargetSelected: (Int) -> Unit,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val browsingOnly = !moveMode
    val isVolumeRow = isVolume || isEpubVolumeFileName(fileName)
    val isLevelBoxRow = rowBoxPosition != DirectoryRowBoxPosition.Standalone
    val levelIndent = if (isLevelBoxRow) 0.dp else (tocLevel.coerceIn(0, 6) * 10).dp
    val displayFileName = remember(fileName) { fileName.ifBlank { "文件名" } }
    val displayTitle = remember(title) { directoryDisplayTitle(title) }
    val wordCountLabel = remember(wordCount) { compactCountLabel(wordCount) }
    val statusLabel = remember(status) { status.joinToString("、") }
    val fixedRowHeight = if (controller.kind == DocumentKind.Epub) epubDirectoryRowHeight(showFileName) else null
    val rowShape = when (rowBoxPosition) {
        DirectoryRowBoxPosition.Standalone -> RowShape
        DirectoryRowBoxPosition.LevelSingle -> RowShape
        DirectoryRowBoxPosition.LevelStart -> RoundedCornerShape(
            topStart = 8.dp,
            topEnd = 8.dp,
            bottomEnd = 0.dp,
            bottomStart = 0.dp
        )
        DirectoryRowBoxPosition.LevelMiddle -> RoundedCornerShape(0.dp)
        DirectoryRowBoxPosition.LevelEnd -> RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomEnd = 8.dp,
            bottomStart = 8.dp
        )
    }
    val outerPadding = when (rowBoxPosition) {
        DirectoryRowBoxPosition.Standalone -> PaddingValues(horizontal = 2.dp, vertical = 1.dp)
        DirectoryRowBoxPosition.LevelSingle -> PaddingValues(
            start = 8.dp + (tocLevel.coerceIn(1, 6) * 6).dp,
            top = 2.dp,
            end = 2.dp,
            bottom = 2.dp
        )
        DirectoryRowBoxPosition.LevelStart -> PaddingValues(
            start = 8.dp + (tocLevel.coerceIn(1, 6) * 6).dp,
            top = 2.dp,
            end = 2.dp,
            bottom = 0.dp
        )
        DirectoryRowBoxPosition.LevelMiddle -> PaddingValues(
            start = 8.dp + (tocLevel.coerceIn(1, 6) * 6).dp,
            top = 0.dp,
            end = 2.dp,
            bottom = 0.dp
        )
        DirectoryRowBoxPosition.LevelEnd -> PaddingValues(
            start = 8.dp + (tocLevel.coerceIn(1, 6) * 6).dp,
            top = 0.dp,
            end = 2.dp,
            bottom = 2.dp
        )
    }
    val rowColor = when {
        bulkSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        selected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        isVolumeRow -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)
        isLevelBoxRow -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surface
    }
    val railColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0f)

    Surface(
        shape = rowShape,
        color = rowColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = when {
            bulkSelected -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.58f))
            selected -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
            isVolumeRow -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f))
            isLevelBoxRow -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
            else -> null
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(outerPadding)
            .then(fixedRowHeight?.let { Modifier.height(it) } ?: Modifier)
            .then(
                if (browsingOnly) {
                    val longPress = onLongPress
                    if (longPress == null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier.pointerInput(onClick, longPress) {
                            detectTapGestures(
                                onLongPress = { longPress() },
                                onTap = { onClick() }
                            )
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .then(if (fixedRowHeight != null) Modifier.fillMaxHeight() else Modifier)
                .padding(
                    start = if (controller.kind == DocumentKind.Txt) 0.dp else 2.dp,
                    top = 6.dp,
                    end = if (controller.kind == DocumentKind.Txt) 8.dp else 3.dp,
                    bottom = 6.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (bulkSelectMode) {
                Box(
                    modifier = Modifier.width(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (bulkSelected) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = "已选择",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .border(
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                                    RoundedCornerShape(999.dp)
                                )
                        )
                    }
                }
            }
            if (sequenceLabel != null) {
                Text(
                    text = sequenceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.width(24.dp)
                )
            }
            Spacer(Modifier.width(levelIndent))
            Box(
                modifier = Modifier.size(if (hasChildren) 22.dp else 2.dp),
                contentAlignment = Alignment.Center
            ) {
                if (hasChildren) {
                    IconButton(
                        onClick = onToggleExpanded,
                        modifier = Modifier.size(22.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = if (expanded) "收起" else "展开",
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(30.dp)
                    .background(railColor, RoundedCornerShape(999.dp))
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (showFileName) {
                    Text(
                        text = displayFileName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    verticalAlignment = if (controller.kind == DocumentKind.Txt) Alignment.Bottom else Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (sequenceLabel != null && controller.kind != DocumentKind.Txt) {
                        Text(
                            text = sequenceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.widthIn(min = 22.dp)
                        )
                    }
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isVolumeRow) FontWeight.Bold else null,
                            color = if (isVolumeRow) {
                                androidx.compose.ui.graphics.Color.Black
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        ),
                        maxLines = if (controller.kind == DocumentKind.Epub) 1 else Int.MAX_VALUE,
                        overflow = if (controller.kind == DocumentKind.Epub) TextOverflow.Ellipsis else TextOverflow.Clip,
                        modifier = Modifier.weight(1f)
                    )
                    if (hiddenFromToc) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Text(
                                text = "已从目录隐藏",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (controller.kind == DocumentKind.Txt && !moveMode) {
                        Text(
                            text = wordCountLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (status.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier
                                .widthIn(min = 42.dp)
                                .padding(end = 4.dp)
                        )
                    }
                }
                if (controller.kind == DocumentKind.Txt && status.isNotEmpty()) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (moveMode) {
                DirectoryMoveTargetMenu(
                    currentIndex = index - 1,
                    selectedTargetIndex = selectedMoveTargetIndex,
                    options = moveTargetOptions,
                    onSelect = onMoveTargetSelected,
                    label = if (controller.kind == DocumentKind.Txt) "移动" else "移动至",
                    title = if (controller.kind == DocumentKind.Txt) "移动到章节后" else "移动至",
                    modifier = if (controller.kind == DocumentKind.Txt) Modifier.width(54.dp) else Modifier
                )
            }
        }
    }
}
