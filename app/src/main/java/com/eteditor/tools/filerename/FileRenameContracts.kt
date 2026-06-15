package com.eteditor

internal const val FILE_RENAME_PARAM_NAMING_FORMAT = "naming_format"
internal const val FILE_RENAME_PARAM_SCOPE = "scope"
internal const val FILE_RENAME_PARAM_MATCH_PATTERN = "match_pattern"
internal const val FILE_RENAME_PARAM_MATCH_REGEX_ENABLED = "match_regex_enabled"
internal const val FILE_RENAME_PARAM_PREVIEW = "preview"
internal const val DEFAULT_FILE_RENAME_PATTERN = "Chapter{z4}"

data class FileRenameParameters(
    val namingFormat: String,
    val preview: Boolean
)
