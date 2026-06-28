package com.eteditor

internal const val BOOL_TRUE = "true"
internal const val BOOL_FALSE = "false"
internal const val TEXT_REPLACE_BATCH_INPUT = "input"
internal const val TITLE_FORMAT_SCOPE_ALL = "all"

internal val SENSITIVE_PARAMETER_KEYS = setOf(
    FETCH_INFO_PARAM_AUTH_COOKIE,
    INSERT_CHAPTER_PARAM_SOSAD_AUTH_COOKIE
)

internal val BOOLEAN_OPTIONS = listOf(
    BOOL_TRUE to "是",
    BOOL_FALSE to "否"
)

internal val TITLE_RENAME_SCOPE_OPTIONS = listOf(
    TOOL_SCOPE_ALL to "全部HTML",
    TOOL_SCOPE_FILE_REGEX to "标题匹配"
)

internal val TEXT_REPLACE_MODE_OPTIONS = listOf(
    TEXT_REPLACE_MODE_SINGLE to "单条",
    TEXT_REPLACE_MODE_BATCH to "批量",
    TEXT_REPLACE_MODE_REPLACEMENT to "静读专用"
)

internal val TEXT_REPLACE_TARGET_OPTIONS = listOf(
    TEXT_REPLACE_TARGET_VISIBLE to "正文文本",
    TEXT_REPLACE_TARGET_SOURCE to "正文源码"
)

internal val TEXT_REPLACE_BATCH_SOURCE_OPTIONS = listOf(
    TEXT_REPLACE_BATCH_INPUT to "自定义",
    TEXT_REPLACE_BATCH_FILE to "文件"
)

internal val TEXT_REPLACE_SCOPE_OPTIONS = listOf(
    TOOL_SCOPE_ALL to "全部HTML章节",
    TOOL_SCOPE_CURRENT to "当前HTML章节"
)

internal val EPUB_TEXT_REPLACE_BATCH_SCOPE_OPTIONS = listOf(
    TOOL_SCOPE_ALL to "全部HTML章节",
    TEXT_REPLACE_SCOPE_INTRO to "简介"
)

internal val TXT_TEXT_REPLACE_SCOPE_OPTIONS = listOf(
    TOOL_SCOPE_ALL to "全文",
    TOOL_SCOPE_CURRENT to "当前章节"
)

internal val TITLE_FORMAT_MODE_OPTIONS = listOf(
    TITLE_FORMAT_MODE_PER_CHAPTER to "自动判断",
    TITLE_FORMAT_MODE_UNIFORM to "统一选择"
)

internal val TITLE_FORMAT_SCOPE_OPTIONS = listOf(
    TITLE_FORMAT_SCOPE_ALL to "全部HTML",
    TITLE_FORMAT_SCOPE_SELECTED to "自选HTML"
)

internal val INSERT_CHAPTER_SOURCE_OPTIONS = listOf(
    INSERT_CHAPTER_SOURCE_UPLOAD to "上传文件",
    INSERT_CHAPTER_SOURCE_SOSAD to "废文"
)

internal val COVER_MODE_OPTIONS = listOf(
    COVER_MODE_GENERATE to "生成封面",
    COVER_MODE_INSERT to "插入封面",
    COVER_MODE_IMAGE_INSERT to "插入图片"
)

internal val COVER_IMAGE_INSERT_OPTIONS = listOf(
    COVER_IMAGE_INSERT_NOTE to "注解",
    COVER_IMAGE_INSERT_WARNING to "预警",
    COVER_IMAGE_INSERT_CUSTOM to "自定义"
)

internal val FETCH_INFO_SOURCE_OPTIONS = FetchInfoSources.options

internal val FETCH_INFO_CONTENT_OPTIONS = listOf(
    FETCH_INFO_CONTENT_COVER to "封面",
    FETCH_INFO_CONTENT_INTRO to "简介",
    FETCH_INFO_CONTENT_CATALOG to "目录"
)

internal val TOOL_SCOPES = setOf(
    TOOL_SCOPE_ALL,
    TOOL_SCOPE_CURRENT,
    TOOL_SCOPE_FILE_REGEX
)
