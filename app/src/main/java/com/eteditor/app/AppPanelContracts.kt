package com.eteditor

internal const val LEFT_PANEL_NONE = "none"
internal const val LEFT_PANEL_DIRECTORY = "directory"
internal const val RIGHT_PANEL_NONE = "none"
internal const val RIGHT_PANEL_AUTOMATION = "automation"
internal const val RIGHT_PANEL_FEATURES = "features"
internal const val TXT_PREVIEW_MODE_CHAPTER = "chapter"
internal const val TXT_PREVIEW_MODE_FULL = "full"

internal val LEFT_PANEL_MODES = setOf(
    LEFT_PANEL_NONE,
    LEFT_PANEL_DIRECTORY
)

internal val RIGHT_PANEL_MODES = setOf(
    RIGHT_PANEL_NONE,
    RIGHT_PANEL_FEATURES,
    RIGHT_PANEL_AUTOMATION
)

internal val TXT_PREVIEW_MODES = setOf(
    TXT_PREVIEW_MODE_CHAPTER,
    TXT_PREVIEW_MODE_FULL
)
