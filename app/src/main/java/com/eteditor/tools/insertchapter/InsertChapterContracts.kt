package com.eteditor

internal const val INSERT_CHAPTER_PARAM_SOURCE_TYPE = "insert_source_type"
internal const val INSERT_CHAPTER_PARAM_SOSAD_QUERY = "insert_sosad_query"
internal const val INSERT_CHAPTER_PARAM_SOSAD_AUTH_COOKIE = "insert_sosad_auth_cookie"
internal const val INSERT_CHAPTER_PARAM_SOSAD_RANGE_START = "insert_sosad_range_start"
internal const val INSERT_CHAPTER_PARAM_SOSAD_RANGE_END = "insert_sosad_range_end"
internal const val INSERT_CHAPTER_PARAM_PREVIEW = "insert_preview"
internal const val INSERT_CHAPTER_SOSAD_URI = "eteditor://insert-chapter/sosad"
internal const val INSERT_CHAPTER_SOURCE_UPLOAD = "upload"
internal const val INSERT_CHAPTER_SOURCE_EPUB = "epub"
internal const val INSERT_CHAPTER_SOURCE_TXT = "txt"
internal const val INSERT_CHAPTER_SOURCE_SOSAD = "sosad"
internal const val INSERT_CHAPTER_POSITION_START = "book_start"
internal const val INSERT_CHAPTER_POSITION_END = "book_end"
internal const val INSERT_CHAPTER_POSITION_CURRENT_BEFORE = "current_before"
internal const val INSERT_CHAPTER_POSITION_CURRENT_AFTER = "current_after"
internal const val INSERT_CHAPTER_POSITION_VOLUME_END = "volume_end"
internal const val INSERT_CHAPTER_POSITION_TARGET_BEFORE = "target_before"
internal const val INSERT_CHAPTER_POSITION_TARGET_AFTER = "target_after"

data class InsertChapterParameters(
    val sourceType: String,
    val sosadQuery: String,
    val sosadAuthCookie: String,
    val sosadBodyRangeStart: Int,
    val sosadBodyRangeEnd: Int,
    val preview: Boolean
)
