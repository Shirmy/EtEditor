package com.eteditor

internal const val TEXT_REPLACE_SCOPE_SELECTED_HTML = "selected_html"
internal const val TEXT_REPLACE_SCOPE_INTRO = "intro"
internal const val TEXT_REPLACE_PARAM_MODE = "replace_mode"
internal const val TEXT_REPLACE_PARAM_TARGET = "replace_target"
internal const val TEXT_REPLACE_PARAM_SCOPE = "scope"
internal const val TEXT_REPLACE_PARAM_FIND = "find_text"
internal const val TEXT_REPLACE_PARAM_REPLACE = "replace_text"
internal const val TEXT_REPLACE_PARAM_FIND_REGEX = "find_regex"
internal const val TEXT_REPLACE_PARAM_BATCH_SOURCE = "batch_source"
internal const val TEXT_REPLACE_PARAM_SELECTED_HTML = "selected_html"
internal const val TEXT_REPLACE_PARAM_BATCH_TEXT = "batch_text"
internal const val TEXT_REPLACE_PARAM_BATCH_FILE = "batch_file"
internal const val TEXT_REPLACE_PARAM_PREVIEW = "preview"
internal const val TEXT_REPLACE_MODE_SINGLE = "single"
internal const val TEXT_REPLACE_MODE_BATCH = "batch"
internal const val TEXT_REPLACE_MODE_REPLACEMENT = "replacement"
internal const val TEXT_REPLACE_TARGET_VISIBLE = "visible_text"
internal const val TEXT_REPLACE_TARGET_SOURCE = "source"
internal const val TEXT_REPLACE_BATCH_FILE = "file"

data class TextReplaceParameters(
    val mode: String,
    val target: String,
    val scope: String,
    val selectedHtmlSourceIndices: Set<Int>,
    val matchPattern: String,
    val matchRegexEnabled: Boolean,
    val findText: String,
    val replaceText: String,
    val findRegexEnabled: Boolean,
    val batchSource: String,
    val batchText: String,
    val batchFile: String,
    val preview: Boolean
)
