package com.eteditor

import androidx.compose.runtime.Composable

@Composable
fun FileRenameParameterFields(
    controller: EditorController,
    toolId: String,
    valueFor: (ToolParameterDefinition) -> String,
    onValueChange: (ToolParameterDefinition, String) -> Unit
) {
    val basicParameters = controller.toolParameterDefinitions(toolId)
    val handledParameterKeys = setOf(
        FILE_RENAME_PARAM_SCOPE,
        FILE_RENAME_PARAM_NAMING_FORMAT,
        FILE_RENAME_PARAM_MATCH_PATTERN,
        FILE_RENAME_PARAM_MATCH_REGEX_ENABLED
    )
    val namingFormatParameter = basicParameters.firstOrNull { it.key == FILE_RENAME_PARAM_NAMING_FORMAT }
    namingFormatParameter?.let { parameter ->
        ToolParameterField(
            parameter = parameter,
            value = valueFor(parameter),
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
