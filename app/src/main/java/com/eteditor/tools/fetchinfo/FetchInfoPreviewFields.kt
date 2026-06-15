package com.eteditor

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
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
            val applied = controller.applyFetchInfoPreviewWithProgress(toolId, ::updateWritingProgress)
            if (applied) {
                onApplied?.invoke() ?: onDismiss()
            } else {
                automationStep?.let { step -> controller.failAutomationConfirmationStep(step) }
            }
        }
    }
    val filteredCatalogRows = remember(preview) { controller.fetchInfoCatalogPreviewRows(preview, filtered = true) }
    val displayedCatalogRows = remember(filteredCatalogRows, catalogOrderReversed) {
        if (catalogOrderReversed) filteredCatalogRows.asReversed() else filteredCatalogRows
    }
    val catalogSummary = remember(preview) {
        if (preview.parameters.fetchCatalog) controller.fetchInfoCatalogSummary(preview) else ""
    }
    val isCoverPreview = preview.parameters.fetchCover
    val isIntroPreview = preview.parameters.fetchIntro
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
                Text(
                    text = catalogSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (isCoverPreview) {
                FetchedCoverPreviewBlock(
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
                                    title = if (isIntroPreview) "原始简介" else "原始结果",
                                    info = preview.raw,
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
                                val applied = controller.applyFetchInfoPreviewWithProgress(toolId, ::updateWritingProgress)
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
}

@Composable
private fun FetchedInfoBlock(
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
                FetchCoverImage(info.coverUrl)
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
                        FetchCatalogCompareRow(row)
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
                    fixedItemHeight = 42.dp,
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
private fun FetchCatalogCompareRow(row: FetchInfoCatalogPreviewRow) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        CatalogCompareLine(
            left = row.originalTitle.ifBlank { "无标题" },
            right = row.fetchedTitle,
            rightStrong = row.fetchedTitle.isNotBlank()
        )
        if (row.isVolume && row.willCreateVolume) {
            CatalogCompareLine(
                left = "缺卷将新建",
                right = "",
                leftError = true
            )
        }
        if (row.skipped) {
            CatalogCompareLine(
                left = "超出范围",
                right = "不写入",
                leftError = true,
                rightError = true
            )
        }
    }
}

@Composable
private fun CatalogCompareLine(
    left: String,
    right: String,
    leftError: Boolean = false,
    rightStrong: Boolean = false,
    rightError: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = left,
            style = MaterialTheme.typography.bodySmall,
            color = if (leftError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .height(18.dp)
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Text(
            text = right,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (rightStrong) FontWeight.Medium else FontWeight.Normal,
            color = when {
                rightError -> MaterialTheme.colorScheme.error
                right.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FetchCoverImage(
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
                val response = FetchHttpClient.getBytes(
                    coverUrl,
                    maxBytes = HTTP_IMAGE_RESPONSE_MAX_BYTES
                )
                val size = imageSize(response.bytes) ?: error("无法解析封面图片")
                validateImageDimensions(size, "封面图片")
                val bitmap = BitmapFactory.decodeByteArray(response.bytes, 0, response.bytes.size)
                    ?: error("无法解析封面图片")
                FetchCoverImageInfo(
                    bitmap = bitmap.asImageBitmap(),
                    type = coverImageType(response.contentType, coverUrl),
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
