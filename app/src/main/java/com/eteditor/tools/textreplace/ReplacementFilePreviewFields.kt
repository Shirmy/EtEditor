package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ReplacementPreviewStats(
    preview: ReplacementFilePreview,
    selectedSection: ReplacementPreviewSection,
    onSelectSection: (ReplacementPreviewSection) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        if (preview.multiRules.isNotEmpty()) {
            ReplacementStatChip(
                "多处",
                preview.multiRules.size.toString(),
                Modifier.weight(1f),
                selected = selectedSection == ReplacementPreviewSection.Multi,
                onClick = { onSelectSection(ReplacementPreviewSection.Multi) }
            )
        }
        if (preview.singleRules.isNotEmpty()) {
            ReplacementStatChip(
                "单处",
                preview.singleRules.size.toString(),
                Modifier.weight(1f),
                selected = selectedSection == ReplacementPreviewSection.Single,
                onClick = { onSelectSection(ReplacementPreviewSection.Single) }
            )
        }
        if (preview.zeroRules.isNotEmpty()) {
            ReplacementStatChip(
                "无匹配",
                preview.zeroRules.size.toString(),
                Modifier.weight(1f),
                selected = selectedSection == ReplacementPreviewSection.Zero,
                onClick = { onSelectSection(ReplacementPreviewSection.Zero) }
            )
        }
    }
}

@Composable
private fun ReplacementStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val chipContent: @Composable () -> Unit = {
        val labelColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        val valueColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                maxLines = 1
            )
        }
    }
    if (onClick == null) {
        Surface(
            shape = RowShape,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.54f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
            border = BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.56f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            ),
            modifier = modifier.height(32.dp),
            content = chipContent
        )
    } else {
        Surface(
            onClick = onClick,
            shape = RowShape,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.64f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
            border = BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.68f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
            ),
            modifier = modifier.height(32.dp),
            content = chipContent
        )
    }
}

@Composable
fun ReplacementSectionHeader(
    title: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
internal fun ReplacementMultiRuleRow(
    controller: EditorController,
    rule: ReplacementPreviewRule,
    expanded: Boolean,
    selectedMatchIds: Set<String>,
    onToggleExpanded: () -> Unit,
    onToggleMatch: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onApplySelected: () -> Unit,
    dimmed: Boolean = false,
    enabled: Boolean = true
) {
    val ruleMatchIds = remember(rule) { rule.matches.map { it.id }.toSet() }
    val selectedCount = ruleMatchIds.count { it in selectedMatchIds }
    val matchListState = rememberLazyListState()
    val titleColor = if (dimmed) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val countColor = if (dimmed) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dimmed) 0.13f else 0.24f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (dimmed) 0.42f else 0.7f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = enabled, onClick = onToggleExpanded),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = replacementRuleText(rule),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${rule.matches.size} 处",
                        style = MaterialTheme.typography.labelMedium,
                        color = countColor,
                        maxLines = 1
                    )
                    if (dimmed) {
                        Text(
                            text = "待应用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            maxLines = 1
                        )
                    }
                }
                IconButton(
                    onClick = onToggleExpanded,
                    enabled = enabled,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReplacementRuleActionButton(
                        text = "全选",
                        onClick = onSelectAll,
                        enabled = enabled && selectedCount < rule.matches.size,
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                    )
                    ReplacementRuleActionButton(
                        text = "全不选",
                        onClick = onClearAll,
                        enabled = enabled && selectedCount > 0,
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                    )
                    ReplacementRuleActionButton(
                        text = "应用选中",
                        onClick = onApplySelected,
                        enabled = enabled && selectedCount > 0,
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((rule.matches.size.coerceAtMost(5) * 72).dp)
                ) {
                    LazyColumn(
                        state = matchListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(rule.matches, key = { it.id }) { match ->
                            ReplacementMatchRow(
                                controller = controller,
                                match = match,
                                checked = match.id in selectedMatchIds,
                                onCheckedChange = { checked -> onToggleMatch(match.id, checked) },
                                grouped = true,
                                enabled = enabled
                            )
                        }
                    }
                    ContentScrollbar(
                        state = matchListState,
                        itemCount = rule.matches.size,
                        prominent = false,
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
private fun ReplacementRuleActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.82f else 0.5f)
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun ReplacementSingleRuleRow(
    controller: EditorController,
    rule: ReplacementPreviewRule,
    selectedMatchIds: Set<String>,
    onToggleMatch: (String, Boolean) -> Unit
) {
    val match = rule.matches.firstOrNull() ?: return
    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = replacementRuleText(rule),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "1 处",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            ReplacementMatchRow(
                controller = controller,
                match = match,
                checked = match.id in selectedMatchIds,
                onCheckedChange = { checked -> onToggleMatch(match.id, checked) }
            )
        }
    }
}

@Composable
private fun ReplacementMatchRow(
    controller: EditorController,
    match: ReplacementPreviewMatch,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    grouped: Boolean = false,
    enabled: Boolean = true
) {
    val context = remember(match.context, match.contextMatchStart, match.contextMatchEnd) {
        displaySearchContext(
            text = match.context,
            highlightStart = match.contextMatchStart,
            highlightEnd = match.contextMatchEnd
        )
    }
    val selected = controller.selectedReplacementPreviewMatchId == match.id
    Surface(
        onClick = { if (enabled) controller.selectReplacementPreviewMatch(match.id) },
        shape = if (grouped) ControlShape else RowShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        } else if (grouped) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (grouped) {
            null
        } else {
            BorderStroke(
                if (selected) 1.5.dp else 1.dp,
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
                }
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                modifier = Modifier.size(28.dp)
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
                    text = match.chapterTitle.ifBlank { match.fileName },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun ReplacementZeroRuleRow(rule: ReplacementPreviewRule) {
    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = replacementRuleText(rule),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

internal fun replacementInvalidRulesMessage(rules: List<ReplacementSkippedRule>): String {
    if (rules.isEmpty()) return "没有无效规则"
    return rules.joinToString("\n\n") { rule ->
        "第 ${rule.lineNo} 行：${rule.reason}\n${rule.text}"
    }
}

private fun replacementRuleText(rule: ReplacementPreviewRule): String {
    return "${displayReplacementPattern(rule.pattern, rule.regex)} -> ${displayLineBreakEscapes(rule.replacement)}"
}

private fun displayReplacementPattern(pattern: String, regex: Boolean): String {
    val escaped = displayLineBreakEscapes(pattern)
    return if (regex) escaped else "*$escaped"
}
