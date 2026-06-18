package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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

private fun fetchCatalogRuleCategoryLabel(category: String): String {
    return if (category == FETCH_CATALOG_RULE_CATEGORY_PURIFY) "净化" else "章节"
}

@Composable
internal fun FetchCatalogRenameDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            shadowElevation = 8.dp,
            modifier = Modifier.fixedDialogWidth(fraction = 0.88f, maxWidth = 360.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RuleCreateDialogHeader(title = "重命名抓取标题", onDismiss = onDismiss)
                ToolTextInputField(
                    label = "标题",
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth()
                )
                RuleCreateDialogActions(
                    onDismiss = onDismiss,
                    onConfirm = { onConfirm(value) },
                    confirmEnabled = value.isNotBlank()
                )
            }
        }
    }
}

@Composable
internal fun FetchInfoCatalogRuleDialog(
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
                        text = "目录规则",
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
                FetchInfoCatalogRulePanel(
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
internal fun FetchInfoCatalogRulePanel(
    controller: EditorController,
    sortMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val rules = controller.fetchInfoCatalogRuleItems()
    val chapterRules = rules.filter { it.category == FETCH_CATALOG_RULE_CATEGORY_CHAPTER }
    val purifyRules = rules.filter { it.category == FETCH_CATALOG_RULE_CATEGORY_PURIFY }
    var createCategory by remember { mutableStateOf<String?>(null) }
    var editRule by remember { mutableStateOf<FetchCatalogRuleItem?>(null) }
    var deleteConfirm by remember { mutableStateOf<DeleteConfirmRequest?>(null) }
    var openSwipeRowKey by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(rules, openSwipeRowKey) {
        val openedKey = openSwipeRowKey ?: return@LaunchedEffect
        if (rules.none { "fetch-rule-${it.index}" == openedKey }) {
            openSwipeRowKey = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FetchCatalogRuleGroup(
            title = "章节",
            groupRules = chapterRules,
            sortMode = sortMode,
            openSwipeRowKey = openSwipeRowKey,
            onOpenSwipeRowChange = { openSwipeRowKey = it },
            onEnabledChange = { rule, enabled -> controller.setFetchInfoCatalogRuleEnabled(rule.index, enabled) },
            onEdit = { editRule = it },
            onDelete = { rule ->
                deleteConfirm = DeleteConfirmRequest(
                    title = "确认删除规则",
                    message = "确定删除规则“${rule.name.ifBlank { "未命名" }}”吗？",
                    onConfirm = { controller.deleteFetchInfoCatalogRule(rule.index) }
                )
            },
            onMove = { from, to -> controller.moveFetchInfoCatalogRule(from, to) },
            onAdd = { createCategory = FETCH_CATALOG_RULE_CATEGORY_CHAPTER }
        )
        FetchCatalogRuleGroup(
            title = "净化",
            groupRules = purifyRules,
            sortMode = sortMode,
            openSwipeRowKey = openSwipeRowKey,
            onOpenSwipeRowChange = { openSwipeRowKey = it },
            onEnabledChange = { rule, enabled -> controller.setFetchInfoCatalogRuleEnabled(rule.index, enabled) },
            onEdit = { editRule = it },
            onDelete = { rule ->
                deleteConfirm = DeleteConfirmRequest(
                    title = "确认删除规则",
                    message = "确定删除规则“${rule.name.ifBlank { "未命名" }}”吗？",
                    onConfirm = { controller.deleteFetchInfoCatalogRule(rule.index) }
                )
            },
            onMove = { from, to -> controller.moveFetchInfoCatalogRule(from, to) },
            onAdd = { createCategory = FETCH_CATALOG_RULE_CATEGORY_PURIFY }
        )
    }

    createCategory?.let { category ->
        FetchCatalogRuleEditDialog(
            title = "新增规则·${fetchCatalogRuleCategoryLabel(category)}",
            initialCategory = category,
            initialName = "",
            initialSearch = "",
            initialReplacement = "",
            initialRegex = true,
            onDismiss = { createCategory = null },
            onConfirm = { c, name, search, replacement, regex ->
                controller.addFetchInfoCatalogRule(c, name, search, replacement, regex)
                createCategory = null
            }
        )
    }

    editRule?.let { rule ->
        FetchCatalogRuleEditDialog(
            title = "编辑规则·${fetchCatalogRuleCategoryLabel(rule.category)}",
            initialCategory = rule.category,
            initialName = rule.name,
            initialSearch = rule.search,
            initialReplacement = rule.replacement,
            initialRegex = rule.regex,
            onDismiss = { editRule = null },
            onConfirm = { c, name, search, replacement, regex ->
                controller.updateFetchInfoCatalogRule(rule.index, c, name, search, replacement, regex)
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

@Composable
private fun FetchCatalogRuleGroup(
    title: String,
    groupRules: List<FetchCatalogRuleItem>,
    sortMode: Boolean,
    openSwipeRowKey: Any?,
    onOpenSwipeRowChange: (Any?) -> Unit,
    onEnabledChange: (FetchCatalogRuleItem, Boolean) -> Unit,
    onEdit: (FetchCatalogRuleItem) -> Unit,
    onDelete: (FetchCatalogRuleItem) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$title：共 ${groupRules.size} 项规则",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onAdd, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = "新增$title 规则", modifier = Modifier.size(17.dp))
        }
    }
    if (groupRules.isEmpty()) {
        Text(
            text = "暂无规则",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        groupRules.forEachIndexed { positionInGroup, rule ->
            DraggableCompactRuleListRow(
                rowKey = "fetch-rule-${rule.index}",
                index = positionInGroup + 1,
                name = rule.name.ifBlank { "未命名" },
                showIndex = false,
                onEdit = { onEdit(rule) },
                onDelete = { onDelete(rule) },
                position = positionInGroup,
                itemCount = groupRules.size,
                reorderStepPx = 0f,
                displacedOffsetPx = 0f,
                onMove = { target -> groupRules.getOrNull(target)?.let { onMove(rule.index, it.index) } },
                onDragVisualChange = { _, _ -> },
                openSwipeRowKey = openSwipeRowKey,
                onOpenSwipeRowChange = onOpenSwipeRowChange,
                showDragHandle = false,
                sortMode = sortMode,
                leadingContent = {
                    FetchRuleEnableToggle(checked = rule.enabled, onCheckedChange = { enabled -> onEnabledChange(rule, enabled) })
                }
            )
        }
    }
}

@Composable
internal fun FetchRuleEnableToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(999.dp),
        color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.size(width = 38.dp, height = 20.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (checked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = if (checked) 0.dp else 1.dp,
                modifier = Modifier
                    .offset(x = if (checked) 20.dp else 2.dp)
                    .size(16.dp)
            ) {}
        }
    }
}

@Composable
internal fun FetchCatalogRuleEditDialog(
    title: String,
    initialCategory: String,
    initialName: String,
    initialSearch: String,
    initialReplacement: String,
    initialRegex: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (category: String, name: String, search: String, replacement: String, regex: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var regex by remember { mutableStateOf(initialRegex) }
    var search by remember { mutableStateOf(initialSearch) }
    var replacement by remember { mutableStateOf(initialReplacement) }
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
                .fixedDialogWidth(fraction = 0.82f, maxWidth = 320.dp)
                .heightIn(max = 470.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RuleCreateDialogHeader(title = title, onDismiss = onDismiss)
                ToolTextInputField(
                    label = "名称",
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth()
                )
                ToolTextInputField(
                    label = "匹配",
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth()
                )
                ToolTextInputField(
                    label = "替换为",
                    value = replacement,
                    onValueChange = { replacement = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "正则",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FetchRuleEnableToggle(checked = regex, onCheckedChange = { regex = it })
                }
                RuleCreateDialogActions(
                    onDismiss = onDismiss,
                    onConfirm = { onConfirm(initialCategory, name, search, replacement, regex) },
                    confirmEnabled = search.isNotBlank()
                )
            }
        }
    }
}
