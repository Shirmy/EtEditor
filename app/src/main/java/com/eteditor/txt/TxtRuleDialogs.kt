package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eteditor.core.DocumentKind

@Composable
internal fun TxtChapterRulesDialog(
    controller: EditorController,
    onDismiss: () -> Unit
) {
    var showCreateChapterRuleDialog by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(false) }
    fun closeDialog() {
        onDismiss()
        controller.scheduleDeferredTxtChapterRuleRefresh()
    }
    Dialog(
        onDismissRequest = ::closeDialog,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fixedDialogWidth(fraction = 0.78f, maxWidth = 320.dp)
                .heightIn(max = 460.dp)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RuleDialogIconButton(
                            icon = Icons.Outlined.Add,
                            contentDescription = "新建规则",
                            onClick = { showCreateChapterRuleDialog = true }
                        )
                        RuleDialogIconButton(
                            icon = Icons.Outlined.SwapVert,
                            contentDescription = if (sortMode) "退出排序" else "排序",
                            selected = sortMode,
                            onClick = { sortMode = !sortMode }
                        )
                        RuleDialogIconButton(
                            icon = Icons.Outlined.Close,
                            contentDescription = "关闭",
                            onClick = ::closeDialog
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    TxtChapterRecognitionPanel(
                        controller = controller,
                        sortMode = sortMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    if (showCreateChapterRuleDialog) {
        TxtChapterRuleCreateDialog(
            controller = controller,
            onDismiss = { showCreateChapterRuleDialog = false }
        )
    }
}

@Composable
private fun TxtChapterRuleCreateDialog(
    controller: EditorController,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    fun saveRule() {
        val nextName = name
        val nextPattern = pattern
        val nextReplacement = replacement
        if (controller.txtMoveChapterSyncPending) {
            scope.launchAfterTxtMoveChapterSync(controller, "新增目录规则") {
                if (controller.addTxtChapterRule(name = nextName, pattern = nextPattern, replacement = nextReplacement, deferRefresh = true)) {
                    onDismiss()
                } else {
                    errorMessage = controller.statusMessage.ifBlank { "正则错误，请检查表达式" }
                }
            }
        } else {
            if (controller.addTxtChapterRule(name = nextName, pattern = nextPattern, replacement = nextReplacement, deferRefresh = true)) {
                onDismiss()
            } else {
                errorMessage = controller.statusMessage.ifBlank { "正则错误，请检查表达式" }
            }
        }
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
                .fixedDialogWidth(fraction = 0.88f, maxWidth = 360.dp)
                .heightIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RuleCreateDialogHeader(title = "新建目录规则", onDismiss = onDismiss)
                ToolTextInputField(
                    label = "名称",
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth()
                )
                ToolTextInputField(
                    label = "表达式",
                    value = pattern,
                    onValueChange = {
                        pattern = it
                        errorMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                ToolTextInputField(
                    label = "映射为",
                    value = replacement,
                    onValueChange = { replacement = it },
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                RuleCreateDialogActions(
                    onDismiss = onDismiss,
                    onConfirm = { saveRule() },
                    confirmEnabled = pattern.isNotBlank()
                )
            }
        }
    }
}

@Composable
fun TxtPurifyRulesDialog(
    controller: EditorController,
    onDismiss: () -> Unit
) {
    var showCreatePurifyRuleDialog by remember { mutableStateOf(false) }
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
            modifier = Modifier
                .fixedDialogWidth(fraction = 0.90f, maxWidth = 380.dp)
                .heightIn(max = 460.dp)
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
                        text = "净化规则",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RuleDialogIconButton(
                            icon = Icons.Outlined.Add,
                            contentDescription = "新建规则",
                            onClick = { showCreatePurifyRuleDialog = true }
                        )
                        RuleDialogIconButton(
                            icon = Icons.Outlined.SwapVert,
                            contentDescription = if (sortMode) "退出排序" else "排序",
                            selected = sortMode,
                            onClick = { sortMode = !sortMode }
                        )
                        RuleDialogIconButton(
                            icon = Icons.Outlined.Close,
                            contentDescription = "关闭",
                            onClick = onDismiss
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    TxtPurifyTextPanel(
                        controller = controller,
                        sortMode = sortMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Button(
                    onClick = {
                        if (controller.applyTxtPurifyRules()) {
                            onDismiss()
                        }
                    },
                    enabled = controller.kind == DocumentKind.Txt && !controller.busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ControlShape,
                    contentPadding = CompactButtonPadding
                ) {
                    Icon(Icons.Outlined.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("应用规则")
                }
            }
        }
    }
    if (showCreatePurifyRuleDialog) {
        TxtPurifyRuleCreateDialog(
            onDismiss = { showCreatePurifyRuleDialog = false },
            onConfirm = { target, name, regex, pattern, replacement ->
                controller.addTxtPurifyRule(
                    target = target,
                    name = name,
                    regex = regex,
                    pattern = pattern,
                    replacement = replacement
                ).also { saved ->
                    if (saved) showCreatePurifyRuleDialog = false
                }
            }
        )
    }
}

@Composable
private fun TxtPurifyRuleCreateDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean, String, String) -> Boolean
) {
    var target by remember { mutableStateOf(TXT_PURIFY_TARGET_BODY) }
    var name by remember { mutableStateOf("") }
    var regex by remember { mutableStateOf(true) }
    var pattern by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
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
                .heightIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RuleCreateDialogHeader(title = "新建净化规则", onDismiss = onDismiss)
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
                        label = "分组",
                        value = txtPurifyTargetLabel(target),
                        options = listOf(
                            TXT_PURIFY_TARGET_BODY to "正文",
                            TXT_PURIFY_TARGET_CATALOG to "目录"
                        ),
                        onSelect = { target = it },
                        modifier = Modifier.width(116.dp)
                    )
                }
                ToolTextInputField(
                    label = "匹配",
                    value = pattern,
                    onValueChange = {
                        pattern = it
                        errorMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                ToolTextInputField(
                    label = "替换为",
                    value = replacement,
                    onValueChange = { replacement = it },
                    modifier = Modifier.fillMaxWidth()
                )
                CompactDialogSwitchField(
                    label = "正则",
                    checked = regex,
                    onCheckedChange = {
                        regex = it
                        errorMessage = ""
                    },
                    modifier = Modifier.widthIn(max = 120.dp)
                )
                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                RuleCreateDialogActions(
                    onDismiss = onDismiss,
                    onConfirm = {
                        if (!onConfirm(target, name, regex, pattern, replacement)) {
                            errorMessage = "正则错误，请检查匹配表达式"
                        }
                    },
                    confirmEnabled = pattern.isNotBlank()
                )
            }
        }
    }
}
