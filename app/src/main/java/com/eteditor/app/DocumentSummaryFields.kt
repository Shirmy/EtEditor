package com.eteditor

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.eteditor.core.CheckReport
import com.eteditor.core.DocumentKind
import kotlinx.coroutines.launch

@Composable
internal fun DocumentSummary(
    controller: EditorController,
    documentSessionKey: Int = 0,
    directoryOpen: Boolean = false,
    onToggleDirectory: (() -> Unit)? = null,
    onOpenFile: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    onOpenAutomation: (() -> Unit)? = null,
    automationOpen: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showFileMenu by remember(documentSessionKey) { mutableStateOf(false) }
    var showBookTitleEditDialog by remember(documentSessionKey) { mutableStateOf(false) }
    var showBookTitleFilterDialog by remember(documentSessionKey) { mutableStateOf(false) }
    var showTxtChapterRulesDialog by remember(documentSessionKey) { mutableStateOf(false) }
    var showTxtPurifyRulesDialog by remember(documentSessionKey) { mutableStateOf(false) }
    var showUnsavedOpenDialog by remember(documentSessionKey) { mutableStateOf(false) }
    var checkDialogReport by remember(documentSessionKey) { mutableStateOf<CheckReport?>(null) }
    val scope = rememberCoroutineScope()
    val documentOpen = controller.kind != DocumentKind.None
    val saveEnabled = documentOpen && !controller.busy

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (controller.kind == DocumentKind.Txt) {
            val txtDirectoryEnabled = onToggleDirectory != null
            Column(
                modifier = Modifier.width(72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { onToggleDirectory?.invoke() },
                        enabled = txtDirectoryEnabled,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (directoryOpen) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (directoryOpen) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.List, contentDescription = if (directoryOpen) "收起目录" else "目录", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { showTxtChapterRulesDialog = true },
                        enabled = saveEnabled,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = "目录规则", modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    text = if (controller.txtCatalogParsing && controller.chapters.isEmpty()) {
                        "识别中"
                    } else {
                        "章节 ${controller.chapters.size}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else if (controller.chapters.isNotEmpty() && onToggleDirectory != null) {
            Column(
                modifier = Modifier.width(46.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                IconButton(
                    onClick = onToggleDirectory,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (directoryOpen) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (directoryOpen) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.List, contentDescription = if (directoryOpen) "收起目录" else "展开目录", modifier = Modifier.size(18.dp))
                }
                Text(
                    text = chapterProgressText(controller),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            Spacer(Modifier.width(46.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val actionWidth = if (controller.kind == DocumentKind.Txt) 28.dp else 0.dp
                val titleMaxWidth = (this.maxWidth - actionWidth).coerceAtLeast(48.dp)
                val titleDoubleTapModifier = if (
                    controller.kind == DocumentKind.Txt &&
                    controller.txtDoubleTapTitleEdit &&
                    saveEnabled
                ) {
                    Modifier.pointerInput(controller.title) {
                        detectTapGestures(onDoubleTap = { showBookTitleEditDialog = true })
                    }
                } else {
                    Modifier
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = controller.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = titleMaxWidth)
                            .then(titleDoubleTapModifier)
                    )
                    if (controller.kind == DocumentKind.Txt) {
                        IconButton(
                            onClick = { showBookTitleFilterDialog = true },
                            enabled = saveEnabled,
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            ),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Outlined.FilterAlt, contentDescription = "书名过滤", modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
            val summaryMeta = if (documentOpen) documentSummaryMeta(controller) else ""
            if (summaryMeta.isNotBlank()) {
                Text(
                    text = summaryMeta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (documentOpen) {
            Row(
                modifier = Modifier.widthIn(min = 112.dp, max = 188.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (controller.kind == DocumentKind.Txt) {
                    IconButton(
                        onClick = { showTxtPurifyRulesDialog = true },
                        enabled = saveEnabled,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = "净化规则", modifier = Modifier.size(18.dp))
                    }
                }
                if (controller.kind == DocumentKind.Epub) {
                    IconButton(
                        onClick = { onOpenAutomation?.invoke() },
                        enabled = saveEnabled && onOpenAutomation != null,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (automationOpen) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (automationOpen) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = "执行", modifier = Modifier.size(18.dp))
                    }
                }
                Box {
                    IconButton(
                        onClick = { showFileMenu = true },
                        enabled = saveEnabled,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "更多文件操作", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = showFileMenu,
                        onDismissRequest = { showFileMenu = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 6.dp,
                        modifier = Modifier.dialogBorder()
                    ) {
                        DropdownMenuItem(
                            text = { Text("保存") },
                            onClick = {
                                showFileMenu = false
                                onSave?.invoke()
                            },
                            enabled = onSave != null
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        DropdownMenuItem(
                            text = { Text("另打开") },
                            onClick = {
                                showFileMenu = false
                                if (controller.hasUnsavedChanges) {
                                    showUnsavedOpenDialog = true
                                } else {
                                    onOpenFile?.invoke()
                                }
                            },
                            enabled = onOpenFile != null
                        )
                    }
                }
            }
        }
    }

    if (showBookTitleEditDialog) {
        TxtBookTitleEditDialog(
            controller = controller,
            onDismiss = { showBookTitleEditDialog = false }
        )
    }

    if (showBookTitleFilterDialog) {
        TxtBookTitleFilterDialog(
            controller = controller,
            onDismiss = { showBookTitleFilterDialog = false }
        )
    }

    if (showTxtChapterRulesDialog) {
        TxtChapterRulesDialog(
            controller = controller,
            onDismiss = { showTxtChapterRulesDialog = false }
        )
    }

    if (showTxtPurifyRulesDialog) {
        TxtPurifyRulesDialog(
            controller = controller,
            onDismiss = { showTxtPurifyRulesDialog = false }
        )
    }

    if (showUnsavedOpenDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedOpenDialog = false },
            modifier = Modifier
                .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
                .dialogBorder(),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "未保存修改",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showUnsavedOpenDialog = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(19.dp))
                    }
                }
            },
            text = {
                Text(
                    text = "当前文件有未保存修改，可以不保存继续打开，或保存后打开其他文件名",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showUnsavedOpenDialog = false
                            onOpenFile?.invoke()
                        },
                        enabled = onOpenFile != null && !controller.busy,
                        shape = ControlShape,
                        contentPadding = CompactButtonPadding
                    ) {
                        Text("继续打开")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                controller.saveToOriginal()
                                if (!controller.hasUnsavedChanges) {
                                    showUnsavedOpenDialog = false
                                    onOpenFile?.invoke()
                                }
                            }
                        },
                        enabled = onOpenFile != null && !controller.busy,
                        shape = ControlShape,
                        contentPadding = CompactButtonPadding
                    ) {
                        Text("保存并打开")
                    }
                }
            },
            shape = PreviewShape,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    checkDialogReport?.let { report ->
        AlertDialog(
            onDismissRequest = { checkDialogReport = null },
            modifier = Modifier
                .adaptiveDialogWidth(AdaptiveDialogWidth.Medium)
                .dialogBorder(),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = {
                DialogTitleWithClose(
                    title = "保存前检查",
                    onDismiss = { checkDialogReport = null },
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = { CheckReportView(report) },
            confirmButton = {},
            shape = PreviewShape,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

private fun documentSummaryMeta(controller: EditorController): String {
    val status = controller.statusMessage.takeIf {
        it.startsWith("已保存") ||
            it.startsWith("已另存") ||
            it.startsWith("保存前检查")
    }
    if (controller.kind == DocumentKind.Txt) {
        return listOfNotNull(
            txtSummaryMeta(controller.subtitle).takeIf { it.isNotBlank() },
            status
        ).joinToString(" · ")
    }
    if (controller.kind == DocumentKind.Epub) {
        return listOfNotNull(
            epubSummaryMeta(controller).takeIf { it.isNotBlank() },
            status
        ).joinToString(" · ")
    }
    return status.orEmpty()
}

private fun epubSummaryMeta(controller: EditorController): String {
    return controller.epubSummaryMeta
}

private fun txtSummaryMeta(subtitle: String): String {
    val parts = subtitle.split("|").map { it.trim() }
    val encoding = summaryEncodingLabel(parts.firstOrNull { part ->
        part.isNotBlank() &&
            part != "TXT" &&
            !part.contains("编码") &&
            !part.contains("换行") &&
            !part.startsWith("章节") &&
            !part.startsWith("问题") &&
            !Regex("""^\d+(?:\.\d+)?\s*(?:GB|MB|KB|B)${'$'}""").matches(part)
    } ?: "UTF-8")
    val lineEnding = parts.firstOrNull { part ->
        part == "CRLF" ||
            part == "CR" ||
            part == "LF" ||
            part == "\u6df7\u5408" ||
            part == "\u65e0"
    }
    val wordCount = parts.firstNotNullOfOrNull { part ->
        Regex("""^(\d+)(?:\s*字)?${'$'}""").matchEntire(part)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }
    val sizeText = Regex("""\d+(?:\.\d+)?\s*(?:GB|MB|KB|B)""").find(subtitle)
        ?.value
        ?.replace(" ", "")
    return listOfNotNull(
        encoding,
        lineEnding,
        wordCount?.let { compactCountLabel(it) },
        sizeText
    ).joinToString("|")
}

private fun summaryEncodingLabel(encoding: String): String {
    val upper = encoding.uppercase()
    return when {
        upper.startsWith("UTF-16") || upper.startsWith("UTF16") -> "UTF-16"
        upper.startsWith("UTF-8") || upper.startsWith("UTF8") -> "UTF-8"
        else -> encoding
    }
}

private fun chapterProgressText(controller: EditorController): String {
    return if (controller.previewChapterCount > 0) {
        "${controller.previewChapterIndex + 1}/${controller.previewChapterCount}"
    } else {
        "章节 ${controller.chapters.size}"
    }
}

@Composable
private fun CheckReportView(report: CheckReport) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            if (report.ok) "检查通过" else "检查未通过",
            fontWeight = FontWeight.SemiBold,
            color = if (report.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        report.errors.forEach {
            Text("问题：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        report.warnings.forEach {
            Text("提醒：$it", style = MaterialTheme.typography.bodySmall)
        }
    }
}
