package com.eteditor

internal const val TITLE_RENAME_PARAM_SCOPE = "scope"
internal const val TITLE_RENAME_PARAM_PATTERN = "title_rename_pattern"
internal const val TITLE_RENAME_PARAM_MATCH_PATTERN = "match_pattern"
internal const val TITLE_RENAME_PARAM_MATCH_REGEX = "match_regex"
internal const val TITLE_RENAME_PARAM_PREVIEW = "preview"

data class TitleRenameParameters(
    val pattern: String,
    val scope: String,
    val matchPattern: String,
    val matchRegexEnabled: Boolean,
    val preview: Boolean
)
