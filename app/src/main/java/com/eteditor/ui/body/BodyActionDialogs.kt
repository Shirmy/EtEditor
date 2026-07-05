package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
internal fun EpubLongPressSplitChapterDialog(
    controller: EditorController,
    chapterIndex: Int,
    lineIndex: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val chapter = controller.chapters.getOrNull(chapterIndex)
    val lineCount = remember(chapterIndex, controller.documentContentVersion) {
        controller.epubChapterBodyLineCount(chapterIndex)
    }
    val defaultTitle = remember(chapterIndex, lineIndex, controller.documentContentVersion) {
        controller.epubSplitChapterDefaultTitle(chapterIndex, lineIndex)
    }
    var title by remember(chapterIndex, lineIndex, defaultTitle) { mutableStateOf(defaultTitle) }
    val canSplit = chapter != null && lineIndex in 1 until lineCount
    val chapterLabel = chapter?.let { it.title.ifBlank { it.fileName } }.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = "分章",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = chapterLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "将从第 ${lineIndex + 1} 行开始生成新章节",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ToolTextInputField(
                    label = "新章节标题",
                    value = title,
                    onValueChange = { title = it },
                    height = 42.dp
                )
                if (!canSplit) {
                    Text(
                        text = if (lineIndex <= 0) {
                            "第一行不能作为分章位置。"
                        } else {
                            "当前行超出正文范围，无法分章。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSplit,
                onClick = { onConfirm(title) },
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Text("分章")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = PreviewShape,
        containerColor = MaterialTheme.colorScheme.surface
    )
}

internal data class ParagraphEditDialogLayoutMetrics(
    val minDialogHeightDp: Int,
    val maxDialogHeightDp: Int,
    val minEditorHeightDp: Int,
    val headerActionSizeDp: Int,
    val editorPaddingDp: Int,
    val verticalMarginDp: Int
)

internal fun paragraphEditDialogLayoutMetrics(): ParagraphEditDialogLayoutMetrics {
    return ParagraphEditDialogLayoutMetrics(
        minDialogHeightDp = 260,
        maxDialogHeightDp = 430,
        minEditorHeightDp = 120,
        headerActionSizeDp = 34,
        editorPaddingDp = 10,
        verticalMarginDp = 32
    )
}

internal fun paragraphEditDialogHeightDp(
    screenHeightDp: Int,
    imeBottomDp: Int,
    metrics: ParagraphEditDialogLayoutMetrics = paragraphEditDialogLayoutMetrics()
): Int {
    val availableHeight = screenHeightDp - imeBottomDp.coerceAtLeast(0) - metrics.verticalMarginDp
    return availableHeight.coerceIn(metrics.minDialogHeightDp, metrics.maxDialogHeightDp)
}

@Composable
internal fun EpubParagraphEditDialog(
    controller: EditorController,
    chapterIndex: Int,
    bodyOffset: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    val book = controller.epub
    val chapter = book?.chapters?.getOrNull(chapterIndex)
    val bodyText = remember(chapterIndex, controller.documentContentVersion) {
        chapter?.let { htmlVisibleBodyContent(it.html) } ?: ""
    }
    val paragraphRange = remember(bodyText, bodyOffset) {
        val ranges = splitBodyIntoParagraphRanges(bodyText)
        if (ranges.isEmpty()) return@remember 0 to 0
        val idx = findParagraphIndexAtOffset(ranges, bodyOffset)
        ranges[idx]
    }
    val paragraphText = remember(bodyText, paragraphRange) {
        bodyText.substring(paragraphRange.first.coerceIn(0, bodyText.length), paragraphRange.second.coerceIn(0, bodyText.length))
    }
    val editableParagraph = remember(paragraphText) { editableParagraphContent(paragraphText) }
    var editValue by remember(editableParagraph) { mutableStateOf(editableParagraph.text) }
    val layoutMetrics = remember { paragraphEditDialogLayoutMetrics() }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val imeBottomDp = with(density) {
        WindowInsets.ime.getBottom(this).toDp().value.roundToInt()
    }
    val dialogHeightDp = paragraphEditDialogHeightDp(
        screenHeightDp = configuration.screenHeightDp,
        imeBottomDp = imeBottomDp,
        metrics = layoutMetrics
    )
    val canSave = editValue != editableParagraph.text
    val focusRequester = remember { FocusRequester() }
    val hostView = LocalView.current
    fun confirmEdit() {
        val start = paragraphRange.first.coerceIn(0, bodyText.length)
        val end = paragraphRange.second.coerceIn(0, bodyText.length)
        val editedParagraphText = editableParagraph.withEditedText(editValue)
        val newBody = bodyText.substring(0, start) +
            editedParagraphText +
            bodyText.substring(end)
        val restoreOffset = paragraphEditRestoreOffset(
            paragraphRange = start to end,
            bodyOffset = bodyOffset,
            editedTextLength = editValue.length
        )
        onConfirm(newBody, restoreOffset)
    }
    LaunchedEffect(chapterIndex, bodyOffset, editableParagraph) {
        delay(120)
        runCatching { focusRequester.requestFocus() }
        runCatching { hostView.showSoftKeyboard() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            shadowElevation = 8.dp,
            modifier = Modifier
                .adaptiveDialogWidth(AdaptiveDialogWidth.Preview)
                .height(dialogHeightDp.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "编辑段落",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(layoutMetrics.headerActionSizeDp.dp)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "取消", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { confirmEdit() },
                        enabled = canSave,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        ),
                        modifier = Modifier.size(layoutMetrics.headerActionSizeDp.dp)
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = "保存", modifier = Modifier.size(18.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = layoutMetrics.minEditorHeightDp.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                            ControlShape
                        )
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ControlShape
                        )
                        .padding(layoutMetrics.editorPaddingDp.dp)
                ) {
                    BasicTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester)
                            .verticalScroll(rememberScrollState()),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Default),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
internal fun TxtSupplementChapterDialog(
    lineText: String,
    initialChapterNumber: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> String?
) {
    var chapterNumber by remember(initialChapterNumber) { mutableStateOf(initialChapterNumber) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
            .dialogBorder(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("补章节") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "当前行：${lineText.trim().ifBlank { "空行" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                CompactDialogTextInputField(
                    label = "章节号",
                    value = chapterNumber,
                    onValueChange = {
                        chapterNumber = it.filterNot { char -> char.isWhitespace() }
                        errorMessage = null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val error = onConfirm(chapterNumber)
                    if (error == null) {
                        onDismiss()
                    } else {
                        errorMessage = error
                    }
                },
                enabled = chapterNumber.isNotBlank(),
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = ControlShape,
                contentPadding = CompactButtonPadding
            ) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = PreviewShape,
        containerColor = MaterialTheme.colorScheme.surface
    )
}
