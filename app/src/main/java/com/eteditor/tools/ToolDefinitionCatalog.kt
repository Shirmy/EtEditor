package com.eteditor

internal fun defaultEditorToolDefinitions(): List<ToolDefinition> {
    return listOf(
        ToolDefinition(
            id = "file_rename",
            title = "重命名文件",
            category = "",
            description = "批量重命名 EPUB 内部章节文件，并同步 OPF / NCX / NAV 引用。",
            implemented = true,
            parameters = listOf(
                ToolParameterDefinition(
                    key = FILE_RENAME_PARAM_NAMING_FORMAT,
                    label = "命名格式",
                    defaultValue = DEFAULT_FILE_RENAME_PATTERN
                ),
                ToolParameterDefinition(
                    key = FILE_RENAME_PARAM_PREVIEW,
                    label = "预览",
                    defaultValue = BOOL_TRUE,
                    options = BOOLEAN_OPTIONS
                )
            )
        ),
        ToolDefinition(
            id = "text_replace",
            title = "文本替换",
            category = "文本",
            description = "单条、批量或 replacement 替换文本，支持正文文本、正文源码和规则文件。",
            implemented = true,
            parameters = listOf(
                ToolParameterDefinition(
                    key = TEXT_REPLACE_PARAM_MODE,
                    label = "替换模式",
                    defaultValue = TEXT_REPLACE_MODE_SINGLE,
                    options = TEXT_REPLACE_MODE_OPTIONS
                ),
                ToolParameterDefinition(
                    key = TEXT_REPLACE_PARAM_TARGET,
                    label = "替换目标",
                    defaultValue = TEXT_REPLACE_TARGET_SOURCE,
                    options = TEXT_REPLACE_TARGET_OPTIONS
                ),
                ToolParameterDefinition(
                    key = TEXT_REPLACE_PARAM_FIND,
                    label = "查找内容",
                    defaultValue = ""
                ),
                ToolParameterDefinition(
                    key = TEXT_REPLACE_PARAM_REPLACE,
                    label = "替换为",
                    defaultValue = ""
                ),
                ToolParameterDefinition(
                    key = TEXT_REPLACE_PARAM_FIND_REGEX,
                    label = "查找正则",
                    defaultValue = BOOL_FALSE,
                    options = BOOLEAN_OPTIONS
                ),
                ToolParameterDefinition(
                    key = TEXT_REPLACE_PARAM_BATCH_SOURCE,
                    label = "来源",
                    defaultValue = TEXT_REPLACE_BATCH_INPUT,
                    options = TEXT_REPLACE_BATCH_SOURCE_OPTIONS
                ),
                ToolParameterDefinition(
                    key = TEXT_REPLACE_PARAM_SCOPE,
                    label = "处理范围",
                    defaultValue = TOOL_SCOPE_ALL,
                    options = TEXT_REPLACE_SCOPE_OPTIONS
                ),
                ToolParameterDefinition(
                    key = TEXT_REPLACE_PARAM_BATCH_TEXT,
                    label = "批量文本",
                    defaultValue = ""
                ),
                ToolParameterDefinition(
                    key = TEXT_REPLACE_PARAM_BATCH_FILE,
                    label = "规则文件",
                    defaultValue = ""
                ),
                ToolParameterDefinition(
                    key = TEXT_REPLACE_PARAM_PREVIEW,
                    label = "预览",
                    defaultValue = BOOL_TRUE,
                    options = BOOLEAN_OPTIONS
                )
            )
        ),
        ToolDefinition(
            id = "insert_chapter",
            title = "插入章节",
            category = "章节",
            description = "从 EPUB、TXT 或废文导入章节，并插入到当前目录指定位置。",
            implemented = true,
            parameters = listOf(
                ToolParameterDefinition(
                    key = INSERT_CHAPTER_PARAM_SOURCE_TYPE,
                    label = "来源类型",
                    defaultValue = INSERT_CHAPTER_SOURCE_UPLOAD,
                    options = INSERT_CHAPTER_SOURCE_OPTIONS
                ),
                ToolParameterDefinition(
                    key = INSERT_CHAPTER_PARAM_SOSAD_QUERY,
                    label = "废文书名/网址",
                    defaultValue = ""
                ),
                ToolParameterDefinition(
                    key = INSERT_CHAPTER_PARAM_SOSAD_AUTH_COOKIE,
                    label = "废文登录",
                    defaultValue = ""
                ),
                ToolParameterDefinition(
                    key = INSERT_CHAPTER_PARAM_SOSAD_RANGE_START,
                    label = "废文起始",
                    defaultValue = "1"
                ),
                ToolParameterDefinition(
                    key = INSERT_CHAPTER_PARAM_SOSAD_RANGE_END,
                    label = "废文结束",
                    defaultValue = ""
                ),
                ToolParameterDefinition(
                    key = INSERT_CHAPTER_PARAM_PREVIEW,
                    label = "预览",
                    defaultValue = BOOL_TRUE,
                    options = BOOLEAN_OPTIONS
                )
            )
        ),
        ToolDefinition(
            id = "chapter_title_rename",
            title = "重命名标题",
            category = "标题",
            description = "批量给普通章节编号并保留标题后缀。",
            implemented = true,
            parameters = listOf(
                ToolParameterDefinition(
                    key = TITLE_RENAME_PARAM_SCOPE,
                    label = "处理范围",
                    defaultValue = TOOL_SCOPE_ALL,
                    options = TITLE_RENAME_SCOPE_OPTIONS
                ),
                ToolParameterDefinition(
                    key = TITLE_RENAME_PARAM_PATTERN,
                    label = "标题模板",
                    defaultValue = ""
                ),
                ToolParameterDefinition(
                    key = TITLE_RENAME_PARAM_MATCH_PATTERN,
                    label = "匹配规则",
                    defaultValue = ""
                ),
                ToolParameterDefinition(
                    key = TITLE_RENAME_PARAM_MATCH_REGEX,
                    label = "正则",
                    defaultValue = BOOL_TRUE,
                    options = BOOLEAN_OPTIONS
                ),
                ToolParameterDefinition(
                    key = TITLE_RENAME_PARAM_PREVIEW,
                    label = "预览",
                    defaultValue = BOOL_TRUE,
                    options = BOOLEAN_OPTIONS
                )
            )
        ),
        ToolDefinition(
            id = "title_format",
            title = "标题格式",
            category = "标题",
            description = "按标题后缀长度自动套用无横线、双横线或左竖线格式。",
            implemented = true,
            parameters = listOf(
                ToolParameterDefinition(
                    key = TITLE_FORMAT_PARAM_MODE,
                    label = "执行方式",
                    defaultValue = TITLE_FORMAT_MODE_PER_CHAPTER,
                    options = TITLE_FORMAT_MODE_OPTIONS
                ),
                ToolParameterDefinition(
                    key = TITLE_FORMAT_PARAM_STYLE,
                    label = "格式",
                    defaultValue = TITLE_FORMAT_STYLE_DOUBLE,
                    options = TITLE_FORMAT_STYLE_OPTIONS
                ),
                ToolParameterDefinition(
                    key = TITLE_FORMAT_PARAM_PREVIEW,
                    label = "预览",
                    defaultValue = BOOL_TRUE,
                    options = BOOLEAN_OPTIONS
                ),
                ToolParameterDefinition(
                    key = TITLE_FORMAT_PARAM_SCOPE,
                    label = "处理范围",
                    defaultValue = TOOL_SCOPE_ALL,
                    options = TITLE_FORMAT_SCOPE_OPTIONS
                ),
                ToolParameterDefinition(
                    key = TITLE_FORMAT_PARAM_SELECTED_CHAPTERS,
                    label = "自选HTML",
                    defaultValue = ""
                )
            )
        ),
        ToolDefinition(
            id = "fetch_info",
            title = "抓取信息",
            category = "信息",
            description = "从晋江、长佩、废文抓取目录、简介和封面元信息。",
            implemented = true,
            parameters = listOf(
                ToolParameterDefinition(
                    key = FETCH_INFO_PARAM_SOURCE,
                    label = "来源",
                    defaultValue = FETCH_INFO_SOURCE_JJWXC,
                    options = FetchInfoSources.options
                ),
                ToolParameterDefinition(
                    key = FETCH_INFO_PARAM_SEARCH_MODE,
                    label = "搜索",
                    defaultValue = FETCH_INFO_SEARCH_TITLE,
                    options = FETCH_INFO_SEARCH_OPTIONS
                ),
                ToolParameterDefinition(
                    key = FETCH_INFO_PARAM_QUERY,
                    label = "关键词/网址",
                    defaultValue = ""
                ),
                ToolParameterDefinition(
                    key = FETCH_INFO_PARAM_CONTENT,
                    label = "抓取内容",
                    defaultValue = FETCH_INFO_CONTENT_CATALOG,
                    options = FETCH_INFO_CONTENT_OPTIONS
                ),
                ToolParameterDefinition(
                    key = FETCH_INFO_PARAM_AUTH_COOKIE,
                    label = "登录 Cookie",
                    defaultValue = ""
                ),
                ToolParameterDefinition(
                    key = FETCH_INFO_PARAM_INTRO_TARGET,
                    label = "简介文件路径",
                    defaultValue = DEFAULT_FETCH_INFO_INTRO_TARGET
                ),
                ToolParameterDefinition(
                    key = FETCH_INFO_PARAM_CATALOG_FILTER,
                    label = "目录过滤",
                    defaultValue = ""
                ),
                ToolParameterDefinition(
                    key = FETCH_INFO_PARAM_CATALOG_FILTER_ENABLED,
                    label = "启用目录过滤",
                    defaultValue = BOOL_TRUE,
                    options = BOOLEAN_OPTIONS
                ),
                ToolParameterDefinition(
                    key = FETCH_INFO_PARAM_AUTO_TITLE_FORMAT,
                    label = "自动判断格式",
                    defaultValue = BOOL_FALSE,
                    options = BOOLEAN_OPTIONS
                ),
                ToolParameterDefinition(
                    key = FETCH_INFO_PARAM_INTRO_FILTER,
                    label = "简介过滤",
                    defaultValue = "trim\ncompressBlankLines"
                )
            )
        ),
        ToolDefinition(
            id = "generate_cover",
            title = "插入图片",
            category = "信息",
            description = "插入本地图片为封面，生成封面，或向 EPUB 注册图片资源。",
            implemented = true,
            parameters = listOf(
                ToolParameterDefinition(
                    key = COVER_PARAM_MODE,
                    label = "封面方式",
                    defaultValue = COVER_MODE_INSERT,
                    options = COVER_MODE_OPTIONS
                ),
                ToolParameterDefinition(
                    key = COVER_PARAM_TITLE,
                    label = "封面标题",
                    defaultValue = ""
                ),
                ToolParameterDefinition(
                    key = COVER_PARAM_IMAGE_URI,
                    label = "图片文件",
                    defaultValue = ""
                ),
                ToolParameterDefinition(
                    key = COVER_PARAM_IMAGE_INSERT_TYPE,
                    label = "图片类型",
                    defaultValue = COVER_IMAGE_INSERT_NOTE,
                    options = COVER_IMAGE_INSERT_OPTIONS
                ),
                ToolParameterDefinition(
                    key = COVER_PARAM_COMPRESS,
                    label = "压缩",
                    defaultValue = BOOL_TRUE
                ),
                ToolParameterDefinition(
                    key = COVER_PARAM_PREVIEW,
                    label = "预览",
                    defaultValue = BOOL_FALSE,
                    options = BOOLEAN_OPTIONS
                )
            )
        )
    )
}
