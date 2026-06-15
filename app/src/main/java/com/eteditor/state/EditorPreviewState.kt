package com.eteditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class EditorPreviewState {
    var previewTitle by mutableStateOf("")
    var previewText by mutableStateOf("")
    var previewHighlightStart by mutableStateOf(-1)
    var previewHighlightEnd by mutableStateOf(-1)
    var previewChapterIndex by mutableStateOf(0)
    var previewDisplayChapterIndexOverride by mutableStateOf<Int?>(null)
    var previewChapterCount by mutableStateOf(0)
    var txtPreviewMode by mutableStateOf(TXT_PREVIEW_MODE_FULL)
    var txtFullPreviewCachedAnchor by mutableStateOf<TxtFullPreviewAnchor?>(null)
    var previewHighlightChapterIndex: Int? = null
    var previewHighlightSourceStart = -1
    var previewHighlightSourceEnd = -1
    var previewVisibleSourceOffset = 0
    var previewVisibleSourceLineOffset = 0
}
