package com.eteditor

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
@Composable
fun FetchInfoPreviewDialog(
    controller: EditorController,
    toolId: String,
    onDismiss: () -> Unit,
    onApplied: (() -> Unit)? = null,
    onApplyStarted: (() -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val dialogHeight = (configuration.screenHeightDp.dp * 0.86f).coerceAtLeast(320.dp)
    val preview = controller.fetchInfoPreview?.takeIf { it.toolId == toolId }
    val dialogWidthModifier = if (preview?.parameters?.fetchCover == true) {
        Modifier.fixedDialogWidth(fraction = 0.86f, maxWidth = 420.dp)
    } else {
        Modifier.adaptiveDialogWidth(AdaptiveDialogWidth.Preview)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = dialogWidthModifier
                .height(dialogHeight)
                .dialogBorder()
        ) {
            FetchInfoPreviewPane(
                controller = controller,
                toolId = toolId,
                onDismiss = onDismiss,
                onApplied = onApplied,
                onApplyStarted = onApplyStarted,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun FetchInfoPreviewPane(
    controller: EditorController,
    toolId: String,
    onDismiss: () -> Unit,
    onApplied: (() -> Unit)? = null,
    onApplyStarted: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val preview = controller.fetchInfoPreview
    if (preview == null || preview.toolId != toolId) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有可用的抓取预设", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val scope = rememberCoroutineScope()
    var writing by remember(preview) { mutableStateOf(false) }
    var writingProgress by remember(preview) { mutableStateOf(0f) }
    var writingLabel by remember(preview) { mutableStateOf("应用抓取信息") }
    var catalogOrderReversed by remember(preview) { mutableStateOf(false) }
    // 过滤恒为开：总开关 UI 已移除，是否过滤改由每条规则自己的开关控制（全关时 filtered 即等于 raw）。
    // 不再读旧的 catalogFilterEnabled，避免历史默认值存了"关闭"后界面无法再开启。
    val filterActive = true
    var showRulePanel by remember(toolId) { mutableStateOf(false) }
    var showIntroRulePanel by remember(toolId) { mutableStateOf(false) }
    var showSkippedDialog by remember(preview) { mutableStateOf(false) }
    val renames = remember(toolId) { mutableStateMapOf<Int, String>() }
    val deletes = remember(toolId) { mutableStateListOf<Int>() }
    var renameTarget by remember(toolId) { mutableStateOf<Int?>(null) }
    val automationStep = controller.automationConfirmationRequest
        ?.takeIf { it.stepId == toolId }
        ?.let(controller::automationConfirmationStep)
    fun updateWritingProgress(phase: String, completed: Int, total: Int) {
        writingProgress = if (total > 0) completed.toFloat() / total.toFloat() else 0f
        writingLabel = if (total > 0) "$phase $completed/$total" else phase
        automationStep?.let { step ->
            controller.setAutomationRunStepProgress(step, writingProgress, writingLabel)
        }
    }
    fun startWritingAfterClosing() {
        writingProgress = 0f
        writingLabel = "应用抓取信息"
        automationStep?.let { step ->
            controller.setAutomationRunStepState(step, AutomationRunStepState.Running)
            controller.setAutomationRunStepProgress(step, 0f, writingLabel)
        }
        onApplyStarted?.invoke()
        controller.controllerScope.launch {
            delay(16)
            yieldToAppUiBeforeHeavyWork()
            val applied = controller.applyFetchInfoPreviewWithProgress(
                toolId,
                filterActive = filterActive,
                renames = renames.toMap(),
                deletes = deletes.toSet(),
                onProgress = ::updateWritingProgress
            )
            if (applied) {
                onApplied?.invoke() ?: onDismiss()
            } else {
                automationStep?.let { step -> controller.failAutomationConfirmationStep(step) }
            }
        }
    }
    // 预览行依赖 preview / 过滤开关 / 逐行重命名与删除；把它们都作为 key 缓存，
    // 避免每次重组（滚动、按钮高亮等）都重新遍历整本书生成行。
    val renameSnapshot = renames.toMap()
    val deleteSnapshot = deletes.toSet()
    val displayCatalogRows = remember(preview, filterActive, renameSnapshot, deleteSnapshot) {
        controller.fetchInfoCatalogPreviewRows(
            preview,
            filtered = filterActive,
            renames = renameSnapshot,
            deletes = deleteSnapshot
        )
    }
    val visibleCatalogRows = displayCatalogRows.filterNot { it.skipped }
    val skippedCatalogRows = displayCatalogRows.filter { it.skipped }
    val displayedCatalogRows = if (catalogOrderReversed) visibleCatalogRows.asReversed() else visibleCatalogRows
    val catalogOriginalCount = remember(preview) {
        if (preview.parameters.fetchCatalog) controller.fetchInfoWritableChapterCount() else 0
    }
    val catalogFetchedCount = remember(preview) {
        if (preview.parameters.fetchCatalog) preview.filtered.catalog.count { !it.isVolume } else 0
    }
    val isCoverPreview = preview.parameters.fetchCover
    val isIntroPreview = preview.parameters.fetchIntro
    val existingIntroText = remember(preview) {
        if (isIntroPreview) controller.fetchInfoExistingIntroText(preview) else ""
    }
    val headingTitle = preview.filtered.title.ifBlank { preview.raw.title }
    val headingAuthor = preview.filtered.author.ifBlank { preview.raw.author }
    val headingText = listOf(headingTitle, headingAuthor)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
        .ifBlank { "抓取预览" }

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
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = headingText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (preview.parameters.fetchCatalog) {
                        IconButton(
                            onClick = { showRulePanel = true },
                            enabled = !writing,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.Tune, contentDescription = "规则", modifier = Modifier.size(19.dp))
                        }
                        TextButton(
                            onClick = { catalogOrderReversed = !catalogOrderReversed },
                            enabled = !writing,
                            shape = ControlShape,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (catalogOrderReversed) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("逆序")
                        }
                    }
                    if (isIntroPreview) {
                        IconButton(
                            onClick = { showIntroRulePanel = true },
                            enabled = !writing,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.Tune, contentDescription = "规则", modifier = Modifier.size(19.dp))
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !writing,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(19.dp))
                    }
                }
                if (preview.parameters.fetchCatalog) {
                    val hasSkipped = skippedCatalogRows.isNotEmpty()
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "原章节 $catalogOriginalCount 章",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "抓取章节 $catalogFetchedCount 章",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (hasSkipped) {
                                Surface(
                                    onClick = { showSkippedDialog = true },
                                    shape = ControlShape,
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f),
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Outlined.ErrorOutline,
                                            contentDescription = "查看不写入章节",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (isCoverPreview) {
                FetchedCoverPreviewBlock(
                    parameters = preview.parameters,
                    info = preview.raw,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else if (preview.parameters.fetchCatalog) {
                FetchCatalogComparePane(
                    rows = displayedCatalogRows,
                    filterIssues = preview.filterIssues,
                    orderReversed = catalogOrderReversed,
                    onRename = { position -> renameTarget = position },
                    onToggleDelete = { position ->
                        if (deletes.contains(position)) deletes.remove(position) else deletes.add(position)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item("result-pair") {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val gap = 8.dp
                            val columnWidth = (maxWidth - gap) / 2
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(gap)
                            ) {
                                FetchedInfoBlock(
                                    title = if (isIntroPreview) "epub 原简介" else "原始结果",
                                    info = if (isIntroPreview) preview.raw.copy(intro = existingIntroText) else preview.raw,
                                    introMaxHeight = if (isIntroPreview) 330.dp else 220.dp,
                                    modifier = Modifier.width(columnWidth)
                                )
                                FetchedInfoBlock(
                                    title = if (isIntroPreview) "过滤后简介" else "过滤结果",
                                    info = preview.filtered,
                                    introMaxHeight = if (isIntroPreview) 330.dp else 220.dp,
                                    modifier = Modifier.width(columnWidth)
                                )
                            }
                        }
                    }
                    if (preview.filterIssues.isNotEmpty()) {
                        item("issues-title") {
                            ReplacementSectionHeader("跳过过滤", preview.filterIssues.size)
                        }
                        items(preview.filterIssues, key = { "${it.lineNo}-${it.text}" }) { issue ->
                            FetchInfoFilterIssueRow(issue)
                        }
                    }
                }
            }
            if (writing) {
                ToolRunProgress(
                    toolName = writingLabel,
                    progress = writingProgress
                )
            }
            ButtonRow {
                Button(
                    enabled = !writing && !controller.busy,
                    onClick = {
                        if (onApplyStarted != null || automationStep != null) {
                            startWritingAfterClosing()
                        } else {
                            writing = true
                            writingProgress = 0f
                            writingLabel = "应用抓取信息"
                            scope.launch {
                                yieldToAppUiBeforeHeavyWork()
                                val applied = controller.applyFetchInfoPreviewWithProgress(
                                    toolId,
                                    filterActive = filterActive,
                                    renames = renames.toMap(),
                                    deletes = deletes.toSet(),
                                    onProgress = ::updateWritingProgress
                                )
                                writing = false
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
                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("确认应用")
                }
            }
        }
    }

    if (showRulePanel) {
        FetchInfoCatalogRuleDialog(
            controller = controller,
            onDismiss = { showRulePanel = false }
        )
    }

    if (showIntroRulePanel) {
        FetchInfoIntroRuleDialog(
            controller = controller,
            onDismiss = { showIntroRulePanel = false }
        )
    }

    if (showSkippedDialog) {
        FetchSkippedChaptersDialog(
            rows = skippedCatalogRows,
            onDismiss = { showSkippedDialog = false }
        )
    }

    renameTarget?.let { position ->
        val initial = renames[position]
            ?: displayCatalogRows.firstOrNull { it.chapterPosition == position }?.fetchedTitle.orEmpty()
        FetchCatalogRenameDialog(
            initialValue = initial,
            onDismiss = { renameTarget = null },
            onConfirm = { value ->
                renames[position] = value
                renameTarget = null
            }
        )
    }
}

@Composable
private fun FetchedInfoBlock(
    parameters: FetchInfoParameters? = null,
    title: String,
    info: FetchedInfo,
    showCoverImage: Boolean = false,
    catalogRows: List<FetchInfoCatalogPreviewRow> = emptyList(),
    introMaxHeight: Dp = 220.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (showCoverImage) {
                FetchCoverImage(parameters = parameters, coverUrl = info.coverUrl)
            } else if (info.coverUrl.isNotBlank()) {
                FetchInfoLine("封面", info.coverUrl)
            }
            if (info.intro.isNotBlank()) {
                val introScrollState = rememberScrollState()
                Text(
                    text = "简介",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = ControlShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = introMaxHeight)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = info.intro,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(introScrollState)
                                .padding(start = 8.dp, top = 7.dp, end = 20.dp, bottom = 7.dp)
                        )
                        ContentScrollbar(
                            state = introScrollState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(top = 3.dp, end = 2.dp, bottom = 3.dp)
                        )
                    }
                }
            }
            if (catalogRows.isNotEmpty()) {
                Text(
                    text = "目录 ${catalogRows.size}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                catalogRows.forEach { row ->
                    FetchCatalogCompareRow(row)
                }
            } else if (info.catalog.isNotEmpty()) {
                Text(
                    text = "目录 ${info.catalog.size}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                info.catalog.forEach { item ->
                    Text(
                        text = item.previewText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FetchedCoverPreviewBlock(
    parameters: FetchInfoParameters,
    info: FetchedInfo,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "封面预览",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            FetchCoverImage(
                parameters = parameters,
                coverUrl = info.coverUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun FetchCatalogComparePane(
    rows: List<FetchInfoCatalogPreviewRow>,
    filterIssues: List<FetchInfoFilterIssue> = emptyList(),
    orderReversed: Boolean = false,
    onRename: (Int) -> Unit = {},
    onToggleDelete: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(orderReversed) {
        listState.scrollToItem(0)
    }
    val itemCount = rows.size + if (filterIssues.isNotEmpty()) filterIssues.size + 1 else 0
    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.74f)),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (itemCount <= 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "没有抓取到目录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 10.dp),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(
                        rows,
                        key = { index, row -> "${index}-${row.fileName}-${row.fetchedTitle}" }
                    ) { index, row ->
                        FetchCatalogCompareRow(
                            row = row,
                            interactive = true,
                            onRename = onRename,
                            onToggleDelete = onToggleDelete
                        )
                        if (index < rows.lastIndex || filterIssues.isNotEmpty()) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                    if (filterIssues.isNotEmpty()) {
                        item("issues-title") {
                            ReplacementSectionHeader("跳过过滤", filterIssues.size)
                        }
                        items(filterIssues, key = { "issue-${it.lineNo}-${it.text}" }) { issue ->
                            FetchInfoFilterIssueRow(issue)
                        }
                    }
                }
                ContentScrollbar(
                    state = listState,
                    itemCount = itemCount,
                    directDrag = true,
                    thumbFollowsDrag = true,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun FetchInfoFilterIssueRow(issue: FetchInfoFilterIssue) {
    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "第 ${issue.lineNo} 行：${issue.reason}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = issue.text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FetchCatalogCompareRow(
    row: FetchInfoCatalogPreviewRow,
    interactive: Boolean = false,
    onRename: (Int) -> Unit = {},
    onToggleDelete: (Int) -> Unit = {}
) {
    val hasActions = interactive && !row.isVolume && !row.skipped && row.chapterPosition >= 0

    val lines = buildList {
        if (row.isVolume && row.willCreateVolume) {
            // 缺卷：左列主行直接显示"缺卷将新建"，与右侧卷名对齐成一行。
            add(
                CatalogCompareLineSpec(
                    left = "缺卷将新建",
                    right = row.fetchedTitle,
                    leftError = true,
                    rightStrong = row.fetchedTitle.isNotBlank()
                )
            )
        } else if (row.missingFetch) {
            // 原章节多出来、没抓到对应内容：整行变灰，右侧标注保持原样。
            add(
                CatalogCompareLineSpec(
                    left = row.originalTitle,
                    right = "未抓到·保持原样",
                    leftDimmed = true,
                    rightDimmed = true
                )
            )
        } else {
            add(
                CatalogCompareLineSpec(
                    left = row.originalTitle,
                    right = if (row.deleted) "已删除（保留原标题）" else row.fetchedTitle,
                    rightStrong = !row.deleted && row.fetchedTitle.isNotBlank(),
                    rightError = row.deleted
                )
            )
        }
    }

    if (hasActions) {
        var menuExpanded by remember(row.chapterPosition) { mutableStateOf(false) }
        CatalogCompareBlock(
            lines = lines,
            rightColumnModifier = Modifier.pointerInput(row.chapterPosition) {
                detectTapGestures(onLongPress = { menuExpanded = true })
            },
            rightColumnOverlay = {
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RowShape
                    )
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        enabled = !row.deleted,
                        onClick = {
                            menuExpanded = false
                            onRename(row.chapterPosition)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (row.deleted) "撤销删除" else "删除",
                                color = if (row.deleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleDelete(row.chapterPosition)
                        }
                    )
                }
            }
        )
    } else {
        CatalogCompareBlock(lines = lines)
    }
}

private data class CatalogCompareLineSpec(
    val left: String,
    val right: String,
    val leftError: Boolean = false,
    val rightStrong: Boolean = false,
    val rightError: Boolean = false,
    val leftDimmed: Boolean = false,
    val rightDimmed: Boolean = false
)

// 左右两列共用一条贯穿整行的竖线，行内多条信息不再断开。
// rightColumnModifier/rightColumnOverlay 只作用在右列（抓取章节），用于长按弹菜单并让菜单锚定右侧。
@Composable
private fun CatalogCompareBlock(
    lines: List<CatalogCompareLineSpec>,
    rightColumnModifier: Modifier = Modifier,
    rightColumnOverlay: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            lines.forEach { line ->
                Text(
                    text = line.left,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        line.leftError -> MaterialTheme.colorScheme.error
                        line.leftDimmed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .defaultMinSize(minHeight = 18.dp)
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = rightColumnModifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                lines.forEach { line ->
                    Text(
                        text = line.right,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (line.rightStrong) FontWeight.Medium else FontWeight.Normal,
                        color = when {
                            line.rightError -> MaterialTheme.colorScheme.error
                            line.rightDimmed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            line.right.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
            rightColumnOverlay()
        }
    }
}

@Composable
private fun FetchSkippedChaptersDialog(
    rows: List<FetchInfoCatalogPreviewRow>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fixedDialogWidth(fraction = 0.9f, maxWidth = 420.dp)
                .heightIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "不写入章节 ${rows.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(rows, key = { index, _ -> "skipped-$index" }) { index, row ->
                        Text(
                            text = "${index + 1}. ${row.fetchedTitle.ifBlank { "（空标题）" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FetchCoverImage(
    parameters: FetchInfoParameters? = null,
    coverUrl: String,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(292.dp)
) {
    var imageInfo by remember(coverUrl) { mutableStateOf<FetchCoverImageInfo?>(null) }
    var loading by remember(coverUrl) { mutableStateOf(false) }
    var errorText by remember(coverUrl) { mutableStateOf<String?>(null) }

    LaunchedEffect(coverUrl) {
        imageInfo = null
        errorText = null
        if (coverUrl.isBlank()) return@LaunchedEffect
        loading = true
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val request = parameters?.let { buildFetchInfoCoverRequest(it, coverUrl) }
                val response = if (request?.sameHostRedirectOnly == true) {
                    FetchHttpClient.getBytes(
                        request.url,
                        request.headers,
                        ::isSosadSameHostHttpsRedirect,
                        maxBytes = HTTP_IMAGE_RESPONSE_MAX_BYTES
                    )
                } else if (request != null) {
                    FetchHttpClient.getBytes(
                        request.url,
                        request.headers,
                        maxBytes = HTTP_IMAGE_RESPONSE_MAX_BYTES
                    )
                } else {
                    FetchHttpClient.getBytes(
                        coverUrl,
                        maxBytes = HTTP_IMAGE_RESPONSE_MAX_BYTES
                    )
                }
                val size = imageSize(response.bytes) ?: error("无法解析封面图片")
                validateImageDimensions(size, "封面图片")
                val bitmap = BitmapFactory.decodeByteArray(response.bytes, 0, response.bytes.size)
                    ?: error("无法解析封面图片")
                FetchCoverImageInfo(
                    bitmap = bitmap.asImageBitmap(),
                    type = coverImageType(response.contentType, request?.url ?: coverUrl),
                    byteSize = response.bytes.size,
                    width = bitmap.width,
                    height = bitmap.height
                )
            }
        }
        imageInfo = result.getOrNull()
        errorText = result.exceptionOrNull()?.message
        loading = false
    }

    Surface(
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            imageInfo?.let { info ->
                Text(
                    text = "${info.type} · ${formatCoverByteSize(info.byteSize)} · ${info.width}×${info.height}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val info = imageInfo
                when {
                    info != null -> {
                        Image(
                            bitmap = info.bitmap,
                            contentDescription = "封面预览",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    loading -> {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(0.72f),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    else -> {
                        Text(
                            text = errorText?.let { "封面加载失败：$it" } ?: "无封面",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
