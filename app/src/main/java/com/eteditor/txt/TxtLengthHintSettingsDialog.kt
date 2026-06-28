package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
@Composable
fun TxtLengthHintSettingsDialog(
    controller: EditorController,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var shortChapterHintEnabledDraft by remember(controller.txtShortChapterHintEnabled) {
        mutableStateOf(controller.txtShortChapterHintEnabled)
    }
    var longChapterHintEnabledDraft by remember(controller.txtLongChapterHintEnabled) {
        mutableStateOf(controller.txtLongChapterHintEnabled)
    }
    var shortChapterThresholdText by remember(controller.txtShortChapterThreshold) {
        mutableStateOf(controller.txtShortChapterThreshold.toString())
    }
    var longChapterThresholdText by remember(controller.txtLongChapterThreshold) {
        mutableStateOf(controller.txtLongChapterThreshold.toString())
    }
    var hintModeDraft by remember(controller.txtChapterHintMode) {
        mutableStateOf(controller.txtChapterHintMode.takeIf { it in TXT_CHAPTER_HINT_MODES } ?: TXT_CHAPTER_HINT_MODE_AUTO)
    }
    fun applyChangesAndDismiss() {
        val shortThreshold = shortChapterThresholdText.toIntOrNull() ?: controller.txtShortChapterThreshold
        val longThreshold = longChapterThresholdText.toIntOrNull() ?: controller.txtLongChapterThreshold
        val autoMode = hintModeDraft == TXT_CHAPTER_HINT_MODE_AUTO
        val effectiveShortHintEnabled = if (autoMode) true else shortChapterHintEnabledDraft
        val effectiveLongHintEnabled = if (autoMode) true else longChapterHintEnabledDraft
        val effectiveShortThreshold = if (autoMode && shortThreshold <= 0) 1000 else shortThreshold
        val effectiveLongThreshold = if (autoMode && longThreshold <= 0) 10000 else longThreshold
        val changed = effectiveShortHintEnabled != controller.txtShortChapterHintEnabled ||
            effectiveLongHintEnabled != controller.txtLongChapterHintEnabled ||
            effectiveShortThreshold != controller.txtShortChapterThreshold ||
            effectiveLongThreshold != controller.txtLongChapterThreshold ||
            hintModeDraft != controller.txtChapterHintMode
        if (!changed) {
            onDismiss()
            return
        }
        scope.launchAfterTxtMoveChapterSync(controller, "\u4fee\u6539\u7ae0\u8282\u63d0\u793a\u8bbe\u7f6e") {
            controller.updateTxtChapterHintSettings(
                shortHintEnabled = effectiveShortHintEnabled,
                longHintEnabled = effectiveLongHintEnabled,
                shortThreshold = effectiveShortThreshold,
                longThreshold = effectiveLongThreshold,
                hintMode = hintModeDraft
            )
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = ::applyChangesAndDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            shadowElevation = 8.dp,
            modifier = Modifier
                .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
                .heightIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RuleCreateDialogHeader(
                    title = "\u957f\u77ed\u7ae0\u63d0\u793a",
                    onDismiss = ::applyChangesAndDismiss
                )
                val dialogScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp)
                        .verticalScroll(dialogScrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TxtLengthHintModeCard(
                        selectedMode = hintModeDraft,
                        onModeChange = { mode ->
                            hintModeDraft = mode
                            if (mode == TXT_CHAPTER_HINT_MODE_AUTO) {
                                shortChapterHintEnabledDraft = true
                                longChapterHintEnabledDraft = true
                            }
                        }
                    )
                    if (hintModeDraft == TXT_CHAPTER_HINT_MODE_AUTO) {
                        TxtLengthHintAutoStatsCard(
                            stats = txtAutoChapterHintStats(controller.txt?.chapters.orEmpty())
                        )
                    } else if (hintModeDraft == TXT_CHAPTER_HINT_MODE_MANUAL) {
                        TxtLengthHintThresholdCard(
                            title = "\u77ed\u7ae0\u63d0\u793a",
                            subtitle = "\u5c11\u4e8e\u8bbe\u5b9a\u5b57\u6570\u65f6\u6807\u8bb0\u4e3a\u77ed\u7ae0\u3002",
                            checked = shortChapterHintEnabledDraft,
                            onCheckedChange = { enabled -> shortChapterHintEnabledDraft = enabled },
                            numberTitle = "\u77ed\u7ae0\u5b57\u6570",
                            numberSubtitle = "\u5c11\u4e8e\u8be5\u5b57\u6570\u63d0\u793a\u3002",
                            value = shortChapterThresholdText,
                            unit = "\u5b57",
                            onValueChange = { value ->
                                shortChapterThresholdText = value
                            }
                        )
                        TxtLengthHintThresholdCard(
                            title = "\u957f\u7ae0\u63d0\u793a",
                            subtitle = "\u8d85\u8fc7\u8bbe\u5b9a\u5b57\u6570\u65f6\u6807\u8bb0\u4e3a\u957f\u7ae0\u3002",
                            checked = longChapterHintEnabledDraft,
                            onCheckedChange = { enabled -> longChapterHintEnabledDraft = enabled },
                            numberTitle = "\u957f\u7ae0\u5b57\u6570",
                            numberSubtitle = "\u8d85\u8fc7\u8be5\u5b57\u6570\u63d0\u793a\u3002",
                            value = longChapterThresholdText,
                            unit = "\u5b57",
                            onValueChange = { value ->
                                longChapterThresholdText = value
                            }
                        )
                        val manualShortThreshold = shortChapterThresholdText.toIntOrNull() ?: 0
                        val manualLongThreshold = longChapterThresholdText.toIntOrNull() ?: 0
                        if (shortChapterHintEnabledDraft && longChapterHintEnabledDraft &&
                            manualShortThreshold > 0 && manualLongThreshold > 0 &&
                            manualShortThreshold >= manualLongThreshold
                        ) {
                            Text(
                                text = "\u77ed\u7ae0\u5b57\u6570\u9700\u5c0f\u4e8e\u957f\u7ae0\u5b57\u6570",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TxtLengthHintTextBlock(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TxtLengthHintCompactSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = appSwitchColors(),
            modifier = Modifier.graphicsLayer(scaleX = 0.84f, scaleY = 0.84f)
        )
    }
}

@Composable
private fun TxtLengthHintModeCard(
    selectedMode: String,
    onModeChange: (String) -> Unit
) {
    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TxtLengthHintTextBlock(
                title = "\u5339\u914d\u6a21\u5f0f",
                subtitle = "\u81ea\u52a8\u6a21\u5f0f\u4f1a\u968f\u76ee\u5f55\u91cd\u7b97\u9608\u503c\u3002",
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TxtLengthHintModeButton(
                    text = "\u81ea\u52a8",
                    selected = selectedMode == TXT_CHAPTER_HINT_MODE_AUTO,
                    onClick = { onModeChange(TXT_CHAPTER_HINT_MODE_AUTO) }
                )
                TxtLengthHintModeButton(
                    text = "\u624b\u52a8",
                    selected = selectedMode == TXT_CHAPTER_HINT_MODE_MANUAL,
                    onClick = { onModeChange(TXT_CHAPTER_HINT_MODE_MANUAL) }
                )
            }
        }
    }
}

@Composable
private fun TxtLengthHintModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = ControlShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .width(50.dp)
            .height(30.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TxtLengthHintAutoStatsCard(
    stats: TxtAutoChapterHintStats
) {
    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TxtLengthHintTextBlock(
                title = "自动参数",
                subtitle = if (stats.hasThresholds) {
                    "根据当前目录自动计算，只读显示。"
                } else {
                    "有效样本不足，保存时暂用当前配置。"
                },
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
            TxtLengthHintReadonlyStatRow(
                label = "有效样本",
                value = "${stats.sampleCount} 章"
            )
            if (stats.hasThresholds) {
                TxtLengthHintReadonlyStatRow(
                    label = "基准字数",
                    value = txtLengthHintWordLabel(stats.medianWordCount ?: 0)
                )
                TxtLengthHintReadonlyStatRow(
                    label = "自动短章（${txtLengthHintFactorLabel(TXT_AUTO_SHORT_CHAPTER_FACTOR)}）",
                    value = "< ${txtLengthHintWordLabel(stats.shortThreshold ?: 0)}"
                )
                TxtLengthHintReadonlyStatRow(
                    label = "自动超长（${txtLengthHintFactorLabel(TXT_AUTO_LONG_CHAPTER_FACTOR)}）",
                    value = "> ${txtLengthHintWordLabel(stats.longThreshold ?: 0)}"
                )
                TxtLengthHintReadonlyStatRow(
                    label = "当前命中",
                    value = "短章 ${stats.shortHitCount} / 超长 ${stats.longHitCount}"
                )
            } else {
                TxtLengthHintReadonlyStatRow(
                    label = "计算结果",
                    value = "至少需要 8 章有效样本"
                )
            }
        }
    }
}

@Composable
private fun TxtLengthHintReadonlyStatRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun txtLengthHintWordLabel(value: Int): String = "$value 字"

private fun txtLengthHintFactorLabel(value: Float): String = "${value}倍"

@Composable
private fun TxtLengthHintThresholdCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    numberTitle: String,
    numberSubtitle: String,
    value: String,
    unit: String,
    onValueChange: (String) -> Unit
) {
    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TxtLengthHintTextBlock(
                    title = title,
                    subtitle = subtitle,
                    modifier = Modifier.weight(1f)
                )
                TxtLengthHintCompactSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            }
            if (checked) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TxtLengthHintTextBlock(
                        title = numberTitle,
                        subtitle = numberSubtitle,
                        modifier = Modifier.weight(1f)
                    )
                    TxtLengthHintNumberField(
                        value = value,
                        unit = unit,
                        onValueChange = onValueChange
                    )
                }
            }
        }
    }
}

@Composable
private fun TxtLengthHintNumberField(
    value: String,
    unit: String,
    onValueChange: (String) -> Unit
) {
    Surface(
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .width(96.dp)
            .height(34.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = { next -> onValueChange(next.filter { it.isDigit() }.take(6)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
