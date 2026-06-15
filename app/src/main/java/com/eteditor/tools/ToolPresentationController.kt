package com.eteditor

fun EditorController.titleFormatSelectableChapterOptions(): List<Pair<String, String>> {
    return titleFormatSelectableChapterOptionsModel(
        kind = kind,
        epubChapters = epub?.chapters,
        txtDocument = txt
    )
}

fun EditorController.editorToolSummary(tool: EditorTool): String {
    return editorToolSummaryText(
        toolId = tool.toolId,
        baseLabel = editorToolBaseLabel(tool),
        fetchInfoParameters = if (tool.toolId == "fetch_info") fetchInfoParameters(tool) else null,
        titleFormatParameters = if (tool.toolId == "title_format") titleFormatParameters(tool) else null,
        titleRenameParameters = if (tool.toolId == "chapter_title_rename") titleRenameParameters(tool) else null,
        insertChapterParameters = if (tool.toolId == "insert_chapter") insertChapterParameters(tool) else null,
        coverParameters = if (tool.toolId == "generate_cover") coverParameters(tool) else null,
        titleRenameScopeOptions = TITLE_RENAME_SCOPE_OPTIONS,
        titleFormatModeOptions = TITLE_FORMAT_MODE_OPTIONS,
        insertChapterSourceOptions = INSERT_CHAPTER_SOURCE_OPTIONS,
        coverModeOptions = COVER_MODE_OPTIONS,
        coverImageInsertOptions = COVER_IMAGE_INSERT_OPTIONS
    )
}
