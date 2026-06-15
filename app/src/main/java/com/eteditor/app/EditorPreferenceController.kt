package com.eteditor

import com.eteditor.core.DocumentKind
import com.eteditor.core.syncEpubDirectoryTitleFromHtml

fun EditorController.updateLeftRailExpanded(expanded: Boolean) {
    leftRailExpanded = expanded
    settingsPreferences.saveLeftRailExpanded(expanded)
}

fun EditorController.updateHideDirectoryFileNameByDefault(hidden: Boolean) {
    hideDirectoryFileNameByDefault = hidden
}

fun EditorController.updateEpubHideSection0001FromNcx(enabled: Boolean) {
    if (epubHideSection0001FromNcx == enabled) return
    epubHideSection0001FromNcx = enabled
    if (kind == DocumentKind.Epub) {
        refreshEpubSection0001DirectoryTitleForVisibility()
    }
}

private fun EditorController.refreshEpubSection0001DirectoryTitleForVisibility() {
    val book = epub ?: return
    val useOwnSection0001Title = !epubHideSection0001FromNcx
    book.chapters
        .filter { chapter -> isSection0001Path(chapter.path) }
        .forEach { chapter -> chapter.syncEpubDirectoryTitleFromHtml(useOwnSection0001Title) }
    val nextChapters = chapters.map { chapterInfo ->
        if (!isSection0001Path(chapterInfo.source)) {
            chapterInfo
        } else {
            val sourceChapter = book.chapters.firstOrNull { chapter -> chapter.path == chapterInfo.source }
            val nextTitle = sourceChapter?.title ?: chapterInfo.title
            if (chapterInfo.title == nextTitle) chapterInfo else chapterInfo.copy(title = nextTitle)
        }
    }
    if (nextChapters != chapters) {
        chapters = nextChapters
    }
}

internal fun EditorController.resetEpubDirectoryVisibilityDefaults() {
    hideDirectoryFileNameByDefault = true
    epubHideSection0001FromNcx = true
}

fun EditorController.updateEpubLongPressSplitChapter(enabled: Boolean) {
    epubLongPressSplitChapter = enabled
}

fun EditorController.updateEpubDoubleTapEdit(enabled: Boolean) {
    epubDoubleTapEdit = enabled
    settingsPreferences.saveEpubDoubleTapEdit(enabled)
}

fun EditorController.updateEpubLeftPanelMode(mode: String) {
    if (mode !in LEFT_PANEL_MODES) return
    epubLeftPanelMode = mode
    settingsPreferences.saveEpubLeftPanelMode(mode)
}

fun EditorController.updateEpubRightPanelMode(mode: String) {
    if (mode !in RIGHT_PANEL_MODES) return
    epubRightPanelMode = mode
    settingsPreferences.saveEpubRightPanelMode(mode)
}

fun EditorController.updateTxtLeftPanelMode(mode: String) {
    if (mode !in LEFT_PANEL_MODES) return
    txtLeftPanelMode = mode
    settingsPreferences.saveTxtLeftPanelMode(mode)
}

fun EditorController.updateTxtRightPanelMode(mode: String) {
    if (mode == RIGHT_PANEL_AUTOMATION) return
    if (mode !in RIGHT_PANEL_MODES) return
    txtRightPanelMode = mode
    settingsPreferences.saveTxtRightPanelMode(mode)
}

fun EditorController.updateTxtDoubleTapEdit(enabled: Boolean) {
    txtDoubleTapEdit = enabled
    settingsPreferences.saveTxtDoubleTapEdit(enabled)
}

fun EditorController.updateTxtDoubleTapTitleEdit(enabled: Boolean) {
    txtDoubleTapTitleEdit = enabled
    settingsPreferences.saveTxtDoubleTapTitleEdit(enabled)
}

fun EditorController.updateTxtSupplementLongPressMode(enabled: Boolean) {
    txtSupplementLongPressMode = enabled
}

fun EditorController.updateTxtPreviewMode(mode: String) {
    if (mode !in TXT_PREVIEW_MODES || txtPreviewMode == mode) return
    if (
        warnTxtMoveChapterSyncPending(
            if (mode == TXT_PREVIEW_MODE_FULL) {
                "\u5207\u6362\u5168\u6587\u9884\u89c8"
            } else {
                "\u5207\u6362\u7ae0\u8282\u9884\u89c8"
            }
        )
    ) {
        return
    }
    txtFullPreviewCachedAnchor = if (kind == DocumentKind.Txt && mode == TXT_PREVIEW_MODE_FULL) {
        txtPreviewAnchor()
    } else {
        null
    }
    txtPreviewMode = mode
    refreshPreview()
}
