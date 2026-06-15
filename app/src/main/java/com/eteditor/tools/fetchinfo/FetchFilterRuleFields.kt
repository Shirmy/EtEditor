package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import org.json.JSONArray
import org.json.JSONObject

private data class FetchFilterUiRule(
    val name: String,
    val search: String,
    val replacement: String,
    val regex: Boolean
)

@Composable
fun FetchFilterRulesField(
    label: String,
    rawValue: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rules = remember(rawValue) { parseFetchFilterUiRules(rawValue) }
    var editingIndex by remember(rawValue) { mutableStateOf<Int?>(null) }
    var showEditor by remember(rawValue) { mutableStateOf(false) }
    val editingRule = editingIndex?.let { index -> rules.getOrNull(index) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    editingIndex = null
                    showEditor = true
                },
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "新建$label", modifier = Modifier.size(18.dp))
            }
        }
        rules.forEachIndexed { index, rule ->
            FetchFilterRuleRow(
                index = index,
                rule = rule,
                onEdit = {
                    editingIndex = index
                    showEditor = true
                },
                onDelete = {
                    onValueChange(serializeFetchFilterUiRules(rules.filterIndexed { rowIndex, _ -> rowIndex != index }))
                }
            )
        }
    }

    if (showEditor) {
        FetchFilterRuleEditorDialog(
            title = if (editingIndex == null) "新建$label" else "编辑$label",
            initialRule = editingRule,
            defaultName = "规则${(editingIndex ?: rules.size) + 1}",
            onDismiss = { showEditor = false },
            onConfirm = { rule ->
                val nextRules = rules.toMutableList()
                val index = editingIndex
                if (index != null && index in nextRules.indices) {
                    nextRules[index] = rule
                } else {
                    nextRules += rule
                }
                onValueChange(serializeFetchFilterUiRules(nextRules))
                showEditor = false
            }
        )
    }
}

@Composable
private fun FetchFilterRuleRow(
    index: Int,
    rule: FetchFilterUiRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var deleteConfirm by remember(index, rule.name) { mutableStateOf<DeleteConfirmRequest?>(null) }
    Surface(
        onClick = onEdit,
        shape = RowShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 5.dp, end = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.width(22.dp)
            )
            Text(
                text = rule.name.ifBlank { "规则${index + 1}" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑规则", modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = {
                    deleteConfirm = DeleteConfirmRequest(
                        title = "确认删除规则",
                        message = "确定删除过滤规则“${rule.name.ifBlank { "规则${index + 1}" }}”吗？",
                        onConfirm = onDelete
                    )
                },
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除规则", modifier = Modifier.size(16.dp))
            }
        }
    }
    deleteConfirm?.let { request ->
        DeleteConfirmDialog(
            request = request,
            onDismiss = { deleteConfirm = null }
        )
    }
}

@Composable
private fun FetchFilterRuleEditorDialog(
    title: String,
    initialRule: FetchFilterUiRule?,
    defaultName: String,
    onDismiss: () -> Unit,
    onConfirm: (FetchFilterUiRule) -> Unit
) {
    var name by remember(initialRule) { mutableStateOf(initialRule?.name.orEmpty()) }
    var search by remember(initialRule) { mutableStateOf(initialRule?.search.orEmpty()) }
    var replacement by remember(initialRule) { mutableStateOf(initialRule?.replacement.orEmpty()) }
    var regex by remember(initialRule) { mutableStateOf(initialRule?.regex ?: false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            modifier = Modifier
                .fixedDialogWidth(fraction = 0.78f, maxWidth = 340.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(17.dp))
                    }
                }
                CompactDialogTextInputField(
                    label = "名称",
                    value = name,
                    onValueChange = { name = it },
                    height = 32.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                CompactDialogTextInputField(
                    label = "搜索",
                    value = search,
                    onValueChange = { search = it },
                    height = 32.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                CompactDialogTextInputField(
                    label = "替换",
                    value = replacement,
                    onValueChange = { replacement = it },
                    height = 32.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    CompactDialogSwitchField(
                        label = "正则",
                        checked = regex,
                        onCheckedChange = { regex = it },
                        modifier = Modifier.width(86.dp)
                    )
                }
                Button(
                    enabled = search.isNotBlank(),
                    onClick = {
                        onConfirm(
                            FetchFilterUiRule(
                                name = name.trim().ifBlank { defaultName },
                                search = search,
                                replacement = replacement,
                                regex = regex
                            )
                        )
                    },
                    shape = ControlShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                ) {
                    Text("保存")
                }
            }
        }
    }
}

private fun parseFetchFilterUiRules(rawValue: String): List<FetchFilterUiRule> {
    val text = rawValue.trim()
    if (text.isBlank()) return emptyList()
    if (text.startsWith("[")) {
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                FetchFilterUiRule(
                    name = json.optString("name").ifBlank { "规则${index + 1}" },
                    search = json.optString("search"),
                    replacement = json.optString("replacement"),
                    regex = json.optBoolean("regex", false)
                ).takeIf { it.search.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }
    return text.lines().mapIndexedNotNull { index, line ->
        val trimmed = line.trim()
        val regex = when {
            trimmed.startsWith("replaceRegex:", ignoreCase = true) -> true
            trimmed.startsWith("replace:", ignoreCase = true) -> false
            else -> return@mapIndexedNotNull null
        }
        val body = trimmed.substringAfter(':')
        val separator = body.indexOf("=>")
        if (separator < 0) return@mapIndexedNotNull null
        val search = body.substring(0, separator)
        if (search.isBlank()) return@mapIndexedNotNull null
        FetchFilterUiRule(
            name = "规则${index + 1}",
            search = search,
            replacement = body.substring(separator + 2),
            regex = regex
        )
    }
}

private fun serializeFetchFilterUiRules(rules: List<FetchFilterUiRule>): String {
    if (rules.isEmpty()) return ""
    val array = JSONArray()
    rules.forEach { rule ->
        array.put(
            JSONObject()
                .put("name", rule.name)
                .put("search", rule.search)
                .put("replacement", rule.replacement)
                .put("regex", rule.regex)
        )
    }
    return array.toString()
}
