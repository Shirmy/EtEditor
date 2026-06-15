package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun TitleFormatParameterFields(
    controller: EditorController,
    toolId: String,
    valueFor: (ToolParameterDefinition) -> String,
    onValueChange: (ToolParameterDefinition, String) -> Unit,
    useBottomActionForSelectedScope: Boolean = false,
    chapterPickerRequestKey: Int = 0,
    onSelectedChaptersConfirmed: () -> Unit = {}
) {
    val parameters = controller.toolParameterDefinitions(toolId)
    val parameterByKey = parameters.associateBy { it.key }
    val modeParameter = parameterByKey[TITLE_FORMAT_PARAM_MODE]
    val styleParameter = parameterByKey[TITLE_FORMAT_PARAM_STYLE]
    val scopeParameter = parameterByKey[TITLE_FORMAT_PARAM_SCOPE]
    val selectedChaptersParameter = parameterByKey[TITLE_FORMAT_PARAM_SELECTED_CHAPTERS]
    val selectedMode = modeParameter
        ?.let { parameter -> valueFor(parameter).takeIf { value -> parameter.options.any { it.first == value } } }
        ?: modeParameter?.defaultValue
        ?: TITLE_FORMAT_MODE_PER_CHAPTER
    val selectedScope = scopeParameter
        ?.let { parameter -> valueFor(parameter).takeIf { value -> parameter.options.any { it.first == value } } }
        ?: scopeParameter?.defaultValue
        ?: scopeParameter?.options?.firstOrNull()?.first
        ?: ""
    val selectableChapters = controller.titleFormatSelectableChapterOptions()
    val selectedChaptersRaw = selectedChaptersParameter?.let(valueFor).orEmpty()
    val selectedChapterKeys = remember(selectedChaptersRaw, selectableChapters) {
        val validKeys = selectableChapters.map { it.first }.toSet()
        selectedChaptersRaw
            .split(',')
            .map { it.trim() }
            .filter { it in validKeys }
            .toSet()
    }
    val selectedChapterCount = selectableChapters.count { it.first in selectedChapterKeys }
    var showChapterPicker by remember(toolId) { mutableStateOf(false) }
    var handledChapterPickerRequestKey by remember(toolId) { mutableStateOf(chapterPickerRequestKey) }
    val selectedScopeUsesBottomAction = useBottomActionForSelectedScope &&
        selectedScope == TITLE_FORMAT_SCOPE_SELECTED
    LaunchedEffect(chapterPickerRequestKey, selectedScopeUsesBottomAction) {
        if (
            selectedScopeUsesBottomAction &&
            chapterPickerRequestKey > handledChapterPickerRequestKey
        ) {
            handledChapterPickerRequestKey = chapterPickerRequestKey
            showChapterPicker = true
        }
    }

    @Composable
    fun modeField(
        parameter: ToolParameterDefinition,
        modifier: Modifier = Modifier.fillMaxWidth()
    ) {
        val modeValue = valueFor(parameter)
            .takeIf { current -> parameter.options.any { it.first == current } }
            ?: parameter.defaultValue.takeIf { default -> parameter.options.any { it.first == default } }
            ?: parameter.options.firstOrNull()?.first
            ?: valueFor(parameter)
        ToolDropdownField(
            label = parameter.label,
            value = parameter.options.firstOrNull { it.first == modeValue }?.second ?: modeValue,
            options = parameter.options,
            onSelect = { value -> onValueChange(parameter, value) },
            modifier = modifier
        )
    }

    scopeParameter?.let { parameter ->
        ToolSegmentedChoiceField(
            label = parameter.label,
            value = selectedScope,
            options = parameter.options,
            onSelect = { value -> onValueChange(parameter, value) }
        )
    }
    if (
        scopeParameter != null &&
        selectedScope == TITLE_FORMAT_SCOPE_SELECTED &&
        selectedChaptersParameter != null &&
        !selectedScopeUsesBottomAction
    ) {
        ToolActionButtonField(
            buttonText = if (selectedChapterCount > 0) "已选${selectedChapterCount} 个" else "选择HTML章节",
            onClick = { showChapterPicker = true },
            selected = selectedChapterCount > 0
        )
    }
    if (selectedMode == TITLE_FORMAT_MODE_UNIFORM && modeParameter != null && styleParameter != null) {
        val styleValue = valueFor(styleParameter)
            .takeIf { current -> styleParameter.options.any { it.first == current } }
            ?: styleParameter.defaultValue.takeIf { default -> styleParameter.options.any { it.first == default } }
            ?: styleParameter.options.firstOrNull()?.first
            ?: valueFor(styleParameter)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            modeField(
                parameter = modeParameter,
                modifier = Modifier.weight(1f)
            )
            ToolDropdownField(
                label = styleParameter.label,
                value = styleParameter.options.firstOrNull { it.first == styleValue }?.second ?: styleValue,
                options = styleParameter.options,
                onSelect = { value -> onValueChange(styleParameter, value) },
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        modeParameter?.let { parameter ->
            modeField(parameter)
        }
    }
    val hiddenKeys = buildSet {
        add(TITLE_FORMAT_PARAM_SCOPE)
        add(TITLE_FORMAT_PARAM_SELECTED_CHAPTERS)
        add(TITLE_FORMAT_PARAM_MODE)
        add(TITLE_FORMAT_PARAM_STYLE)
        if (selectedScopeUsesBottomAction) add(TITLE_FORMAT_PARAM_PREVIEW)
    }
    parameters
        .filterNot { it.key in hiddenKeys }
        .forEach { parameter ->
            ToolParameterField(
                parameter = parameter,
                value = valueFor(parameter).ifBlank { parameter.defaultValue },
                onValueChange = { value -> onValueChange(parameter, value) }
            )
        }
    if (showChapterPicker && selectedChaptersParameter != null) {
        TitleFormatChapterPickerDialog(
            options = selectableChapters,
            selectedRaw = selectedChaptersRaw,
            confirmButtonText = if (selectedScopeUsesBottomAction) "执行" else "确定",
            onDismiss = { showChapterPicker = false },
            onConfirm = { nextValue ->
                onValueChange(selectedChaptersParameter, nextValue)
                showChapterPicker = false
                onSelectedChaptersConfirmed()
            }
        )
    }
}

@Composable
private fun TitleFormatChapterPickerDialog(
    options: List<Pair<String, String>>,
    selectedRaw: String,
    confirmButtonText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val validKeys = remember(options) { options.map { it.first }.toSet() }
    var selectedKeys by remember(selectedRaw, validKeys) {
        mutableStateOf(
            selectedRaw
                .split(',')
                .map { it.trim() }
                .filter { it in validKeys }
                .toSet()
        )
    }
    var reverseOrder by remember(options) { mutableStateOf(false) }
    val displayOptions = remember(options, reverseOrder) {
        if (reverseOrder) options.asReversed() else options
    }
    val listState = rememberLazyListState()
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
                .fixedDialogWidth(fraction = 0.86f, maxWidth = 480.dp)
                .heightIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier.padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "自选HTML",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { selectedKeys = validKeys },
                        shape = ControlShape,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("全选")
                    }
                    TextButton(
                        onClick = { reverseOrder = !reverseOrder },
                        shape = ControlShape,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(if (reverseOrder) "正序" else "逆序")
                    }
                    TextButton(
                        onClick = { selectedKeys = emptySet<String>() },
                        shape = ControlShape,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("全不选")
                    }
                }

                Surface(
                    shape = RowShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    if (options.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "没有可选章节",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .padding(end = 26.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                items(displayOptions, key = { it.first }) { (key, label) ->
                                    val selected = key in selectedKeys
                                    Surface(
                                        onClick = {
                                            selectedKeys = if (selected) selectedKeys - key else selectedKeys + key
                                        },
                                        shape = RowShape,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = selected,
                                                onCheckedChange = { checked ->
                                                    selectedKeys = if (checked) selectedKeys + key else selectedKeys - key
                                                }
                                            )
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
                                }
                            }
                            ContentScrollbar(
                                state = listState,
                                itemCount = options.size,
                                directDrag = true,
                                thumbFollowsDrag = true,
                                hitWidth = 30.dp,
                                trackWidth = 9.dp,
                                thumbWidth = 14.dp,
                                draggingThumbWidth = 18.dp,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, shape = ControlShape, contentPadding = CompactButtonPadding) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val value = selectedKeys
                                .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
                                .joinToString(",")
                            onConfirm(value)
                        },
                        shape = ControlShape,
                        contentPadding = CompactButtonPadding
                    ) {
                        Text(confirmButtonText)
                    }
                }
            }
        }
    }
}
