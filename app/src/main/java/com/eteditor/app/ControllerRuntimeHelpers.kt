package com.eteditor

internal fun EditorController.targetChapterIndices(
    scope: String,
    size: Int,
    matchPattern: String = "",
    matchRegexEnabled: Boolean = true
): List<Int> {
    return toolScopeTargetChapterIndices(
        scope = scope,
        size = size,
        currentIndex = previewChapterIndex,
        chapters = chapters,
        matchPattern = matchPattern,
        matchRegexEnabled = matchRegexEnabled
    ) { message ->
        statusMessage = message
    }
}

internal fun EditorController.appendAutomationLog(message: String) {
    automationLog = (automationLog + message).takeLast(80)
}

internal fun EditorController.automationStepResultSuffix(): String {
    return statusMessage
        .takeIf { it.isNotBlank() && it != FILE_RENAME_NEEDS_CONFIRM_MESSAGE }
        ?.let { "：$it" }
        ?: "：已处理"
}

internal fun EditorController.needsConfirmationMessage(): String = FILE_RENAME_NEEDS_CONFIRM_MESSAGE

internal fun EditorController.insertImageNoteAssetPath(): String = INSERT_IMAGE_NOTE_ASSET

internal fun EditorController.insertImageWarningAssetPath(): String = INSERT_IMAGE_WARNING_ASSET

internal fun EditorController.txtPrefaceChapterIndex(): Int = TXT_PREFACE_CHAPTER_INDEX

internal fun EditorController.log(message: String) = Unit
