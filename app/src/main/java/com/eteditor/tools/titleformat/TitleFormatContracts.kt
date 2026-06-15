package com.eteditor

internal const val TITLE_FORMAT_PARAM_MODE = "title_format_mode"
internal const val TITLE_FORMAT_PARAM_STYLE = "title_format_style"
internal const val TITLE_FORMAT_PARAM_PREVIEW = "preview"
internal const val TITLE_FORMAT_PARAM_SCOPE = "scope"
internal const val TITLE_FORMAT_PARAM_SELECTED_CHAPTERS = "selected_chapters"
internal const val TITLE_FORMAT_MODE_PER_CHAPTER = "per_chapter"
internal const val TITLE_FORMAT_MODE_UNIFORM = "uniform"
internal const val TITLE_FORMAT_SCOPE_SELECTED = "selected"

data class TitleFormatParameters(
    val mode: String,
    val style: String,
    val preview: Boolean,
    val scope: String,
    val selectedChapterIndices: Set<Int>
)
