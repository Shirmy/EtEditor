package com.eteditor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TitleRenameParameterFields(
    controller: EditorController,
    toolId: String,
    valueFor: (ToolParameterDefinition) -> String,
    onValueChange: (ToolParameterDefinition, String) -> Unit
) {
    val basicParameters = controller.toolParameterDefinitions(toolId)
    val handledParameterKeys = setOf(
        TITLE_RENAME_PARAM_SCOPE,
        TITLE_RENAME_PARAM_PATTERN,
        TITLE_RENAME_PARAM_MATCH_PATTERN,
        TITLE_RENAME_PARAM_MATCH_REGEX,
        TITLE_RENAME_PARAM_PREVIEW
    )
    val scopeParameter = basicParameters.firstOrNull { it.key == TITLE_RENAME_PARAM_SCOPE }
    val patternParameter = basicParameters.firstOrNull { it.key == TITLE_RENAME_PARAM_PATTERN }
    val matchPatternParameter = basicParameters.firstOrNull { it.key == TITLE_RENAME_PARAM_MATCH_PATTERN }
    val matchRegexParameter = basicParameters.firstOrNull { it.key == TITLE_RENAME_PARAM_MATCH_REGEX }
    val previewParameter = basicParameters.firstOrNull { it.key == TITLE_RENAME_PARAM_PREVIEW }
    val selectedScope = scopeParameter
        ?.let { parameter -> valueFor(parameter).takeIf { value -> parameter.options.any { it.first == value } } }
        ?: scopeParameter?.options?.firstOrNull()?.first
        ?: TOOL_SCOPE_ALL

    scopeParameter?.let { parameter ->
        ToolSegmentedChoiceField(
            label = parameter.label,
            value = selectedScope,
            options = parameter.options,
            onSelect = { value -> onValueChange(parameter, value) }
        )
    }
    if (selectedScope == TOOL_SCOPE_FILE_REGEX) {
        if (matchPatternParameter != null && matchRegexParameter != null) {
            FileRenameMatchRuleField(
                patternLabel = matchPatternParameter.label,
                patternValue = valueFor(matchPatternParameter),
                onPatternChange = { value -> onValueChange(matchPatternParameter, value) },
                regexLabel = matchRegexParameter.label,
                regexChecked = valueFor(matchRegexParameter).ifBlank { matchRegexParameter.defaultValue } == "true",
                onRegexChange = { checked -> onValueChange(matchRegexParameter, if (checked) "true" else "false") },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            listOfNotNull(matchPatternParameter, matchRegexParameter).forEach { parameter ->
                ToolParameterField(
                    parameter = parameter,
                    value = valueFor(parameter).ifBlank { parameter.defaultValue },
                    onValueChange = { value -> onValueChange(parameter, value) }
                )
            }
        }
    }
    patternParameter?.let { parameter ->
        ToolParameterField(
            parameter = parameter,
            value = valueFor(parameter).ifBlank { parameter.defaultValue },
            onValueChange = { value -> onValueChange(parameter, value) }
        )
    }
    previewParameter?.let { parameter ->
        ToolParameterField(
            parameter = parameter,
            value = valueFor(parameter).ifBlank { parameter.defaultValue },
            onValueChange = { value -> onValueChange(parameter, value) }
        )
    }
    basicParameters.filterNot { it.key in handledParameterKeys }.forEach { parameter ->
        ToolParameterField(
            parameter = parameter,
            value = valueFor(parameter).ifBlank { parameter.defaultValue },
            onValueChange = { value -> onValueChange(parameter, value) }
        )
    }
}
