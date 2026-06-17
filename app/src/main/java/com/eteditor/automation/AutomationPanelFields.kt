package com.eteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private enum class AutomationViewMode {
    List,
    Edit,
    Run
}

@Composable
internal fun AutomationPanel(
    controller: EditorController,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    resetKey: Int = 0,
    documentSessionKey: Int = 0,
    onPickTextReplaceRuleFile: TextReplaceRuleFilePicker = { _ -> }
) {
    var mode by remember(documentSessionKey, resetKey) { mutableStateOf(AutomationViewMode.List) }
    var pendingRunChainId by remember(documentSessionKey, resetKey) { mutableStateOf<String?>(null) }
    var showGroupManager by remember(documentSessionKey, resetKey) { mutableStateOf(false) }
    val chain = controller.selectedAutomationChain

    fun openRun(chainId: String) {
        controller.selectAutomationChain(chainId)
        controller.clearAutomationLog()
        pendingRunChainId = chainId
        mode = AutomationViewMode.Run
    }

    LaunchedEffect(mode, pendingRunChainId, chain?.id) {
        val runChainId = pendingRunChainId ?: return@LaunchedEffect
        if (mode != AutomationViewMode.Run || chain?.id != runChainId) return@LaunchedEffect
        delay(280)
        if (mode == AutomationViewMode.Run && controller.selectedAutomationChain?.id == runChainId) {
            controller.runSelectedAutomationChain()
            pendingRunChainId = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(if (compact) 0.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        if (mode == AutomationViewMode.List) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WorkspaceHeaderHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "执行",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showGroupManager = true },
                        enabled = !controller.busy,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = "分组管理", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            controller.createAutomationChain()
                            mode = AutomationViewMode.Edit
                        },
                        enabled = !controller.busy,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "新建")
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
        }

        when (mode) {
            AutomationViewMode.List -> {
                AutomationChainList(
                    controller = controller,
                    modifier = Modifier.weight(1f),
                    onRun = { chainId -> openRun(chainId) },
                    onEdit = { chainId ->
                        controller.selectAutomationChain(chainId)
                        mode = AutomationViewMode.Edit
                    },
                    onDelete = { chainId ->
                        controller.removeAutomationChain(chainId)
                    },
                    onCreate = {
                        controller.createAutomationChain()
                        mode = AutomationViewMode.Edit
                    }
                )
            }
            AutomationViewMode.Edit -> {
                if (chain != null) {
                    AutomationEditView(
                        controller = controller,
                        selectedChain = chain,
                        modifier = Modifier.weight(1f),
                        onBack = { mode = AutomationViewMode.List },
                        onPickTextReplaceRuleFile = onPickTextReplaceRuleFile
                    )
                } else {
                    AutomationChainList(
                        controller = controller,
                        modifier = Modifier.weight(1f),
                        onRun = { chainId -> openRun(chainId) },
                        onEdit = { chainId ->
                            controller.selectAutomationChain(chainId)
                            mode = AutomationViewMode.Edit
                        },
                        onDelete = { chainId ->
                            controller.removeAutomationChain(chainId)
                        },
                        onCreate = {
                            controller.createAutomationChain()
                            mode = AutomationViewMode.Edit
                        }
                    )
                }
            }
            AutomationViewMode.Run -> {
                if (chain != null) {
                    AutomationRunView(
                        controller = controller,
                        selectedChain = chain,
                        modifier = Modifier.weight(1f),
                        onBack = { mode = AutomationViewMode.List },
                        onPickTextReplaceRuleFile = onPickTextReplaceRuleFile
                    )
                } else {
                    AutomationChainList(
                        controller = controller,
                        modifier = Modifier.weight(1f),
                        onRun = { chainId -> openRun(chainId) },
                        onEdit = { chainId ->
                            controller.selectAutomationChain(chainId)
                            mode = AutomationViewMode.Edit
                        },
                        onDelete = { chainId ->
                            controller.removeAutomationChain(chainId)
                        },
                        onCreate = {
                            controller.createAutomationChain()
                            mode = AutomationViewMode.Edit
                        }
                    )
                }
            }
        }
    }
    if (showGroupManager) {
        AutomationGroupManagerDialog(
            controller = controller,
            onDismiss = { showGroupManager = false }
        )
    }
}

@Composable
private fun AutomationEditView(
    controller: EditorController,
    selectedChain: AutomationChain,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onPickTextReplaceRuleFile: TextReplaceRuleFilePicker
) {
    var editingPresetId by remember(selectedChain.id) { mutableStateOf<String?>(null) }
    var editingStepId by remember(selectedChain.id) { mutableStateOf<String?>(null) }
    var configuringFunctionToolId by remember(selectedChain.id) { mutableStateOf<String?>(null) }
    var showPresetPicker by remember(selectedChain.id) { mutableStateOf(false) }
    var chainNameDraft by remember(selectedChain.id) { mutableStateOf(selectedChain.name) }
    var chainGroupDraft by remember(selectedChain.id) { mutableStateOf(selectedChain.group) }
    var chainNameError by remember(selectedChain.id) { mutableStateOf(false) }
    val isDraftChain = controller.creatingAutomationChainDraft
    fun closeEditor() {
        if (isDraftChain) controller.discardAutomationChainDraft()
        onBack()
    }
    fun saveAndCloseEditor() {
        val cleanName = chainNameDraft.trim()
        if (cleanName.isBlank()) {
            chainNameError = true
            return
        }
        controller.renameAutomationChain(cleanName)
        controller.updateAutomationChainGroup(chainGroupDraft)
        if (!isDraftChain || controller.saveAutomationChainDraft()) {
            onBack()
        }
    }
    if (editingPresetId != null) {
        ToolEditorPanel(
            controller = controller,
            modifier = modifier,
            onBack = {
                if (controller.editingToolDraft) controller.discardEditorToolDraft()
                editingPresetId = null
            },
            onSaveDraft = {
                if (controller.saveEditorToolDraft()) {
                    editingPresetId = null
                }
            },
            onPickTextReplaceRuleFile = onPickTextReplaceRuleFile
        )
        return
    }
    val editingStep = editingStepId?.let { stepId -> selectedChain.steps.firstOrNull { it.id == stepId } }
    if (editingStep != null) {
        AutomationStepEditor(
            controller = controller,
            step = editingStep,
            modifier = modifier,
            onBack = { editingStepId = null },
            onPickTextReplaceRuleFile = onPickTextReplaceRuleFile
        )
        return
    }
    val configuringFunctionTool = configuringFunctionToolId
    if (configuringFunctionTool != null) {
        AutomationFunctionDetail(
            controller = controller,
            toolId = configuringFunctionTool,
            modifier = modifier,
            onBack = { configuringFunctionToolId = null },
            onAdded = { configuringFunctionToolId = null },
            onPickTextReplaceRuleFile = onPickTextReplaceRuleFile
        )
        return
    }
    val editScrollState = rememberScrollState()
    var addStepExpanded by remember(selectedChain.id) { mutableStateOf(false) }
    val groupOptions = remember(controller.automationChainGroups, selectedChain.group) {
        (listOf("" to "未分组") +
            (controller.automationChainGroupOptions() + selectedChain.group)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .map { group -> group to group })
            .distinctBy { it.first }
    }
    val groupLabel = groupOptions.firstOrNull { it.first == chainGroupDraft }?.second ?: "未分组"
    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(editScrollState)
            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
            .padding(end = 10.dp, bottom = 52.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WorkspaceHeaderHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = ::closeEditor,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "返回", modifier = Modifier.size(20.dp))
                }
                Text(
                    text = "编辑执行中",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "执行链名",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ToolTextInputField(
                        value = chainNameDraft,
                        onValueChange = { value ->
                            chainNameDraft = value
                            if (value.trim().isNotEmpty()) {
                                chainNameError = false
                            }
                        },
                        label = "",
                        height = 42.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(
                    modifier = Modifier.width(104.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "分组",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    PanelDropdownField(
                        value = groupLabel,
                        options = groupOptions,
                        onSelect = { value -> chainGroupDraft = value },
                        height = 42.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AutomationStepCreatorButton(
                    expanded = addStepExpanded,
                    onToggle = { addStepExpanded = !addStepExpanded },
                    modifier = Modifier.size(42.dp)
                )
            }
            if (chainNameError) {
                Text(
                    text = "请输入执行链",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (addStepExpanded) {
                AutomationStepFunctionGrid(
                    controller = controller,
                    modifier = Modifier.fillMaxWidth(),
                    onAddFunction = { toolId ->
                        controller.resetBuiltInToolState(toolId)
                        configuringFunctionToolId = toolId
                    }
                )
            }
        }
        AutomationStepList(
            controller = controller,
            steps = selectedChain.steps,
            modifier = Modifier.fillMaxWidth(),
            onEditStep = { stepId ->
                val step = selectedChain.steps.firstOrNull { it.id == stepId } ?: return@AutomationStepList
                if (step.presetId.isBlank()) {
                    editingStepId = stepId
                } else {
                    controller.selectEditorTool(step.presetId)
                    editingPresetId = step.presetId
                }
            },
            onOpenPresets = { showPresetPicker = true }
        )
    }
        ContentScrollbar(
            state = editScrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = 6.dp, bottom = 56.dp)
        )
        Button(
            enabled = !controller.busy,
            onClick = {
                saveAndCloseEditor()
            },
            shape = ControlShape,
            contentPadding = CompactButtonPadding,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 20.dp, bottom = 6.dp)
                .fillMaxWidth()
                .height(42.dp)
        ) {
            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text("保存")
        }
    }

    if (showPresetPicker) {
        PresetPickerDialog(
            controller = controller,
            onDismiss = { showPresetPicker = false },
            onPickPreset = { presetId ->
                controller.addEditorToolToSelectedChain(presetId)
                showPresetPicker = false
            },
            onEditPreset = { presetId ->
                controller.selectEditorTool(presetId)
                editingPresetId = presetId
                showPresetPicker = false
            }
        )
    }
}
