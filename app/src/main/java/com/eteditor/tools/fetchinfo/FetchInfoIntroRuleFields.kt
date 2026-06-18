package com.eteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun FetchInfoIntroRuleDialog(
    controller: EditorController,
    onDismiss: () -> Unit
) {
    var sortMode by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            shadowElevation = 8.dp,
            modifier = Modifier.fixedDialogWidth(fraction = 0.72f, maxWidth = 320.dp)
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
                        text = "简介规则",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RuleDialogIconButton(
                            icon = Icons.Outlined.SwapVert,
                            contentDescription = "排序",
                            selected = sortMode,
                            onClick = { sortMode = !sortMode }
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                FetchInfoIntroRulePanel(
                    controller = controller,
                    sortMode = sortMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                )
            }
        }
    }
}

@Composable
internal fun FetchInfoIntroRulePanel(
    controller: EditorController,
    sortMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val rules = controller.fetchInfoIntroRuleItems()
    var showCreate by remember { mutableStateOf(false) }
    var editRule by remember { mutableStateOf<FetchCatalogRuleItem?>(null) }
    var deleteConfirm by remember { mutableStateOf<DeleteConfirmRequest?>(null) }
    var openSwipeRowKey by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(rules, openSwipeRowKey) {
        val openedKey = openSwipeRowKey ?: return@LaunchedEffect
        if (rules.none { "fetch-intro-rule-${it.index}" == openedKey }) {
            openSwipeRowKey = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "共 ${rules.size} 项规则",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showCreate = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = "新增简介规则", modifier = Modifier.size(17.dp))
            }
        }
        if (rules.isEmpty()) {
            Text(
                text = "暂无规则",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            rules.forEachIndexed { position, rule ->
                DraggableCompactRuleListRow(
                    rowKey = "fetch-intro-rule-${rule.index}",
                    index = position + 1,
                    name = rule.name.ifBlank { "未命名" },
                    showIndex = false,
                    onEdit = { editRule = rule },
                    onDelete = {
                        deleteConfirm = DeleteConfirmRequest(
                            title = "确认删除规则",
                            message = "确定删除规则“${rule.name.ifBlank { "未命名" }}”吗？",
                            onConfirm = { controller.deleteFetchInfoIntroRule(rule.index) }
                        )
                    },
                    position = position,
                    itemCount = rules.size,
                    reorderStepPx = 0f,
                    displacedOffsetPx = 0f,
                    onMove = { target -> rules.getOrNull(target)?.let { controller.moveFetchInfoIntroRule(rule.index, it.index) } },
                    onDragVisualChange = { _, _ -> },
                    openSwipeRowKey = openSwipeRowKey,
                    onOpenSwipeRowChange = { openSwipeRowKey = it },
                    showDragHandle = false,
                    sortMode = sortMode,
                    leadingContent = {
                        FetchRuleEnableToggle(
                            checked = rule.enabled,
                            onCheckedChange = { enabled -> controller.setFetchInfoIntroRuleEnabled(rule.index, enabled) }
                        )
                    }
                )
            }
        }
    }

    if (showCreate) {
        FetchCatalogRuleEditDialog(
            title = "新增简介规则",
            initialCategory = FETCH_CATALOG_RULE_CATEGORY_PURIFY,
            initialName = "",
            initialSearch = "",
            initialReplacement = "",
            initialRegex = true,
            onDismiss = { showCreate = false },
            onConfirm = { _, name, search, replacement, regex ->
                controller.addFetchInfoIntroRule(name, search, replacement, regex)
                showCreate = false
            }
        )
    }

    editRule?.let { rule ->
        FetchCatalogRuleEditDialog(
            title = "编辑简介规则",
            initialCategory = rule.category,
            initialName = rule.name,
            initialSearch = rule.search,
            initialReplacement = rule.replacement,
            initialRegex = rule.regex,
            onDismiss = { editRule = null },
            onConfirm = { _, name, search, replacement, regex ->
                controller.updateFetchInfoIntroRule(rule.index, name, search, replacement, regex)
                editRule = null
            }
        )
    }

    deleteConfirm?.let { request ->
        DeleteConfirmDialog(
            request = request,
            onDismiss = { deleteConfirm = null }
        )
    }
}
