package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolSummaryUtilsTest {
    @Test
    fun editorToolSummaryTextAddsFetchTitleRenameInsertAndCoverDetails() {
        assertEquals(
            "获取资料 | 简介",
            editorToolSummaryText(
                toolId = "fetch_info",
                baseLabel = "获取资料",
                fetchInfoParameters = fetchParameters(content = FETCH_INFO_CONTENT_INTRO),
                titleFormatParameters = null,
                titleRenameParameters = null,
                insertChapterParameters = null,
                coverParameters = null,
                titleRenameScopeOptions = scopeOptions(),
                titleFormatModeOptions = titleModeOptions(),
                insertChapterSourceOptions = insertSourceOptions(),
                coverModeOptions = coverModeOptions(),
                coverImageInsertOptions = coverImageOptions()
            )
        )
        assertEquals(
            "标题重命名 | 标题匹配 正文 | 第{Z2}章 {title}",
            editorToolSummaryText(
                toolId = "chapter_title_rename",
                baseLabel = "标题重命名",
                fetchInfoParameters = null,
                titleFormatParameters = null,
                titleRenameParameters = TitleRenameParameters("第{Z2}章 {title}", TOOL_SCOPE_FILE_REGEX, "正文", true, true),
                insertChapterParameters = null,
                coverParameters = null,
                titleRenameScopeOptions = scopeOptions(),
                titleFormatModeOptions = titleModeOptions(),
                insertChapterSourceOptions = insertSourceOptions(),
                coverModeOptions = coverModeOptions(),
                coverImageInsertOptions = coverImageOptions()
            )
        )
        assertEquals(
            "插入章节 | 废文",
            editorToolSummaryText(
                toolId = "insert_chapter",
                baseLabel = "插入章节",
                fetchInfoParameters = null,
                titleFormatParameters = null,
                titleRenameParameters = null,
                insertChapterParameters = InsertChapterParameters(
                    sourceType = INSERT_CHAPTER_SOURCE_SOSAD,
                    sosadQuery = "",
                    sosadAuthCookie = "",
                    sosadBodyRangeStart = 1,
                    sosadBodyRangeEnd = 0,
                    preview = true
                ),
                coverParameters = null,
                titleRenameScopeOptions = scopeOptions(),
                titleFormatModeOptions = titleModeOptions(),
                insertChapterSourceOptions = insertSourceOptions(),
                coverModeOptions = coverModeOptions(),
                coverImageInsertOptions = coverImageOptions()
            )
        )
        assertEquals(
            "封面 | 插入图片 | cover.jpg",
            editorToolSummaryText(
                toolId = "generate_cover",
                baseLabel = "封面",
                fetchInfoParameters = null,
                titleFormatParameters = null,
                titleRenameParameters = null,
                insertChapterParameters = null,
                coverParameters = CoverParameters(
                    mode = COVER_MODE_INSERT,
                    title = "",
                    imageUri = "content://picked/cover.jpg",
                    imageInsertType = COVER_IMAGE_INSERT_NOTE,
                    compress = true,
                    preview = true
                ),
                titleRenameScopeOptions = scopeOptions(),
                titleFormatModeOptions = titleModeOptions(),
                insertChapterSourceOptions = insertSourceOptions(),
                coverModeOptions = coverModeOptions(),
                coverImageInsertOptions = coverImageOptions()
            )
        )
    }

    @Test
    fun editorToolSummaryTextUsesFallbackLabelsForUnknownOptions() {
        assertEquals(
            "封面 | 插入到正文 | 注解",
            editorToolSummaryText(
                toolId = "generate_cover",
                baseLabel = "封面",
                fetchInfoParameters = null,
                titleFormatParameters = null,
                titleRenameParameters = null,
                insertChapterParameters = null,
                coverParameters = CoverParameters(
                    mode = COVER_MODE_IMAGE_INSERT,
                    title = "",
                    imageUri = "",
                    imageInsertType = "unknown",
                    compress = true,
                    preview = true
                ),
                titleRenameScopeOptions = scopeOptions(),
                titleFormatModeOptions = titleModeOptions(),
                insertChapterSourceOptions = insertSourceOptions(),
                coverModeOptions = coverModeOptions(),
                coverImageInsertOptions = coverImageOptions()
            )
        )
    }

    @Test
    fun editorToolSummaryTextOmitsTitleRenameMatchTextOutsideFileRegexScope() {
        assertEquals(
            "标题重命名 | 全部 | 第{Z2}章 {title}",
            editorToolSummaryText(
                toolId = "chapter_title_rename",
                baseLabel = "标题重命名",
                fetchInfoParameters = null,
                titleFormatParameters = null,
                titleRenameParameters = TitleRenameParameters("第{Z2}章 {title}", TOOL_SCOPE_ALL, "正文", true, true),
                insertChapterParameters = null,
                coverParameters = null,
                titleRenameScopeOptions = scopeOptions(),
                titleFormatModeOptions = titleModeOptions(),
                insertChapterSourceOptions = insertSourceOptions(),
                coverModeOptions = coverModeOptions(),
                coverImageInsertOptions = coverImageOptions()
            )
        )
    }

    @Test
    fun editorToolSummaryTextAddsTitleFormatAndGeneratedCoverDetails() {
        assertEquals(
            "标题格式 | 统一",
            editorToolSummaryText(
                toolId = "title_format",
                baseLabel = "标题格式",
                fetchInfoParameters = null,
                titleFormatParameters = TitleFormatParameters(
                    mode = TITLE_FORMAT_MODE_UNIFORM,
                    style = TITLE_FORMAT_STYLE_DOUBLE,
                    preview = true,
                    scope = TOOL_SCOPE_ALL,
                    selectedChapterIndices = emptySet()
                ),
                titleRenameParameters = null,
                insertChapterParameters = null,
                coverParameters = null,
                titleRenameScopeOptions = scopeOptions(),
                titleFormatModeOptions = titleModeOptions(),
                insertChapterSourceOptions = insertSourceOptions(),
                coverModeOptions = coverModeOptions(),
                coverImageInsertOptions = coverImageOptions()
            )
        )
        assertEquals(
            "封面 | 生成封面 | 当前书名",
            editorToolSummaryText(
                toolId = "generate_cover",
                baseLabel = "封面",
                fetchInfoParameters = null,
                titleFormatParameters = null,
                titleRenameParameters = null,
                insertChapterParameters = null,
                coverParameters = CoverParameters(
                    mode = COVER_MODE_GENERATE,
                    title = "",
                    imageUri = "",
                    imageInsertType = COVER_IMAGE_INSERT_NOTE,
                    compress = true,
                    preview = true
                ),
                titleRenameScopeOptions = scopeOptions(),
                titleFormatModeOptions = titleModeOptions(),
                insertChapterSourceOptions = insertSourceOptions(),
                coverModeOptions = coverModeOptions(),
                coverImageInsertOptions = coverImageOptions()
            )
        )
    }

    @Test
    fun editorToolSummaryTextUsesGeneratedCoverCustomTitleWhenPresent() {
        assertEquals(
            "封面 | 生成封面 | 自定义封面标题",
            editorToolSummaryText(
                toolId = "generate_cover",
                baseLabel = "封面",
                fetchInfoParameters = null,
                titleFormatParameters = null,
                titleRenameParameters = null,
                insertChapterParameters = null,
                coverParameters = CoverParameters(
                    mode = COVER_MODE_GENERATE,
                    title = "自定义封面标题",
                    imageUri = "",
                    imageInsertType = COVER_IMAGE_INSERT_NOTE,
                    compress = true,
                    preview = true
                ),
                titleRenameScopeOptions = scopeOptions(),
                titleFormatModeOptions = titleModeOptions(),
                insertChapterSourceOptions = insertSourceOptions(),
                coverModeOptions = coverModeOptions(),
                coverImageInsertOptions = coverImageOptions()
            )
        )
    }

    @Test
    fun editorToolSummaryTextUsesLastPathAndColonSegmentForInsertedCoverUri() {
        assertEquals(
            "封面 | 插入图片 | cover.webp",
            editorToolSummaryText(
                toolId = "generate_cover",
                baseLabel = "封面",
                fetchInfoParameters = null,
                titleFormatParameters = null,
                titleRenameParameters = null,
                insertChapterParameters = null,
                coverParameters = CoverParameters(
                    mode = COVER_MODE_INSERT,
                    title = "",
                    imageUri = "content://provider/tree/folder/image:cover.webp",
                    imageInsertType = COVER_IMAGE_INSERT_NOTE,
                    compress = true,
                    preview = true
                ),
                titleRenameScopeOptions = scopeOptions(),
                titleFormatModeOptions = titleModeOptions(),
                insertChapterSourceOptions = insertSourceOptions(),
                coverModeOptions = coverModeOptions(),
                coverImageInsertOptions = coverImageOptions()
            )
        )
    }

    @Test
    fun editorToolSummaryTextReturnsBaseLabelWhenToolHasNoDetailOrParametersAreMissing() {
        assertEquals(
            "文件重命名",
            editorToolSummaryText(
                toolId = "file_rename",
                baseLabel = "文件重命名",
                fetchInfoParameters = null,
                titleFormatParameters = null,
                titleRenameParameters = null,
                insertChapterParameters = null,
                coverParameters = null,
                titleRenameScopeOptions = scopeOptions(),
                titleFormatModeOptions = titleModeOptions(),
                insertChapterSourceOptions = insertSourceOptions(),
                coverModeOptions = coverModeOptions(),
                coverImageInsertOptions = coverImageOptions()
            )
        )
        assertEquals(
            "获取资料",
            editorToolSummaryText(
                toolId = "fetch_info",
                baseLabel = "获取资料",
                fetchInfoParameters = null,
                titleFormatParameters = null,
                titleRenameParameters = null,
                insertChapterParameters = null,
                coverParameters = null,
                titleRenameScopeOptions = scopeOptions(),
                titleFormatModeOptions = titleModeOptions(),
                insertChapterSourceOptions = insertSourceOptions(),
                coverModeOptions = coverModeOptions(),
                coverImageInsertOptions = coverImageOptions()
            )
        )
        assertEquals(
            "未知工具",
            editorToolSummaryText(
                toolId = "unknown",
                baseLabel = "未知工具",
                fetchInfoParameters = fetchParameters(FETCH_INFO_CONTENT_COVER),
                titleFormatParameters = null,
                titleRenameParameters = null,
                insertChapterParameters = null,
                coverParameters = null,
                titleRenameScopeOptions = scopeOptions(),
                titleFormatModeOptions = titleModeOptions(),
                insertChapterSourceOptions = insertSourceOptions(),
                coverModeOptions = coverModeOptions(),
                coverImageInsertOptions = coverImageOptions()
            )
        )
    }

    @Test
    fun editorToolSummaryTextUsesFallbackLabelsForUnknownModesAndBlankCoverUri() {
        assertEquals(
            "标题格式 | 自动判断",
            editorToolSummaryText(
                toolId = "title_format",
                baseLabel = "标题格式",
                fetchInfoParameters = null,
                titleFormatParameters = TitleFormatParameters(
                    mode = "unknown",
                    style = TITLE_FORMAT_STYLE_DOUBLE,
                    preview = true,
                    scope = TOOL_SCOPE_ALL,
                    selectedChapterIndices = emptySet()
                ),
                titleRenameParameters = null,
                insertChapterParameters = null,
                coverParameters = null,
                titleRenameScopeOptions = scopeOptions(),
                titleFormatModeOptions = titleModeOptions(),
                insertChapterSourceOptions = insertSourceOptions(),
                coverModeOptions = coverModeOptions(),
                coverImageInsertOptions = coverImageOptions()
            )
        )
        assertEquals(
            "插入章节 | 上传文件",
            editorToolSummaryText(
                toolId = "insert_chapter",
                baseLabel = "插入章节",
                fetchInfoParameters = null,
                titleFormatParameters = null,
                titleRenameParameters = null,
                insertChapterParameters = InsertChapterParameters(
                    sourceType = "unknown",
                    sosadQuery = "",
                    sosadAuthCookie = "",
                    sosadBodyRangeStart = 1,
                    sosadBodyRangeEnd = 0,
                    preview = true
                ),
                coverParameters = null,
                titleRenameScopeOptions = scopeOptions(),
                titleFormatModeOptions = titleModeOptions(),
                insertChapterSourceOptions = insertSourceOptions(),
                coverModeOptions = coverModeOptions(),
                coverImageInsertOptions = coverImageOptions()
            )
        )
        assertEquals(
            "封面 | 插入图片 | 未选图片",
            editorToolSummaryText(
                toolId = "generate_cover",
                baseLabel = "封面",
                fetchInfoParameters = null,
                titleFormatParameters = null,
                titleRenameParameters = null,
                insertChapterParameters = null,
                coverParameters = CoverParameters(
                    mode = "unknown",
                    title = "",
                    imageUri = "",
                    imageInsertType = COVER_IMAGE_INSERT_NOTE,
                    compress = true,
                    preview = true
                ),
                titleRenameScopeOptions = scopeOptions(),
                titleFormatModeOptions = titleModeOptions(),
                insertChapterSourceOptions = insertSourceOptions(),
                coverModeOptions = coverModeOptions(),
                coverImageInsertOptions = coverImageOptions()
            )
        )
    }

    private fun fetchParameters(content: String): FetchInfoParameters {
        return FetchInfoParameters(
            source = FETCH_INFO_SOURCE_JJWXC,
            searchMode = FETCH_INFO_SEARCH_TITLE,
            query = "Book",
            content = content,
            fetchCatalog = content == FETCH_INFO_CONTENT_CATALOG,
            fetchIntro = content == FETCH_INFO_CONTENT_INTRO,
            fetchCover = content == FETCH_INFO_CONTENT_COVER,
            authCookie = "",
            bodyRangeStart = 1,
            bodyRangeEnd = 0,
            catalogFilter = "",
            autoTitleFormat = false,
            introFilter = "",
            writeCatalog = content == FETCH_INFO_CONTENT_CATALOG,
            writeIntro = content == FETCH_INFO_CONTENT_INTRO,
            introTargetPath = DEFAULT_FETCH_INFO_INTRO_TARGET,
            writeCover = content == FETCH_INFO_CONTENT_COVER
        )
    }

    private fun scopeOptions(): List<Pair<String, String>> {
        return listOf(TOOL_SCOPE_ALL to "全部", TOOL_SCOPE_FILE_REGEX to "标题匹配")
    }

    private fun titleModeOptions(): List<Pair<String, String>> {
        return listOf(TITLE_FORMAT_MODE_PER_CHAPTER to "自动判断", TITLE_FORMAT_MODE_UNIFORM to "统一")
    }

    private fun insertSourceOptions(): List<Pair<String, String>> {
        return listOf(INSERT_CHAPTER_SOURCE_UPLOAD to "上传文件", INSERT_CHAPTER_SOURCE_SOSAD to "废文")
    }

    private fun coverModeOptions(): List<Pair<String, String>> {
        return listOf(
            COVER_MODE_INSERT to "插入图片",
            COVER_MODE_GENERATE to "生成封面",
            COVER_MODE_IMAGE_INSERT to "插入到正文"
        )
    }

    private fun coverImageOptions(): List<Pair<String, String>> {
        return listOf(COVER_IMAGE_INSERT_NOTE to "注解", COVER_IMAGE_INSERT_WARNING to "警告")
    }
}
