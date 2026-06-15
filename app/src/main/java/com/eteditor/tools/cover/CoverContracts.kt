package com.eteditor

internal const val COVER_PARAM_MODE = "cover_mode"
internal const val COVER_PARAM_TITLE = "cover_title"
internal const val COVER_PARAM_IMAGE_URI = "cover_image_uri"
internal const val COVER_PARAM_IMAGE_INSERT_TYPE = "cover_image_insert_type"
internal const val COVER_PARAM_COMPRESS = "cover_compress"
internal const val COVER_PARAM_PREVIEW = "cover_preview"
internal const val COVER_MODE_INSERT = "insert"
internal const val COVER_MODE_GENERATE = "generate"
internal const val COVER_MODE_IMAGE_INSERT = "image_insert"
internal const val COVER_IMAGE_INSERT_NOTE = "note"
internal const val COVER_IMAGE_INSERT_WARNING = "warning"
internal const val COVER_IMAGE_INSERT_CUSTOM = "custom"

data class CoverParameters(
    val mode: String,
    val title: String,
    val imageUri: String,
    val imageInsertType: String,
    val compress: Boolean,
    val preview: Boolean
)
