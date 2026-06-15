package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun AutomationLogView(
    controller: EditorController,
    modifier: Modifier = Modifier,
    fillAvailableHeight: Boolean = false
) {
    val logState = rememberLazyListState()
    LaunchedEffect(controller.automationLog.size, controller.automationLog.lastOrNull()) {
        if (controller.automationLog.isNotEmpty()) {
            logState.animateScrollToItem(controller.automationLog.lastIndex)
        }
    }
    val logPanelHeight = when {
        controller.automationLog.size <= 3 -> 104.dp
        controller.automationLog.size <= 6 -> 142.dp
        else -> 220.dp
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "执行日志",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillAvailableHeight) Modifier.weight(1f) else Modifier.height(logPanelHeight)),
            shape = RowShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        state = logState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 12.dp),
                        contentPadding = PaddingValues(bottom = 4.dp)
                    ) {
                        if (controller.automationLog.isEmpty()) {
                            item {
                                Text(
                                    text = "暂无日志",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                )
                            }
                        } else {
                            items(controller.automationLog) { line ->
                                val isDividerTitle = line.startsWith("━━") ||
                                    line == "开始执行" ||
                                    line.startsWith("执行链完成") ||
                                    line.startsWith("自动化完成")
                                if (isDividerTitle) {
                                    val label = line.trim('━', ' ')
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    AutomationLogTimelineRow(
                                        text = line,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    ContentScrollbar(
                        state = logState,
                        itemCount = controller.automationLog.size.takeIf { it > 0 } ?: 1,
                        prominent = false,
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
    }
}

@Composable
private fun AutomationLogTimelineRow(
    text: String,
    modifier: Modifier = Modifier
) {
    val color = when {
        text.startsWith("失败") || text.startsWith("停止") || text.startsWith("上传失败") ||
            text.startsWith("上传文件失败") ->
            MaterialTheme.colorScheme.error
        text.startsWith("完成") || text.startsWith("已上传") ->
            MaterialTheme.colorScheme.primary
        text.startsWith("等待") || text.startsWith("执行") ->
            MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color.copy(alpha = 0.82f), RoundedCornerShape(999.dp))
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
