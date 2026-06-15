package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import com.eteditor.core.DocumentKind
import org.json.JSONArray
import org.json.JSONObject

private data class TextReplaceBatchUiRule(
    val name: String,
    val search: String,
    val replacement: String,
    val textOnly: Boolean = false,
    val regex: Boolean = false
)

fun textReplaceModeForUi(mode: String, batchSource: String): String {
    return when {
        mode == TEXT_REPLACE_MODE_BATCH &&
            batchSource == TEXT_REPLACE_BATCH_FILE -> TEXT_REPLACE_MODE_REPLACEMENT
        mode == TEXT_REPLACE_MODE_BATCH -> TEXT_REPLACE_MODE_BATCH
        mode == TEXT_REPLACE_MODE_REPLACEMENT -> TEXT_REPLACE_MODE_REPLACEMENT
        else -> TEXT_REPLACE_MODE_SINGLE
    }
}

@Composable
private fun TextReplaceModeSegmentedField(
    mode: String,
    onModeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        TEXT_REPLACE_MODE_SINGLE to "单条",
        TEXT_REPLACE_MODE_BATCH to "批量",
        TEXT_REPLACE_MODE_REPLACEMENT to "静读专用"
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "替换模式",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) ->
                val selected = mode == value
                Surface(
                    onClick = { if (!selected) onModeChange(value) },
                    shape = ControlShape,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                    contentColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = if (value == TEXT_REPLACE_MODE_REPLACEMENT) {
                                MaterialTheme.typography.bodySmall
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TextReplaceParameterFields(
    controller: EditorController,
    toolId: String,
    valueFor: (ToolParameterDefinition) -> String,
    onValueChange: (ToolParameterDefinition, String) -> Unit,
) {
    val basicParameters = controller.toolParameterDefinitions(toolId)
    val parameterByKey = basicParameters.associateBy { it.key }
    @Composable
    fun textOnlyField(parameter: ToolParameterDefinition) {
        val effectiveValue = valueFor(parameter).ifBlank { parameter.defaultValue }
        ToolSwitchField(
            label = if (effectiveValue == TEXT_REPLACE_TARGET_SOURCE) "源码" else "文本",
            checked = effectiveValue == TEXT_REPLACE_TARGET_SOURCE,
            onCheckedChange = { checked ->
                onValueChange(
                    parameter,
                    if (checked) TEXT_REPLACE_TARGET_SOURCE else TEXT_REPLACE_TARGET_VISIBLE
                )
            }
        )
    }
    @Composable
    fun scopeField(parameter: ToolParameterDefinition, currentScope: String) {
        ToolSwitchField(
            label = if (currentScope == TOOL_SCOPE_CURRENT) "本章" else "全文",
            checked = currentScope == TOOL_SCOPE_CURRENT,
            onCheckedChange = { checked ->
                onValueChange(
                    parameter,
                    if (checked) TOOL_SCOPE_CURRENT else TOOL_SCOPE_ALL
                )
            }
        )
    }
    @Composable
    fun regexField(parameter: ToolParameterDefinition) {
        val effectiveValue = valueFor(parameter).ifBlank { parameter.defaultValue }
        ToolSwitchField(
            label = "正则",
            checked = effectiveValue == BOOL_TRUE,
            onCheckedChange = { checked ->
                onValueChange(parameter, if (checked) BOOL_TRUE else BOOL_FALSE)
            }
        )
    }
    if (controller.kind == DocumentKind.Txt) {
        val scopeParameter = parameterByKey[TEXT_REPLACE_PARAM_SCOPE]
        val findParameter = parameterByKey[TEXT_REPLACE_PARAM_FIND]
        val replaceParameter = parameterByKey[TEXT_REPLACE_PARAM_REPLACE]
        val targetParameter = parameterByKey[TEXT_REPLACE_PARAM_TARGET]
        val regexParameter = parameterByKey[TEXT_REPLACE_PARAM_FIND_REGEX]

        if (findParameter != null) {
            ToolTextInputField(
                label = findParameter.label,
                value = valueFor(findParameter),
                onValueChange = { value -> onValueChange(findParameter, value) },
                modifier = Modifier.fillMaxWidth(),
                showLineBreakMarks = true
            )
        }
        replaceParameter?.let { parameter ->
            ToolParameterField(
                parameter = parameter,
                value = valueFor(parameter),
                onValueChange = { value -> onValueChange(parameter, value) },
                showLineBreakMarks = true
            )
        }
        targetParameter?.let { parameter ->
            textOnlyField(parameter)
        }
        scopeParameter?.let { parameter ->
            val currentScope = cleanTxtTextReplaceScopeValue(valueFor(parameter).ifBlank { parameter.defaultValue })
            scopeField(parameter, currentScope)
        }
        regexParameter?.let { parameter ->
            regexField(parameter)
        }
        parameterByKey[TEXT_REPLACE_PARAM_PREVIEW]?.let { parameter ->
            ToolParameterField(
                parameter = parameter,
                value = valueFor(parameter),
                onValueChange = { value -> onValueChange(parameter, value) }
            )
        }
        return
    }
    val modeParameter = parameterByKey.getValue(TEXT_REPLACE_PARAM_MODE)
    val batchSourceParameter = parameterByKey.getValue(TEXT_REPLACE_PARAM_BATCH_SOURCE)
    val scopeParameter = parameterByKey[TEXT_REPLACE_PARAM_SCOPE]
    val rawMode = valueFor(modeParameter)
        .takeIf { value -> modeParameter.options.any { it.first == value } }
        ?: modeParameter.defaultValue
    val batchSource = valueFor(batchSourceParameter)
        .takeIf { value -> batchSourceParameter.options.any { it.first == value } }
        ?: batchSourceParameter.defaultValue
    val mode = textReplaceModeForUi(rawMode, batchSource)
    val selectedScope = scopeParameter
        ?.let { parameter ->
            valueFor(parameter).takeIf { value ->
                value == TEXT_REPLACE_SCOPE_INTRO || parameter.options.any { it.first == value }
            }
        }
        ?: scopeParameter?.defaultValue
        ?: TOOL_SCOPE_ALL

    @Composable
    fun field(key: String, showLineBreakMarks: Boolean = false) {
        parameterByKey[key]?.let { parameter ->
            ToolParameterField(
                parameter = parameter,
                value = valueFor(parameter),
                onValueChange = { value -> onValueChange(parameter, value) },
                showLineBreakMarks = showLineBreakMarks
            )
        }
    }

    parameterByKey[TEXT_REPLACE_PARAM_MODE]?.let { parameter ->
        TextReplaceModeSegmentedField(
            mode = mode,
            onModeChange = { value -> onValueChange(parameter, value) },
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (mode == TEXT_REPLACE_MODE_BATCH) {
        parameterByKey[TEXT_REPLACE_PARAM_BATCH_TEXT]?.let { parameter ->
            TextReplaceBatchRulesField(
                label = "${parameter.label}（查找 -> 替换为）",
                rawValue = valueFor(parameter),
                onValueChange = { value -> onValueChange(parameter, value) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        scopeParameter?.let { parameter ->
            ToolSwitchField(
                label = if (selectedScope == TEXT_REPLACE_SCOPE_INTRO) "简介" else "全文",
                checked = selectedScope == TEXT_REPLACE_SCOPE_INTRO,
                onCheckedChange = { checked ->
                    onValueChange(
                        parameter,
                        if (checked) TEXT_REPLACE_SCOPE_INTRO else TOOL_SCOPE_ALL
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        field(TEXT_REPLACE_PARAM_PREVIEW)
    } else if (mode == TEXT_REPLACE_MODE_REPLACEMENT) {
    } else {
        parameterByKey[TEXT_REPLACE_PARAM_FIND]?.let { parameter ->
            ToolTextInputField(
                value = valueFor(parameter),
                onValueChange = { value -> onValueChange(parameter, value) },
                label = parameter.label,
                modifier = Modifier.fillMaxWidth(),
                showLineBreakMarks = true
            )
        }
        field(TEXT_REPLACE_PARAM_REPLACE, showLineBreakMarks = true)
        scopeParameter?.let { parameter ->
            scopeField(parameter, selectedScope)
        }
        parameterByKey[TEXT_REPLACE_PARAM_TARGET]?.let { parameter ->
            textOnlyField(parameter)
        }
        parameterByKey[TEXT_REPLACE_PARAM_FIND_REGEX]?.let { parameter ->
            regexField(parameter)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            )
        }
        field(TEXT_REPLACE_PARAM_PREVIEW)
    }
}

@Composable
private fun TextReplaceBatchRulesField(
    label: String,
    rawValue: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rules = remember(rawValue) { parseTextReplaceBatchUiRules(rawValue) }
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
                Icon(Icons.Outlined.Add, contentDescription = "新增批量替换", modifier = Modifier.size(18.dp))
            }
        }
        if (rules.isEmpty()) {
            Text(
                text = "暂无批量规则",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            rules.forEachIndexed { index, rule ->
                TextReplaceBatchRuleRow(
                    index = index,
                    rule = rule,
                    onEdit = {
                        editingIndex = index
                        showEditor = true
                    },
                    onDelete = {
                        onValueChange(serializeTextReplaceBatchUiRules(rules.filterIndexed { rowIndex, _ -> rowIndex != index }))
                    }
                )
            }
        }
    }

    if (showEditor) {
        TextReplaceBatchRuleEditorDialog(
            title = if (editingIndex == null) "新增批量替换" else "编辑批量替换",
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
                onValueChange(serializeTextReplaceBatchUiRules(nextRules))
                showEditor = false
            }
        )
    }
}

@Composable
private fun TextReplaceBatchRuleRow(
    index: Int,
    rule: TextReplaceBatchUiRule,
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
                Icon(Icons.Outlined.Edit, contentDescription = "编辑批量替换", modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = {
                    deleteConfirm = DeleteConfirmRequest(
                        title = "确认删除规则",
                        message = "确定删除批量替换“${rule.name.ifBlank { "规则${index + 1}" }}”吗？",
                        onConfirm = onDelete
                    )
                },
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除批量替换", modifier = Modifier.size(16.dp))
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
private fun TextReplaceBatchRuleEditorDialog(
    title: String,
    initialRule: TextReplaceBatchUiRule?,
    defaultName: String,
    onDismiss: () -> Unit,
    onConfirm: (TextReplaceBatchUiRule) -> Unit
) {
    var name by remember(initialRule) { mutableStateOf(initialRule?.name.orEmpty()) }
    var search by remember(initialRule) { mutableStateOf(initialRule?.search.orEmpty()) }
    var replacement by remember(initialRule) { mutableStateOf(initialRule?.replacement.orEmpty()) }
    var textOnly by remember(initialRule) { mutableStateOf(initialRule?.textOnly ?: false) }
    var regex by remember(initialRule) { mutableStateOf(initialRule?.regex ?: false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            modifier = Modifier.fixedDialogWidth(fraction = 0.66f, maxWidth = 300.dp)
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
                    label = "查找",
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
                ToolSwitchField(
                    label = if (textOnly) "正文" else "源码",
                    checked = textOnly,
                    onCheckedChange = { textOnly = it },
                    modifier = Modifier.fillMaxWidth()
                )
                ToolSwitchField(
                    label = "正则",
                    checked = regex,
                    onCheckedChange = { regex = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = search.isNotEmpty(),
                    onClick = {
                        onConfirm(
                            TextReplaceBatchUiRule(
                                name = name.trim().ifBlank { defaultName },
                                search = search,
                                replacement = replacement,
                                textOnly = textOnly,
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

private fun parseTextReplaceBatchUiRules(rawValue: String): List<TextReplaceBatchUiRule> {
    val text = rawValue.removePrefix("\uFEFF")
    if (text.isBlank()) return emptyList()
    if (text.trimStart().startsWith("[")) {
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                TextReplaceBatchUiRule(
                    name = json.optString("name").ifBlank { "规则${index + 1}" },
                    search = json.optString("search"),
                    replacement = json.optString("replacement"),
                    textOnly = json.optBoolean("textOnly", false),
                    regex = json.optBoolean("regex", false)
                ).takeIf { it.search.isNotEmpty() }
            }
        }.getOrDefault(emptyList())
    }
    return text
        .split('\n')
        .map { it.removeSuffix("\r") }
        .mapIndexedNotNull { index, line ->
            if (line.isBlank()) return@mapIndexedNotNull null
            val separator = line.indexOf("=>")
            if (separator < 0) return@mapIndexedNotNull null
            val search = line.substring(0, separator)
            if (search.isEmpty()) return@mapIndexedNotNull null
            TextReplaceBatchUiRule(
                name = "规则${index + 1}",
                search = search,
                replacement = line.substring(separator + 2)
            )
        }
}

private fun serializeTextReplaceBatchUiRules(rules: List<TextReplaceBatchUiRule>): String {
    if (rules.isEmpty()) return ""
    val array = JSONArray()
    rules.filter { it.search.isNotEmpty() }.forEach { rule ->
        array.put(
            JSONObject()
                .put("name", rule.name)
                .put("search", rule.search)
                .put("replacement", rule.replacement)
                .put("textOnly", rule.textOnly)
                .put("regex", rule.regex)
        )
    }
    return array.toString()
}
