package com.eteditor

internal fun editorToolSummaryText(
    toolId: String,
    baseLabel: String,
    fetchInfoParameters: FetchInfoParameters?,
    titleFormatParameters: TitleFormatParameters?,
    titleRenameParameters: TitleRenameParameters?,
    insertChapterParameters: InsertChapterParameters?,
    coverParameters: CoverParameters?,
    titleRenameScopeOptions: List<Pair<String, String>>,
    titleFormatModeOptions: List<Pair<String, String>>,
    insertChapterSourceOptions: List<Pair<String, String>>,
    coverModeOptions: List<Pair<String, String>>,
    coverImageInsertOptions: List<Pair<String, String>>
): String {
    val detail = when (toolId) {
        "file_rename" -> ""
        "fetch_info" -> fetchInfoParameters?.let { parameters ->
            " | ${fetchInfoContentLabel(parameters.content)}"
        }.orEmpty()
        "title_format" -> titleFormatParameters?.let { parameters ->
            val modeLabel = titleFormatModeOptions.firstOrNull { it.first == parameters.mode }?.second
                ?: "自动判断"
            " | $modeLabel"
        }.orEmpty()
        "chapter_title_rename" -> titleRenameParameters?.let { parameters ->
            val matchText = if (parameters.scope == TOOL_SCOPE_FILE_REGEX) {
                " ${parameters.matchPattern}"
            } else {
                ""
            }
            " | ${titleRenameScopeLabel(parameters.scope, titleRenameScopeOptions)}$matchText | ${parameters.pattern}"
        }.orEmpty()
        "insert_chapter" -> insertChapterParameters?.let { parameters ->
            val sourceLabel = insertChapterSourceOptions.firstOrNull { it.first == parameters.sourceType }?.second
                ?: "上传文件"
            " | $sourceLabel"
        }.orEmpty()
        "generate_cover" -> coverParameters?.let { parameters ->
            val modeLabel = coverModeOptions.firstOrNull { it.first == parameters.mode }?.second
                ?: "插入图片"
            val coverDetail = when (parameters.mode) {
                COVER_MODE_GENERATE -> parameters.title.ifBlank { "当前书名" }
                COVER_MODE_IMAGE_INSERT -> coverImageInsertOptions
                    .firstOrNull { it.first == parameters.imageInsertType }
                    ?.second
                    ?: "注解"
                else -> parameters.imageUri.substringAfterLast('/').substringAfterLast(':').ifBlank { "未选图片" }
            }
            " | $modeLabel | $coverDetail"
        }.orEmpty()
        else -> ""
    }
    return "$baseLabel$detail"
}
