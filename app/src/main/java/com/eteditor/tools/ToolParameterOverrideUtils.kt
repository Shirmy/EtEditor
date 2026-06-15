package com.eteditor

import com.eteditor.core.DocumentKind

internal fun toolParameterSaveError(
    toolId: String,
    values: Map<String, String>
): String? {
    if (toolId == "chapter_title_rename") {
        val scope = values[TITLE_RENAME_PARAM_SCOPE].orEmpty()
            .ifBlank { TOOL_SCOPE_ALL }
        if (
            scope == TOOL_SCOPE_FILE_REGEX &&
            values[TITLE_RENAME_PARAM_MATCH_PATTERN].orEmpty().isBlank()
        ) {
            return "标题匹配需要填写匹配规则"
        }
    }
    if (toolId == "generate_cover") {
        val mode = values[COVER_PARAM_MODE].orEmpty()
            .ifBlank { COVER_MODE_INSERT }
        val title = values[COVER_PARAM_TITLE].orEmpty()
        if (
            mode == COVER_MODE_GENERATE &&
            title.isNotBlank() &&
            coverTitleLength(title) > GENERATED_COVER_MAX_CHARS
        ) {
            return "封面标题最大 ${GENERATED_COVER_MAX_CHARS} 字"
        }
    }
    return null
}

internal val TXT_TEXT_REPLACE_PARAMETER_KEYS = listOf(
    TEXT_REPLACE_PARAM_SCOPE,
    TEXT_REPLACE_PARAM_FIND,
    TEXT_REPLACE_PARAM_REPLACE,
    TEXT_REPLACE_PARAM_FIND_REGEX
)

internal val TXT_TEXT_REPLACE_PRESET_KEYS = setOf(
    TEXT_REPLACE_PARAM_SCOPE,
    TEXT_REPLACE_PARAM_FIND,
    TEXT_REPLACE_PARAM_REPLACE,
    TEXT_REPLACE_PARAM_FIND_REGEX
)

internal val EPUB_TEXT_REPLACE_PRESET_KEYS = setOf(
    TEXT_REPLACE_PARAM_SCOPE,
    TEXT_REPLACE_PARAM_TARGET,
    TEXT_REPLACE_PARAM_FIND,
    TEXT_REPLACE_PARAM_REPLACE,
    TEXT_REPLACE_PARAM_FIND_REGEX
)

internal val FILE_RENAME_RANGE_PARAMETER_KEYS = setOf(
    FILE_RENAME_PARAM_SCOPE,
    FILE_RENAME_PARAM_MATCH_PATTERN,
    FILE_RENAME_PARAM_MATCH_REGEX_ENABLED
)

private val WHITESPACE_SENSITIVE_TEXT_REPLACE_PARAMETER_KEYS = setOf(
    TEXT_REPLACE_PARAM_FIND,
    TEXT_REPLACE_PARAM_REPLACE
)

internal fun defaultToolParameters(tool: ToolDefinition?): Map<String, String> {
    return tool?.parameters
        .orEmpty()
        .associate { parameter -> parameter.key to parameter.defaultValue }
}

internal fun toolParameterDefinitionsForDocument(
    toolId: String,
    kind: DocumentKind,
    parameters: List<ToolParameterDefinition>,
    txtTextReplaceScopeOptions: List<Pair<String, String>>
): List<ToolParameterDefinition> {
    if (kind == DocumentKind.Txt && toolId == "text_replace") {
        val parameterByKey = parameters.associateBy { it.key }
        return TXT_TEXT_REPLACE_PARAMETER_KEYS.mapNotNull { key ->
            parameterByKey[key]?.let { parameter ->
                if (key == TEXT_REPLACE_PARAM_SCOPE) {
                    parameter.copy(options = txtTextReplaceScopeOptions)
                } else {
                    parameter
                }
            }
        }
    }
    return parameters
}

internal fun cleanToolParameterOverrides(
    toolId: String,
    definitions: List<ToolParameterDefinition>,
    overrides: Map<String, String>,
    textReplaceScopeOptionKeys: Set<String>
): Map<String, String> {
    val definitionsByKey = definitions.associateBy { it.key }
    if (definitionsByKey.isEmpty()) return emptyMap()
    val coverMode = if (toolId == "generate_cover") {
        overrides[COVER_PARAM_MODE]
            ?: definitionsByKey[COVER_PARAM_MODE]?.defaultValue
            ?: COVER_MODE_INSERT
    } else {
        ""
    }
    return overrides
        .filterKeys { it in definitionsByKey }
        .filterKeys { key -> toolId != "file_rename" || key !in FILE_RENAME_RANGE_PARAMETER_KEYS }
        .filterKeys { key -> toolId != "generate_cover" || key != COVER_PARAM_IMAGE_URI }
        .filterKeys { key ->
            toolId != "generate_cover" ||
                key != COVER_PARAM_PREVIEW ||
                coverMode != COVER_MODE_IMAGE_INSERT
        }
        .filter { (key, value) ->
            if (toolId == "text_replace" && key == TEXT_REPLACE_PARAM_BATCH_FILE) {
                return@filter false
            }
            if (toolId == "fetch_info" && key == FETCH_INFO_PARAM_INTRO_TARGET) {
                return@filter !isDefaultFetchInfoIntroTargetOverride(value)
            }
            val parameter = definitionsByKey.getValue(key)
            val optionKeys = allowedToolParameterOptionKeys(
                toolId = toolId,
                key = key,
                parameter = parameter,
                textReplaceScopeOptionKeys = textReplaceScopeOptionKeys
            )
            !isEmptyToolParameterValue(toolId, key, value) &&
                value != parameter.defaultValue &&
                (optionKeys.isEmpty() || value in optionKeys)
        }
}

internal fun isEmptyToolParameterValue(toolId: String, key: String, value: String): Boolean {
    return if (isWhitespaceSensitiveTextReplaceParameter(toolId, key)) {
        value.isEmpty()
    } else {
        value.isBlank()
    }
}

private fun isWhitespaceSensitiveTextReplaceParameter(toolId: String, key: String): Boolean {
    return toolId == "text_replace" && key in WHITESPACE_SENSITIVE_TEXT_REPLACE_PARAMETER_KEYS
}

internal fun isSosadAuthParameter(toolId: String, key: String): Boolean {
    return (toolId == "fetch_info" && key == FETCH_INFO_PARAM_AUTH_COOKIE) ||
        (toolId == "insert_chapter" && key == INSERT_CHAPTER_PARAM_SOSAD_AUTH_COOKIE)
}

internal fun cleanTxtTextReplacePresetParameterOverrides(
    definitions: List<ToolParameterDefinition>,
    find: String,
    replace: String,
    regex: Boolean,
    scope: String,
    txtScopeOptions: List<Pair<String, String>>,
    textReplaceScopeOptionKeys: Set<String>
): Map<String, String> {
    val overrides = mapOf(
        TEXT_REPLACE_PARAM_FIND to find,
        TEXT_REPLACE_PARAM_REPLACE to replace,
        TEXT_REPLACE_PARAM_FIND_REGEX to regex.toString(),
        TEXT_REPLACE_PARAM_SCOPE to cleanTxtTextReplaceScope(scope, txtScopeOptions)
    )
    return cleanToolParameterOverrides(
        toolId = "text_replace",
        definitions = definitions,
        overrides = overrides,
        textReplaceScopeOptionKeys = textReplaceScopeOptionKeys
    ).filterKeys { key -> key in TXT_TEXT_REPLACE_PRESET_KEYS }
}

private fun cleanTxtTextReplaceScope(
    scope: String,
    txtScopeOptions: List<Pair<String, String>>
): String {
    return scope.takeIf { value -> txtScopeOptions.any { it.first == value } }
        ?: TOOL_SCOPE_ALL
}

internal fun cleanEpubTextReplacePresetParameterOverrides(
    definitions: List<ToolParameterDefinition>,
    find: String,
    replace: String,
    regex: Boolean,
    scope: String,
    target: String,
    textReplaceScopeOptionKeys: Set<String>
): Map<String, String> {
    val cleanScope = scope
        .takeIf { value -> TEXT_REPLACE_SCOPE_OPTIONS.any { it.first == value } }
        ?: TOOL_SCOPE_ALL
    val cleanTarget = target
        .takeIf { it == TEXT_REPLACE_TARGET_VISIBLE || it == TEXT_REPLACE_TARGET_SOURCE }
        ?: TEXT_REPLACE_TARGET_SOURCE
    val overrides = mapOf(
        TEXT_REPLACE_PARAM_SCOPE to cleanScope,
        TEXT_REPLACE_PARAM_TARGET to cleanTarget,
        TEXT_REPLACE_PARAM_FIND to find,
        TEXT_REPLACE_PARAM_REPLACE to replace,
        TEXT_REPLACE_PARAM_FIND_REGEX to regex.toString()
    )
    return cleanToolParameterOverrides(
        toolId = "text_replace",
        definitions = definitions,
        overrides = overrides,
        textReplaceScopeOptionKeys = textReplaceScopeOptionKeys
    ).filterKeys { key -> key in EPUB_TEXT_REPLACE_PRESET_KEYS }
}

internal fun cleanBuiltInDefaultParameterOverridesForSave(
    toolId: String,
    definitions: List<ToolParameterDefinition>,
    overrides: Map<String, String>,
    textReplaceScopeOptionKeys: Set<String>
): Map<String, String> {
    val cleaned = cleanToolParameterOverrides(
        toolId = toolId,
        definitions = definitions,
        overrides = overrides,
        textReplaceScopeOptionKeys = textReplaceScopeOptionKeys
    )
    return when (toolId) {
        "text_replace" -> cleaned - TEXT_REPLACE_PARAM_TARGET - TEXT_REPLACE_PARAM_BATCH_FILE
        "generate_cover" -> cleaned - COVER_PARAM_IMAGE_URI
        else -> cleaned
    }
}

internal fun cleanBuiltInToolParameterOverrides(
    toolId: String,
    definitions: List<ToolParameterDefinition>,
    overrides: Map<String, String>,
    textReplaceScopeOptionKeys: Set<String>
): Map<String, String> {
    val definitionsByKey = definitions.associateBy { it.key }
    if (definitionsByKey.isEmpty()) return emptyMap()
    val coverMode = if (toolId == "generate_cover") {
        overrides[COVER_PARAM_MODE]
            ?: definitionsByKey[COVER_PARAM_MODE]?.defaultValue
            ?: COVER_MODE_INSERT
    } else {
        ""
    }
    return overrides
        .filterKeys { it in definitionsByKey }
        .filterKeys { key -> toolId != "file_rename" || key !in FILE_RENAME_RANGE_PARAMETER_KEYS }
        .filterKeys { key ->
            toolId != "generate_cover" ||
                key != COVER_PARAM_PREVIEW ||
                coverMode != COVER_MODE_IMAGE_INSERT
        }
        .filter { (key, value) ->
            if (toolId == "fetch_info" && key == FETCH_INFO_PARAM_INTRO_TARGET) {
                return@filter !isDefaultFetchInfoIntroTargetOverride(value)
            }
            val parameter = definitionsByKey.getValue(key)
            val optionKeys = allowedToolParameterOptionKeys(
                toolId = toolId,
                key = key,
                parameter = parameter,
                textReplaceScopeOptionKeys = textReplaceScopeOptionKeys
            )
            !isEmptyToolParameterValue(toolId, key, value) &&
                if (optionKeys.isEmpty()) {
                    true
                } else {
                    value in optionKeys && value != parameter.defaultValue
                }
        }
}

internal fun allowedToolParameterOptionKeys(
    toolId: String,
    key: String,
    parameter: ToolParameterDefinition,
    textReplaceScopeOptionKeys: Set<String>
): Set<String> {
    return if (toolId == "text_replace" && key == TEXT_REPLACE_PARAM_SCOPE) {
        textReplaceScopeOptionKeys
    } else {
        parameter.options.map { it.first }.toSet()
    }
}

internal fun textReplaceScopeOptionKeys(
    textReplaceScopeOptions: List<Pair<String, String>>,
    txtTextReplaceScopeOptions: List<Pair<String, String>>,
    epubTextReplaceBatchScopeOptions: List<Pair<String, String>>
): Set<String> {
    return (textReplaceScopeOptions + txtTextReplaceScopeOptions + epubTextReplaceBatchScopeOptions)
        .map { it.first }
        .toSet()
}
