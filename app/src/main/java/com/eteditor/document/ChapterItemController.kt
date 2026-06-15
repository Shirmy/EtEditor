package com.eteditor

import com.eteditor.core.DocumentKind

fun EditorController.updateChapterItem(chapterIndex: Int, fileName: String, chapterTitle: String): Boolean {
    return when (kind) {
        DocumentKind.Epub -> updateEpubChapterItem(chapterIndex, fileName, chapterTitle)
        DocumentKind.Txt -> updateTxtChapterItem(chapterIndex, chapterTitle)
        DocumentKind.None -> false
    }
}

private fun EditorController.updateTxtChapterItem(chapterIndex: Int, chapterTitle: String): Boolean {
    if (warnTxtMoveChapterSyncPending("编辑目录标题")) return false
    val document = txt ?: return false
    val result = updateTxtChapterTitleText(document, chapterIndex, chapterTitle)
    if (!result.success) {
        if (result.message.isNotBlank()) statusMessage = result.message
        return false
    }
    document.text = result.text
    document.chapters = detectCurrentTxtChapters(document.text)
    checkReport = null
    markDocumentChanged()
    applyTxtCatalogPurifyRulesAfterCatalogChange()
    refreshChapters()
    selectPreviewChapter(chapterIndex)
    return true
}
