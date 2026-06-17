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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private fun fetchCatalogRuleActionLabel(action: String): String {
    return if (action == FETCH_CATALOG_RULE_ACTION_DROP) "丢弃" else "替换"
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
internal fun FetchInfoCatalogRulePanel(
    controller: EditorController,
    modifier: Modifier = Modifier
) {
    val rules = controller.fetchInfoCatalogRuleItems()
    val chapterRules = rules.filter { it.category == FETCH_CATALOG_RULE_CATEGORY_CHAPTER }
    val purifyRules = rules.filter { it.category == FETCH_CATALOG_RULE_CATEGORY_PURIFY }
    var createCategory by remember { mutableStateOf<String?>(null) }
    var editRule by remember { mutableStateOf<FetchCatalogRuleItem?>(null) }
    var deleteConfirm by remember { mutableStateOf<DeleteConfirmRequest?>(null) }

    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.74f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FetchCatalogRuleGroup(
                title = "章节",
                groupRules = chapterRules,
                allRules = rules,
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
                allRules = rules,
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
    }

    createCategory?.let { category ->
        FetchCatalogRuleEditDialog(
            title = "新增规则",
            initialCategory = category,
            initialName = "",
            initialSearch = "",
            initialReplacement = "",
            initialRegex = true,
            initialAction = FETCH_CATALOG_RULE_ACTION_REPLACE,
            onDismiss = { createCategory = null },
            onConfirm = { c, name, search, replacement, regex, action ->
                controller.addFetchInfoCatalogRule(c, name, search, replacement, regex, action)
                createCategory = null
            }
        )
    }

    editRule?.let { rule ->
        FetchCatalogRuleEditDialog(
            title = "编辑规则",
            initialCategory = rule.category,
            initialName = rule.name,
            initialSearch = rule.search,
            initialReplacement = rule.replacement,
            initialRegex = rule.regex,
            initialAction = rule.action,
            onDismiss = { editRule = null },
            onConfirm = { c, name, search, replacement, regex, action ->
                controller.updateFetchInfoCatalogRule(rule.index, c, name, search, replacement, regex, action)
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
    allRules: List<FetchCatalogRuleItem>,
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
            val prevSame = groupRules.getOrNull(positionInGroup - 1)
            val nextSame = groupRules.getOrNull(positionInGroup + 1)
            FetchCatalogRuleRow(
                rule = rule,
                canMoveUp = prevSame != null,
                canMoveDown = nextSame != null,
                onEnabledChange = { enabled -> onEnabledChange(rule, enabled) },
                onEdit = { onEdit(rule) },
                onDelete = { onDelete(rule) },
                onMoveUp = { prevSame?.let { onMove(rule.index, it.index) } },
                onMoveDown = { nextSame?.let { onMove(rule.index, it.index) } }
            )
        }
    }
}

@Composable
private fun FetchCatalogRuleRow(
    rule: FetchCatalogRuleItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FetchRuleEnableToggle(checked = rule.enabled, onCheckedChange = onEnabledChange)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name.ifBlank { "未命名" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${fetchCatalogRuleActionLabel(rule.action)}${if (rule.regex) " · 正则" else ""} · ${rule.search}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移", modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移", modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑", modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun FetchRuleEnableToggle(
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
private fun FetchCatalogRuleEditDialog(
    title: String,
    initialCategory: String,
    initialName: String,
    initialSearch: String,
    initialReplacement: String,
    initialRegex: Boolean,
    initialAction: String,
    onDismiss: () -> Unit,
    onConfirm: (category: String, name: String, search: String, replacement: String, regex: Boolean, action: String) -> Unit
) {
    var category by remember { mutableStateOf(initialCategory) }
    var name by remember { mutableStateOf(initialName) }
    var action by remember { mutableStateOf(initialAction) }
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
                .fixedDialogWidth(fraction = 0.88f, maxWidth = 380.dp)
                .heightIn(max = 470.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RuleCreateDialogHeader(title = title, onDismiss = onDismiss)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ToolTextInputField(
                        label = "名称",
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.weight(1f)
                    )
                    ToolDropdownField(
                        label = "分类",
                        value = category,
                        options = listOf(
                            FETCH_CATALOG_RULE_CATEGORY_CHAPTER to "章节",
                            FETCH_CATALOG_RULE_CATEGORY_PURIFY to "净化"
                        ),
                        onSelect = { category = it },
                        modifier = Modifier.width(110.dp)
                    )
                }
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactDialogSwitchField(
                        label = "正则",
                        checked = regex,
                        onCheckedChange = { regex = it },
                        modifier = Modifier.weight(1f)
                    )
                    ToolDropdownField(
                        label = "动作",
                        value = fetchCatalogRuleActionLabel(action),
                        options = listOf(
                            FETCH_CATALOG_RULE_ACTION_REPLACE to "替换",
                            FETCH_CATALOG_RULE_ACTION_DROP to "丢弃"
                        ),
                        onSelect = { action = it },
                        modifier = Modifier.width(110.dp)
                    )
                }
                RuleCreateDialogActions(
                    onDismiss = onDismiss,
                    onConfirm = { onConfirm(category, name, search, replacement, regex, action) },
                    confirmEnabled = search.isNotBlank()
                )
            }
        }
    }
}
