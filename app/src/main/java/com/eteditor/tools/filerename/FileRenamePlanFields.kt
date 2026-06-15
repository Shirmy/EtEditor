package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FileRenamePlanPane(
    controller: EditorController,
    toolId: String,
    onDismiss: () -> Unit,
    onApplied: (() -> Unit)? = null,
    onApplyStarted: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val plan = controller.fileRenamePlan
    if (controller.fileRenamePlanToolId != toolId) return
    if (plan.isEmpty()) return
    val scope = rememberCoroutineScope()
    var executing by remember(toolId) { mutableStateOf(false) }
    var executionProgress by remember(toolId) { mutableStateOf(0f) }
    var executionLabel by remember(toolId) { mutableStateOf("执行重命名") }
    var executionJob by remember(toolId) { mutableStateOf<Job?>(null) }
    val automationStep = controller.automationConfirmationRequest
        ?.takeIf { it.stepId == toolId }
        ?.let(controller::automationConfirmationStep)
    fun updateExecutionProgress(completed: Int, total: Int) {
        executionProgress = if (total > 0) completed.toFloat() / total.toFloat() else 0f
        executionLabel = "执行重命名 $completed/$total"
        automationStep?.let { step ->
            controller.setAutomationRunStepProgress(step, executionProgress, executionLabel)
        }
    }
    fun startExecutionAfterClosing() {
        val total = plan.size.coerceAtLeast(1)
        executionJob?.cancel()
        executionProgress = 0f
        executionLabel = "执行重命名 0/$total"
        automationStep?.let { step ->
            controller.setAutomationRunStepState(step, AutomationRunStepState.Running)
            controller.setAutomationRunStepProgress(step, 0f, executionLabel)
        }
        onApplyStarted?.invoke()
        executionJob = controller.controllerScope.launch {
            delay(16)
            yieldToAppUiBeforeHeavyWork()
            val applied = controller.applyPreparedFileRenamePlanWithProgress(toolId, ::updateExecutionProgress)
            if (applied) {
                onApplied?.invoke() ?: onDismiss()
            } else {
                automationStep?.let { step -> controller.failAutomationConfirmationStep(step) }
            }
        }
    }
    Surface(
        shape = PreviewShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "命名预览",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${plan.count { it.changed }}/${plan.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = onDismiss,
                    enabled = !executing,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(19.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (executing) {
                ToolRunProgress(
                    toolName = executionLabel,
                    progress = executionProgress
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(plan, key = { "${it.chapterIndex}-${it.oldPath}-${it.newPath}" }) { item ->
                    FileRenamePlanRow(item)
                }
            }
            ButtonRow {
                Button(
                    enabled = plan.any { it.changed } && !executing,
                    onClick = {
                        executionJob?.cancel()
                        if (onApplyStarted != null || automationStep != null) {
                            startExecutionAfterClosing()
                        } else {
                            executing = true
                            executionProgress = 0f
                            executionLabel = "执行重命名 0/${plan.size}"
                            executionJob = scope.launch {
                                yieldToAppUiBeforeHeavyWork()
                                val applied = controller.applyPreparedFileRenamePlanWithProgress(toolId) { completed, total ->
                                    updateExecutionProgress(completed, total)
                                }
                                executing = false
                                if (applied) {
                                    onApplied?.invoke() ?: onDismiss()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ControlShape,
                    contentPadding = CompactButtonPadding
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("执行重命名")
                }
            }
        }
    }
}

@Composable
private fun FileRenamePlanRow(item: FileRenamePlanItem) {
    Surface(
        shape = RowShape,
        color = if (item.changed) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = item.spineIndex.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.oldFileName,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.newFileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = if (item.changed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (item.changed) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
