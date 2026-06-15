package com.eteditor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.eteditor.core.ChapterInfo

internal enum class TxtChapterIssueType(
    val title: String,
    val contentDescription: String
) {
    Length("长短章", "长短章提示"),
    Other("其余问题", "其余问题提示")
}

private val TxtLengthIssueStatuses = setOf("短章", "超长章")

internal data class TxtChapterIssueSummary(
    val lengthChapters: List<ChapterInfo>,
    val otherChapters: List<ChapterInfo>
) {
    fun chaptersFor(type: TxtChapterIssueType): List<ChapterInfo> = when (type) {
        TxtChapterIssueType.Length -> lengthChapters
        TxtChapterIssueType.Other -> otherChapters
    }
}

internal fun txtChapterIssueSummary(chapters: List<ChapterInfo>): TxtChapterIssueSummary {
    return TxtChapterIssueSummary(
        lengthChapters = chapters.filter { chapter ->
            chapter.status.any { it in TxtLengthIssueStatuses }
        },
        otherChapters = chapters.filter { chapter ->
            chapter.status.any { it !in TxtLengthIssueStatuses }
        }
    )
}

@Composable
internal fun TxtChapterIssueButton(
    type: TxtChapterIssueType,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val containerColor by animateColorAsState(
        targetValue = when {
            pressed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.78f)
            hovered -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.48f)
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        },
        animationSpec = tween(durationMillis = 120),
        label = "txtChapterIssueColor"
    )
    Surface(
        onClick = onClick,
        shape = RowShape,
        color = containerColor,
        interactionSource = interactionSource,
        modifier = modifier.height(28.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = type.contentDescription,
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun TxtChapterIssueRow(
    chapter: ChapterInfo,
    type: TxtChapterIssueType,
    onClick: () -> Unit
) {
    val statusLabel = txtChapterIssueStatusLabel(chapter, type)
    Surface(
        onClick = onClick,
        shape = RowShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = chapter.index.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = chapter.title.ifBlank { "未命名章节" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(statusLabel)
                        if (chapter.wordCount > 0) append("，${compactCountLabel(chapter.wordCount)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun txtChapterIssueStatusLabel(
    chapter: ChapterInfo,
    type: TxtChapterIssueType
): String {
    val statuses = when (type) {
        TxtChapterIssueType.Length -> chapter.status.filter { it in TxtLengthIssueStatuses }
        TxtChapterIssueType.Other -> chapter.status.filter { it !in TxtLengthIssueStatuses }
    }.distinct()
    return statuses.joinToString("、").ifBlank { type.title }
}

@Composable
internal fun TxtChapterIssueDialog(
    summary: TxtChapterIssueSummary,
    type: TxtChapterIssueType,
    onPickChapter: (ChapterInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val chapters = summary.chaptersFor(type)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            DialogTitleWithClose(
                title = type.title,
                onDismiss = onDismiss,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(chapters, key = { chapter -> chapter.index }) { chapter ->
                    TxtChapterIssueRow(
                        chapter = chapter,
                        type = type,
                        onClick = { onPickChapter(chapter) }
                    )
                }
            }
        },
        confirmButton = {},
        shape = PreviewShape,
        containerColor = MaterialTheme.colorScheme.surface
    )
}
