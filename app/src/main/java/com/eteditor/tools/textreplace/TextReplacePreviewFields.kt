package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.eteditor.core.DocumentKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class ReplacementPreviewSection {
    Multi,
    Single,
    Zero
}

@Composable
fun TextSearchResultsPane(
    controller: EditorController,
    toolId: String,
    onDismiss: () -> Unit,
    onApplied: (() -> Unit)? = null,
    onApplyStarted: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dismissWithDeferredRefresh: () -> Unit = {
        controller.applyDeferredTxtTextReplacementRefresh()
        onDismiss()
    }
    val completeWithDeferredRefresh: () -> Unit = {
        controller.applyDeferredTxtTextReplacementRefresh()
        onApplied?.invoke() ?: onDismiss()
    }
    val replacementPreview = controller.replacementFilePreview
    if (replacementPreview?.toolId == toolId) {
        ReplacementFilePreviewPane(
            controller = controller,
            preview = replacementPreview,
            onDismiss = onDismiss,
            onApplied = onApplied,
            modifier = modifier
        )
        return
    }
    val results = controller.textSearchResults
    if (
        shouldAutoDismissTextSearchPreview(
            toolId = toolId,
            textSearchToolId = controller.textSearchToolId,
            resultCount = results.size,
            replacementPreviewToolId = replacementPreview?.toolId,
            hasAppliedCallback = onApplied != null
        )
    ) {
        LaunchedEffect(toolId, controller.textSearchToolId, results.size, replacementPreview?.toolId) {
            dismissWithDeferredRefresh()
        }
        return
    }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var executing by remember(toolId) { mutableStateOf(false) }
    var executionProgress by remember(toolId) { mutableStateOf(0f) }
    var executionLabel by remember(toolId) { mutableStateOf("执行替换") }
    var executionJob by remember(toolId) { mutableStateOf<Job?>(null) }
    var paneMessage by remember(toolId) { mutableStateOf<String?>(null) }
    var checkedResultIds by remember(toolId) { mutableStateOf<Set<String>>(emptySet()) }
    var knownResultIds by remember(toolId) { mutableStateOf<Set<String>>(emptySet()) }
    val currentResultIds = remember(results) { results.mapTo(linkedSetOf()) { it.id } }
    val automationStep = controller.automationConfirmationRequest
        ?.takeIf { it.stepId == toolId }
        ?.let(controller::automationConfirmationStep)
    val previewTitle = controller.textSearchPreviewTitle(toolId)
    LaunchedEffect(toolId, currentResultIds) {
        val existingChecked = checkedResultIds.intersect(currentResultIds)
        val newResultIds = currentResultIds - knownResultIds
        checkedResultIds = existingChecked + newResultIds
        knownResultIds = currentResultIds
    }
    fun updateExecutionProgress(completed: Int, total: Int) {
        executionProgress = if (total > 0) completed.toFloat() / total.toFloat() else 0f
        executionLabel = "执行替换 $completed/$total"
        automationStep?.let { step ->
            controller.setAutomationRunStepProgress(step, executionProgress, executionLabel)
        }
    }
    fun startExecutionAfterClosing(resultIds: Set<String>) {
        val total = resultIds.size.coerceAtLeast(1)
        executionJob?.cancel()
        executionProgress = 0f
        executionLabel = "执行替换 0/$total"
        automationStep?.let { step ->
            controller.setAutomationRunStepState(step, AutomationRunStepState.Running)
            controller.setAutomationRunStepProgress(step, 0f, executionLabel)
        }
        onApplyStarted?.invoke()
        executionJob = controller.controllerScope.launch {
            delay(16)
            yieldToAppUiBeforeHeavyWork()
            val applied = controller.applySelectedTextSearchResultsWithProgress(toolId, resultIds, ::updateExecutionProgress)
            if (applied) {
                if (controller.textSearchResults.isNotEmpty()) {
                    controller.clearTextSearchState()
                }
                completeWithDeferredRefresh()
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
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = previewTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${checkedResultIds.size}/${results.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = { checkedResultIds = currentResultIds },
                    enabled = !executing && checkedResultIds.size < results.size,
                    shape = ControlShape,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("全选")
                }
                TextButton(
                    onClick = { checkedResultIds = emptySet() },
                    enabled = !executing && checkedResultIds.isNotEmpty(),
                    shape = ControlShape,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("全不选")
                }
                IconButton(
                    onClick = dismissWithDeferredRefresh,
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(results, key = { it.id }) { result ->
                        TextSearchResultRow(
                            result = result,
                            selected = controller.selectedTextSearchResultId == result.id,
                            checked = result.id in checkedResultIds,
                            onCheckedChange = { checked ->
                                checkedResultIds = if (checked) {
                                    checkedResultIds + result.id
                                } else {
                                    checkedResultIds - result.id
                                }
                            },
                            onClick = { controller.selectTextSearchResult(result.id) },
                            onReplaceHere = {
                                val resultId = result.id
                                controller.selectTextSearchResult(resultId)
                                executing = true
                                executionProgress = 0f
                                executionLabel = "执行替换 0/1"
                                executionJob?.cancel()
                                executionJob = scope.launch {
                                    yieldToAppUiBeforeHeavyWork()
                                    val applied = controller.applySelectedTextSearchResult(toolId)
                                    executing = false
                                    if (applied && controller.textSearchResults.isEmpty()) {
                                        completeWithDeferredRefresh()
                                    } else if (!applied) {
                                        paneMessage = controller.statusMessage.ifBlank { "替换此处失败" }
                                    } else {
                                        executionProgress = 1f
                                        checkedResultIds = checkedResultIds - resultId
                                    }
                                }
                            },
                            enabled = !executing
                        )
                    }
                }
                ContentScrollbar(
                    state = listState,
                    itemCount = results.size,
                    prominent = false,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp)
                )
            }
            ButtonRow {
                Button(
                    enabled = checkedResultIds.isNotEmpty() && !executing,
                    onClick = {
                        executionJob?.cancel()
                        val resultIds = checkedResultIds
                        if (automationStep != null) {
                            startExecutionAfterClosing(resultIds)
                        } else {
                            executing = true
                            executionProgress = 0f
                            executionLabel = "执行替换 0/${resultIds.size}"
                            executionJob = scope.launch {
                                yieldToAppUiBeforeHeavyWork()
                                val applied = controller.applySelectedTextSearchResultsWithProgress(toolId, resultIds) { completed, total ->
                                    updateExecutionProgress(completed, total)
                                }
                                executing = false
                                if (applied) {
                                    val keepOpen = shouldKeepTextSearchPreviewOpenAfterSelectedApply(
                                        hasAutomationStep = false,
                                        hasAppliedCallback = onApplied != null,
                                        remainingResults = controller.textSearchResults.size
                                    )
                                    if (keepOpen) {
                                        executionProgress = 1f
                                        val remainingResultIds = controller.textSearchResults.mapTo(linkedSetOf()) { it.id }
                                        checkedResultIds = emptySet()
                                        knownResultIds = remainingResultIds
                                    } else {
                                        if (controller.textSearchResults.isNotEmpty()) {
                                            controller.clearTextSearchState()
                                        }
                                        completeWithDeferredRefresh()
                                    }
                                } else {
                                    paneMessage = controller.statusMessage.ifBlank { "执行替换失败" }
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = ControlShape,
                    contentPadding = CompactButtonPadding
                ) {
                    Icon(Icons.Outlined.FindReplace, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("执行替换")
                }
            }
        }
    }
    paneMessage?.let { message ->
        ToolRunMessageDialog(
            title = "文本替换",
            message = message,
            onDismiss = { paneMessage = null }
        )
    }
}

private fun EditorController.textSearchPreviewTitle(toolId: String): String {
    val scopeLabel = textSearchPreviewScopeLabel(toolId) ?: return "替换预览"
    return "替换预览·$scopeLabel"
}

private fun EditorController.textSearchPreviewScopeLabel(toolId: String): String? {
    val tool = textSearchPreviewTextReplaceTool(toolId) ?: return null
    val parameters = textReplaceParameters(tool)
    if (parameters.isReplacementMode()) return null
    return when (kind) {
        DocumentKind.Txt -> replacementPreviewScopeLabel(parameters.scope)
        DocumentKind.Epub -> {
            if (parameters.mode == TEXT_REPLACE_MODE_SINGLE) {
                replacementPreviewScopeLabel(parameters.scope)
            } else {
                null
            }
        }
        DocumentKind.None -> null
    }
}

private fun EditorController.textSearchPreviewTextReplaceTool(toolId: String): EditorTool? {
    return if (toolId == builtInToolPlanId("text_replace")) {
        builtInEditorTool("text_replace")
    } else {
        editorTools.firstOrNull { it.id == toolId } ?: automationStepToolForPreview(toolId)
    }?.takeIf { it.toolId == "text_replace" }
}

private fun replacementPreviewScopeLabel(scope: String): String? {
    return when (scope) {
        TOOL_SCOPE_CURRENT -> "本章"
        TOOL_SCOPE_ALL -> "全文"
        else -> null
    }
}

@Composable
private fun ReplacementFilePreviewPane(
    controller: EditorController,
    preview: ReplacementFilePreview,
    onDismiss: () -> Unit,
    onApplied: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val allSelectableMatches = remember(preview) {
        (preview.multiRules + preview.singleRules).flatMap { rule -> rule.matches.map { it.id } }.toSet()
    }
    var selectedMatchIds by remember(preview) { mutableStateOf(allSelectableMatches) }
    var expandedRuleIds by remember(preview) { mutableStateOf(preview.multiRules.map { it.id }.toSet()) }
    var dimmedRuleIds by remember(preview) { mutableStateOf(emptySet<String>()) }
    var paneMessage by remember(preview) { mutableStateOf<String?>(null) }
    var executing by remember(preview) { mutableStateOf(false) }
    var executionProgress by remember(preview) { mutableStateOf(0f) }
    var executionLabel by remember(preview) { mutableStateOf("执行替换") }
    val listState = rememberLazyListState()
    val automationStep = controller.automationConfirmationRequest
        ?.takeIf { it.stepId == preview.toolId }
        ?.let(controller::automationConfirmationStep)
    val dismissWithDeferredRefresh: () -> Unit = {
        controller.applyDeferredTxtTextReplacementRefresh()
        onDismiss()
    }
    val completeWithDeferredRefresh: () -> Unit = {
        controller.applyDeferredTxtTextReplacementRefresh()
        onApplied?.invoke() ?: onDismiss()
    }
    fun updateExecutionProgress(completed: Int, total: Int) {
        executionProgress = if (total > 0) completed.toFloat() / total.toFloat() else 0f
        executionLabel = "执行替换 $completed/$total"
        automationStep?.let { step ->
            controller.setAutomationRunStepProgress(step, executionProgress, executionLabel)
        }
    }
    var selectedSection by remember(preview) {
        mutableStateOf(
            when {
                preview.multiRules.isNotEmpty() -> ReplacementPreviewSection.Multi
                preview.singleRules.isNotEmpty() -> ReplacementPreviewSection.Single
                else -> ReplacementPreviewSection.Zero
            }
        )
    }
    val previewListItemCount = when (selectedSection) {
        ReplacementPreviewSection.Multi -> preview.multiRules.size + 1
        ReplacementPreviewSection.Single -> preview.singleRules.size + 1
        ReplacementPreviewSection.Zero -> preview.zeroRules.size + 1
    }
    var invalidRulesMessage by remember(preview) { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedSection) {
        listState.scrollToItem(0)
    }

    Surface(
        shape = PreviewShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxSize()
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
                val hasInvalidRules = preview.skippedRules.isNotEmpty()
                val previewCountLabel = if (hasInvalidRules) {
                    "${preview.validRules}/${preview.totalRules}"
                } else {
                    preview.totalRules.toString()
                }
                Text(
                    text = ".replacement 预览（$previewCountLabel）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hasInvalidRules) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (hasInvalidRules) {
                                Modifier.clickable(enabled = !executing) {
                                    invalidRulesMessage = replacementInvalidRulesMessage(preview.skippedRules)
                                }
                            } else {
                                Modifier
                            }
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (automationStep != null) {
                    Button(
                        onClick = {
                            if (onApplied != null) controller.clearReplacementFilePreview(preview.toolId)
                            completeWithDeferredRefresh()
                        },
                        enabled = !executing,
                        shape = ControlShape,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("完成", style = MaterialTheme.typography.labelMedium)
                    }
                }
                IconButton(
                    onClick = dismissWithDeferredRefresh,
                    enabled = !executing,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(19.dp))
                }
            }
            ReplacementPreviewStats(
                preview = preview,
                selectedSection = selectedSection,
                onSelectSection = { section -> if (!executing) selectedSection = section }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (executing) {
                ToolRunProgress(
                    toolName = executionLabel,
                    progress = executionProgress
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (selectedSection) {
                        ReplacementPreviewSection.Multi -> {
                            item("multi-header") {
                                ReplacementSectionHeader("多处匹配", preview.multiRules.size)
                            }
                            items(preview.multiRules, key = { it.id }) { rule ->
                                ReplacementMultiRuleRow(
                                    controller = controller,
                                    rule = rule,
                                    expanded = rule.id in expandedRuleIds,
                                    selectedMatchIds = selectedMatchIds,
                                    onToggleExpanded = {
                                        expandedRuleIds = if (rule.id in expandedRuleIds) {
                                            expandedRuleIds - rule.id
                                        } else {
                                            expandedRuleIds + rule.id
                                        }
                                    },
                                    onToggleMatch = { matchId, checked ->
                                        selectedMatchIds = if (checked) selectedMatchIds + matchId else selectedMatchIds - matchId
                                    },
                                    onSelectAll = {
                                        selectedMatchIds = selectedMatchIds + rule.matches.map { it.id }
                                    },
                                    onClearAll = {
                                        selectedMatchIds = selectedMatchIds - rule.matches.map { it.id }.toSet()
                                    },
                                    onApplySelected = {
                                        val matchIds = rule.matches
                                            .map { it.id }
                                            .filter { it in selectedMatchIds }
                                            .toSet()
                                        if (matchIds.isNotEmpty()) {
                                            // 仅标记为「待应用」：收缩 + 变淡，保留在列表，不立即写入；
                                            // 实际替换交给底部「执行替换」统一执行
                                            expandedRuleIds = expandedRuleIds - rule.id
                                            dimmedRuleIds = dimmedRuleIds + rule.id
                                        }
                                    },
                                    dimmed = rule.id in dimmedRuleIds,
                                    enabled = !executing
                                )
                            }
                        }
                        ReplacementPreviewSection.Single -> {
                            item("single-header") {
                                ReplacementSectionHeader("单处匹配", preview.singleRules.size)
                            }
                            items(preview.singleRules, key = { it.id }) { rule ->
                                ReplacementSingleRuleRow(
                                    controller = controller,
                                    rule = rule,
                                    selectedMatchIds = selectedMatchIds,
                                    onToggleMatch = { matchId, checked ->
                                        selectedMatchIds = if (checked) selectedMatchIds + matchId else selectedMatchIds - matchId
                                    }
                                )
                            }
                        }
                        ReplacementPreviewSection.Zero -> {
                            item("zero-header") {
                                ReplacementSectionHeader("无匹配", preview.zeroRules.size)
                            }
                            items(preview.zeroRules, key = { it.id }) { rule ->
                                ReplacementZeroRuleRow(rule)
                            }
                        }
                    }
                    if (previewListItemCount <= 1) {
                        item("empty-section") {
                            Text(
                                text = "当前分类没有规则",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                ContentScrollbar(
                    state = listState,
                    itemCount = previewListItemCount,
                    prominent = false,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp)
                )
            }
            ButtonRow {
                Button(
                    enabled = selectedMatchIds.isNotEmpty() && !executing,
                    onClick = {
                        val matchIds = selectedMatchIds
                        executing = true
                        executionProgress = 0f
                        executionLabel = "执行替换 0/${matchIds.size}"
                        scope.launch {
                            yieldToAppUiBeforeHeavyWork()
                            // 只应用替换并停留在面板：控制器会用替换后的文本重建预览刷新本面板，
                            // 用户可继续逐条勾选替换或「重新搜索」；automation 由标题栏「完成」推进链。
                            val applied = controller.applySelectedReplacementPreviewWithProgress(matchIds) { completed, total ->
                                updateExecutionProgress(completed, total)
                            }
                            executing = false
                            if (!applied) {
                                paneMessage = controller.statusMessage.ifBlank { "执行替换失败" }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = ControlShape,
                    contentPadding = CompactButtonPadding
                ) {
                    Text("执行替换")
                }
            }
        }
    }
    paneMessage?.let { message ->
        ToolRunMessageDialog(
            title = "文本替换",
            message = message,
            onDismiss = { paneMessage = null }
        )
    }
    invalidRulesMessage?.let { message ->
        ToolRunMessageDialog(
            title = "无效规则",
            message = message,
            onDismiss = { invalidRulesMessage = null }
        )
    }
}

internal fun shouldKeepTextSearchPreviewOpenAfterSelectedApply(
    hasAutomationStep: Boolean,
    hasAppliedCallback: Boolean,
    remainingResults: Int
): Boolean {
    return !hasAutomationStep && !hasAppliedCallback && remainingResults > 0
}

internal fun shouldAutoDismissTextSearchPreview(
    toolId: String,
    textSearchToolId: String?,
    resultCount: Int,
    replacementPreviewToolId: String?,
    hasAppliedCallback: Boolean
): Boolean {
    if (replacementPreviewToolId == toolId) return false
    if (hasAppliedCallback) return false
    return textSearchToolId != toolId || resultCount <= 0
}

internal fun displayLineBreakEscapes(text: String): String {
    return text
        .replace("\r\n", "\\r\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}

private fun displaySearchMatchText(text: String): String {
    val visible = displayLineBreakEscapes(text)
        .replace("\t", "\\t")
    return visible.ifBlank { "空白" }
}

internal data class SearchContextDisplay(
    val text: String,
    val highlightStart: Int,
    val highlightEnd: Int
)

private const val SEARCH_RESULT_VISIBLE_BEFORE_CHARS = 14
private const val SEARCH_RESULT_VISIBLE_AFTER_CHARS = 28

internal fun displaySearchContext(
    text: String,
    highlightStart: Int,
    highlightEnd: Int
): SearchContextDisplay {
    if (text.isEmpty()) return SearchContextDisplay("", -1, -1)
    val start = highlightStart.coerceIn(0, text.length)
    val end = highlightEnd.coerceIn(start, text.length)
    val visible = StringBuilder(text.length)
    var visibleStart = -1
    var visibleEnd = -1
    text.forEachIndexed { index, char ->
        if (index == start) visibleStart = visible.length
        visible.append(
            when (char) {
                '\r' -> "\\r"
                '\n' -> "\\n"
                '\t' -> "\\t"
                else -> char.toString()
            }
        )
        if (index + 1 == end) visibleEnd = visible.length
    }
    if (start == text.length) visibleStart = visible.length
    if (end == text.length && visibleEnd < 0) visibleEnd = visible.length
    if (visibleStart >= 0 && visibleEnd > visibleStart) {
        val clipStart = (visibleStart - SEARCH_RESULT_VISIBLE_BEFORE_CHARS).coerceAtLeast(0)
        val clipEnd = (visibleEnd + SEARCH_RESULT_VISIBLE_AFTER_CHARS).coerceAtMost(visible.length)
        val prefix = if (clipStart > 0) "..." else ""
        val suffix = if (clipEnd < visible.length) "..." else ""
        return SearchContextDisplay(
            text = prefix + visible.substring(clipStart, clipEnd) + suffix,
            highlightStart = prefix.length + visibleStart - clipStart,
            highlightEnd = prefix.length + visibleEnd - clipStart
        )
    }
    return SearchContextDisplay(
        text = visible.toString(),
        highlightStart = visibleStart,
        highlightEnd = visibleEnd
    )
}

@Composable
private fun TextSearchResultRow(
    result: TextSearchResult,
    selected: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onReplaceHere: () -> Unit,
    enabled: Boolean
) {
    val context = remember(result.context, result.contextMatchStart, result.contextMatchEnd) {
        displaySearchContext(
            text = result.context,
            highlightStart = result.contextMatchStart,
            highlightEnd = result.contextMatchEnd
        )
    }
    Surface(
        onClick = onClick,
        shape = RowShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        },
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.82f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 7.dp, top = 6.dp, end = 5.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                modifier = Modifier.size(30.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = highlightedText(
                        text = context.text,
                        highlightStart = context.highlightStart,
                        highlightEnd = context.highlightEnd
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = result.chapterTitle.ifBlank { "第 ${result.chapterIndex + 1} 章" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onReplaceHere,
                enabled = enabled,
                shape = ControlShape,
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                modifier = Modifier
                    .width(64.dp)
                    .height(30.dp)
            ) {
                Text("替换", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun highlightedText(
    text: String,
    highlightStart: Int,
    highlightEnd: Int
): AnnotatedString {
    if (text.isEmpty() || highlightStart < 0 || highlightEnd <= highlightStart || highlightStart >= text.length) {
        return AnnotatedString(text)
    }
    val start = highlightStart.coerceIn(0, text.length)
    val end = highlightEnd.coerceIn(start, text.length)
    if (end <= start) return AnnotatedString(text)

    val highlightBackground = androidx.compose.ui.graphics.Color(0xFFFFD84D)
    val highlightContent = androidx.compose.ui.graphics.Color(0xFF111111)
    return buildAnnotatedString {
        append(text.substring(0, start))
        withStyle(
            SpanStyle(
                background = highlightBackground,
                color = highlightContent,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append(text.substring(start, end))
        }
        append(text.substring(end))
    }
}
