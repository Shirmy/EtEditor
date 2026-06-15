package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun AutomationRunSequence(
    controller: EditorController,
    selectedChain: AutomationChain,
    uploadAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val stepStatuses = selectedChain.steps.map { step ->
        controller.automationRunStepStatus(step)
    }
    val doneCount = stepStatuses.count { it.state.isAutomationTerminalState() }
    val progress = if (selectedChain.steps.isEmpty()) 0f else doneCount.toFloat() / selectedChain.steps.size
    val progressLabel = if (selectedChain.steps.isNotEmpty() && doneCount == selectedChain.steps.size) {
        "已完成"
    } else {
        "$doneCount / ${selectedChain.steps.size}"
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "执行顺序",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = progressLabel,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RowShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        ) {
            if (selectedChain.steps.isEmpty()) {
                Text(
                    text = "暂无执行步骤",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            } else if (selectedChain.steps.size < 4) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectedChain.steps.forEachIndexed { index, step ->
                        AutomationRunStepRow(
                            index = index + 1,
                            title = controller.automationStepLabel(step),
                            status = stepStatuses[index],
                            onStatusClick = if (stepStatuses[index].state == AutomationRunStepState.NeedsUpload) {
                                uploadAction
                            } else {
                                null
                            }
                        )
                    }
                }
            } else if (selectedChain.steps.size <= 6) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectedChain.steps.chunked(2).forEachIndexed { rowIndex, rowSteps ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowSteps.forEachIndexed { columnIndex, step ->
                                val index = rowIndex * 2 + columnIndex
                                AutomationRunStepRow(
                                    index = index + 1,
                                    title = controller.automationStepLabel(step),
                                    status = stepStatuses[index],
                                    modifier = Modifier.weight(1f),
                                    onStatusClick = if (stepStatuses[index].state == AutomationRunStepState.NeedsUpload) {
                                        uploadAction
                                    } else {
                                        null
                                    }
                                )
                            }
                            if (rowSteps.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                val gridHeight = (3 * 58 + 2 * 6 + 6).dp
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    userScrollEnabled = selectedChain.steps.size > 6
                ) {
                    items(selectedChain.steps.size) { index ->
                        val step = selectedChain.steps[index]
                        AutomationRunStepRow(
                            index = index + 1,
                            title = controller.automationStepLabel(step),
                            status = stepStatuses[index],
                            onStatusClick = if (stepStatuses[index].state == AutomationRunStepState.NeedsUpload) {
                                uploadAction
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationRunStepRow(
    index: Int,
    title: String,
    status: AutomationRunStepStatus,
    modifier: Modifier = Modifier,
    onStatusClick: (() -> Unit)? = null
) {
    val activeProgress = status.progress
        ?.coerceIn(0f, 1f)
        ?.takeIf { status.state.showsAutomationStepProgress() }
    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = index.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AutomationStepStatusPill(status.state, onClick = onStatusClick)
            }
            activeProgress?.let { progress ->
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                    )
                    Text(
                        text = status.progressText.ifBlank { "${(progress * 100).roundToInt()}%" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 112.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomationStepStatusPill(
    status: AutomationRunStepState,
    onClick: (() -> Unit)? = null
) {
    val background = when (status) {
        AutomationRunStepState.NeedsUpload -> Color.Black
        AutomationRunStepState.Completed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        AutomationRunStepState.Running -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        AutomationRunStepState.UploadedPendingExecution,
        AutomationRunStepState.NeedsConfirmation,
        AutomationRunStepState.Confirmed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        AutomationRunStepState.Failed -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        AutomationRunStepState.Skipped -> MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
        AutomationRunStepState.Waiting -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
    }
    val foreground = when (status) {
        AutomationRunStepState.NeedsUpload -> Color.White
        AutomationRunStepState.Completed,
        AutomationRunStepState.Running,
        AutomationRunStepState.UploadedPendingExecution,
        AutomationRunStepState.NeedsConfirmation,
        AutomationRunStepState.Confirmed -> MaterialTheme.colorScheme.primary
        AutomationRunStepState.Failed,
        AutomationRunStepState.Skipped -> MaterialTheme.colorScheme.error
        AutomationRunStepState.Waiting -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(999.dp),
        color = background
    ) {
        Text(
            text = automationRunStepStateLabel(status),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = foreground,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun AutomationRunStepState.isAutomationTerminalState(): Boolean {
    return this == AutomationRunStepState.Completed ||
        this == AutomationRunStepState.Skipped ||
        this == AutomationRunStepState.Failed
}

private fun AutomationRunStepState.showsAutomationStepProgress(): Boolean {
    return this == AutomationRunStepState.Running ||
        this == AutomationRunStepState.NeedsConfirmation ||
        this == AutomationRunStepState.Confirmed
}

private fun automationRunStepStateLabel(state: AutomationRunStepState): String {
    return when (state) {
        AutomationRunStepState.Waiting -> "\u7b49\u5f85\u6267\u884c"
        AutomationRunStepState.Running -> "\u6267\u884c\u4e2d"
        AutomationRunStepState.NeedsUpload -> "\u9700\u8981\u4e0a\u4f20\u6587\u4ef6"
        AutomationRunStepState.UploadedPendingExecution -> "已上传待执行"
        AutomationRunStepState.NeedsConfirmation -> "待预览"
        AutomationRunStepState.Confirmed -> "\u7528\u6237\u786e\u8ba4"
        AutomationRunStepState.Completed -> "\u5df2\u5b8c\u6210"
        AutomationRunStepState.Skipped -> "\u5df2\u8df3\u8fc7"
        AutomationRunStepState.Failed -> "\u5931\u8d25"
    }
}
